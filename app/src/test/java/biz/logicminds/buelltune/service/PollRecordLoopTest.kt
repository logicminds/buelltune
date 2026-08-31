/*
 EcmDroid - Android Diagnostic Tool for Buell Motorcycles
 Copyright (C) 2012 by Michel Marti

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 3
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, see <http://www.gnu.org/licenses/>.
 */
package biz.logicminds.buelltune.service

import biz.logicminds.buelltune.ECM
import biz.logicminds.buelltune.PDU
import biz.logicminds.buelltune.transport.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Collections

/**
 * JVM behavior suite for [PollRecordLoop] (R11, R14, F1, AE1). Drives
 * [ECM] through an in-memory [FakeEcmTransport] (see
 * [ServiceTestSupport.kt][ackWithBytes]) rather than a real socket or
 * Android device - the loop's contract with [ECM] is exactly the
 * exception/`isConnected()` behavior [EcmTransportContractTest] (U7)
 * already pins for every real transport, so a fake that honors that same
 * contract is a faithful enough stand-in for this unit's own tests.
 */
class PollRecordLoopTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun connectionLossWhileRecordingStopsPollingFlushesSinkAndKeepsAllPriorFrames() {
        val frame1 = frame(0x11)
        val frame2 = frame(0x22)
        val ecm = connectedEcm(
            mutableListOf(
                FakeOutcome.Reply(frame1),
                FakeOutcome.Reply(frame2),
                FakeOutcome.Fail(IOException("link dropped")),
            ),
        )
        val loop = PollRecordLoop(ecm, scope)
        val sink = RecordingSinkSpy()

        loop.startRecording(sink, 0)

        awaitTrue { loop.state.value is ConnectionState.Failed }

        // Polling and recording both stopped.
        assertFalse(loop.isReading())
        assertEquals(RecordingState.Stopped, loop.recordingState.value)
        val failed = loop.state.value as ConnectionState.Failed
        assertTrue(failed.cause is biz.logicminds.buelltune.transport.FailureCause.Io)

        // Sink flushed and closed exactly once.
        assertTrue(sink.closed)
        assertEquals(1, sink.closeCount)

        // Header, then two full records (timestamp + full PDU each) -- the
        // frame that triggered the failure never got a chance to be written.
        assertEquals(5, sink.writes.size)
        assertArrayEquals("BUEIB".toByteArray(), sink.writes[0])
        assertArrayEquals(frame1.getBytes(), sink.writes[2])
        assertArrayEquals(frame2.getBytes(), sink.writes[4])
        assertEquals(2L, loop.recordsLogged)
        assertEquals((frame1.getBytes().size + 4).toLong() + (frame2.getBytes().size + 4).toLong(), loop.bytesLogged)
    }

    @Test
    fun connectionLossWithoutRecordingTouchesNoSink() {
        val ecm = connectedEcm(
            mutableListOf(
                FakeOutcome.Reply(frame(0x11)),
                FakeOutcome.Fail(IOException("link dropped")),
            ),
        )
        val loop = PollRecordLoop(ecm, scope)
        val untouchedSink = RecordingSinkSpy()

        loop.startReading()

        awaitTrue { loop.state.value is ConnectionState.Failed }

        assertFalse(loop.isReading())
        assertEquals(RecordingState.Stopped, loop.recordingState.value)
        assertEquals(0L, loop.bytesLogged)
        assertEquals(0L, loop.recordsLogged)
        assertTrue(loop.readFailures >= 1L)
        assertTrue(untouchedSink.writes.isEmpty())
        assertFalse(untouchedSink.closed)
    }

    @Test
    fun userInitiatedStopFlushesAndClosesSinkLikeAFailureDrivenStopDoes() {
        val ecm = connectedEcm(
            mutableListOf(
                FakeOutcome.Reply(frame(0x11)),
                FakeOutcome.Reply(frame(0x22)),
            ),
        )
        val loop = PollRecordLoop(ecm, scope)
        val sink = RecordingSinkSpy()

        loop.startRecording(sink, 0)
        awaitTrue { loop.recordsLogged >= 2 }

        loop.stopRecording()

        assertTrue(sink.closed)
        assertEquals(1, sink.closeCount)
        assertEquals(RecordingState.Stopped, loop.recordingState.value)
        // A user-initiated stop is not a link failure: the connection state
        // this loop reports must never be pushed to Failed by it.
        assertFalse(loop.state.value is ConnectionState.Failed)
    }

    @Test
    fun sinkCloseFailureDuringConnectionLossStillFiresFailedAndSurfacesSeparately() {
        val closeError = IOException("volume detached mid-drop")
        val ecm = connectedEcm(
            mutableListOf(
                FakeOutcome.Reply(frame(0x11)),
                FakeOutcome.Fail(IOException("link dropped")),
            ),
        )
        val loop = PollRecordLoop(ecm, scope)
        val sink = RecordingSinkSpy(closeThrows = closeError)

        val sinkFailureDeferred = scope.async { loop.sinkFailures.first() }
        Thread.sleep(20) // let the collector subscribe before the failure fires

        loop.startRecording(sink, 0)

        awaitTrue { loop.state.value is ConnectionState.Failed }

        // The disconnect transition fired even though close() threw.
        assertEquals(RecordingState.Stopped, loop.recordingState.value)
        assertTrue(sink.closed)

        val observed = runBlocking { withTimeout(3000) { sinkFailureDeferred.await() } }
        assertEquals(closeError, observed)
    }

    private fun connectedEcm(rtDataScript: MutableList<FakeOutcome>): ECM {
        val ecm = newEcm(FixedIdDefinitionsProvider("BUEIB"))
        val fullScript = mutableListOf<FakeOutcome>(
            FakeOutcome.Reply(ackWithBytes("BUEIB310 12-11-03".toByteArray(Charsets.US_ASCII))),
        )
        fullScript.addAll(rtDataScript)
        val transport = FakeEcmTransport(fullScript)
        ecm.connect(transport, ECM.Protocol.STOCK)
        ecm.setupEEPROM()
        return ecm
    }

    private fun frame(marker: Byte): PDU = ackWithBytes(byteArrayOf(marker, marker, marker))

    private fun awaitTrue(timeoutMs: Long = 3000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }
}



private class RecordingSinkSpy(private val closeThrows: IOException? = null) : RecordingSink {
    val writes: MutableList<ByteArray> = Collections.synchronizedList(mutableListOf())

    @Volatile var closed = false

    @Volatile var closeCount = 0

    override fun write(bytes: ByteArray) {
        writes.add(bytes.copyOf())
    }

    override fun close() {
        closed = true
        closeCount++
        closeThrows?.let { throw it }
    }
}
