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
package biz.logicminds.buelltune.transport

import biz.logicminds.buelltune.PDU
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Covers the framing codec's acceptance scenarios over the suspend
 * [ByteLink] contract (R1, R3, R4, KTD1): chunked reassembly, checksum
 * rejection, post-failure resync, timeout-budget precision, and
 * write-frame byte fidelity.
 *
 * These run on `runTest`'s virtual clock, so the two timeout scenarios
 * assert the real budget arithmetic without spending a real second.
 */
class PduFramingTest {

    @Test
    fun readFrameReassemblesAPduSplitAcrossThreeChunkBoundaries() = runTest {
        val pdu = PDU(PDU.DROID_ID, PDU.getECMID(), byteArrayOf(PDU.CMD_VERSION))
        val bytes = pdu.getBytes()
        // Three arbitrary, unequal chunk boundaries that don't line up with
        // the 6-byte header/body split.
        val cut1 = 2
        val cut2 = minOf(bytes.size - 1, 5)
        val link = FakeByteLink()
        link.deliver(bytes.copyOfRange(0, cut1))
        link.deliver(bytes.copyOfRange(cut1, cut2))
        link.deliver(bytes.copyOfRange(cut2, bytes.size))

        val result = PduFraming.readFrame(link)

        assertArrayEquals(bytes, result.getBytes())
    }

    @Test
    fun readFrameReassemblesAPduDeliveredAsOneChunk() = runTest {
        val pdu = PDU(PDU.DROID_ID, PDU.getECMID(), byteArrayOf(PDU.CMD_VERSION))
        val link = FakeByteLink()
        link.deliver(pdu.getBytes())

        assertArrayEquals(pdu.getBytes(), PduFraming.readFrame(link).getBytes())
    }

    @Test
    fun readFrameRejectsABadChecksum() = runTest {
        val pdu = PDU(PDU.DROID_ID, PDU.getECMID(), byteArrayOf(PDU.CMD_VERSION))
        val corrupted = pdu.getBytes().copyOf()
        corrupted[corrupted.size - 1] = (corrupted[corrupted.size - 1] + 1).toByte()
        val link = FakeByteLink()
        link.deliver(corrupted)

        val thrown = assertFailsWithIo { PduFraming.readFrame(link) }
        assertTrue(
            "expected a parse-failure message, got: ${thrown.message}",
            thrown.message?.contains("Unable to parse", ignoreCase = true) == true,
        )
    }

    @Test
    fun readFrameAfterAPartialFrameDrainsAndResynchronizesToTheNextValidFrame() = runTest {
        val link = FakeByteLink()
        // A syntactically invalid 6-byte header (fails the header-validity
        // check immediately, no timeout wait needed).
        link.deliver(byteArrayOf(0, 0, 0, 0, 0, 0))
        // Garbage that had already arrived by the time of that failure --
        // must be drained, or it corrupts the next frame's header read.
        link.deliver(byteArrayOf(0x99.toByte(), 0x99.toByte(), 0x99.toByte()))

        assertFailsWithIo { PduFraming.readFrame(link) }

        // The next valid frame arrives on the wire only after the failed
        // one was drained.
        val valid = PDU(PDU.DROID_ID, PDU.getECMID(), byteArrayOf(PDU.CMD_VERSION))
        link.deliver(valid.getBytes())

        assertArrayEquals(valid.getBytes(), PduFraming.readFrame(link).getBytes())
    }

    @Test
    fun readFrameTimesOutWhenAPartialFrameIsFollowedBySilence() = runTest {
        val pdu = PDU(PDU.DROID_ID, PDU.getECMID(), byteArrayOf(PDU.CMD_VERSION))
        val link = FakeByteLink()
        // Header only -- the payload never arrives.
        link.deliver(pdu.getBytes().copyOfRange(0, PduFraming.HEADER_LENGTH))

        val thrown = assertFailsWithIo { PduFraming.readFrame(link, timeoutMs = 50) }
        assertTrue(
            "expected a timeout message, got: ${thrown.message}",
            thrown.message?.contains("Timeout", ignoreCase = true) == true,
        )
    }

    @Test
    fun readFrameHonoursTheSuppliedBudgetRatherThanApproximatingIt() = runTest {
        val pdu = PDU(PDU.DROID_ID, PDU.getECMID(), byteArrayOf(PDU.CMD_VERSION))

        // A peer answering inside the budget succeeds.
        val inTime = FakeByteLink()
        launch {
            delay(900)
            inTime.deliver(pdu.getBytes())
        }
        assertArrayEquals(pdu.getBytes(), PduFraming.readFrame(inTime, timeoutMs = 1000).getBytes())

        // A peer answering past it does not.
        val tooLate = FakeByteLink()
        launch {
            delay(1100)
            tooLate.deliver(pdu.getBytes())
        }
        assertFailsWithIo { PduFraming.readFrame(tooLate, timeoutMs = 1000) }
    }

    @Test
    fun writeFrameEmitsTheFramedBytesUnchanged() = runTest {
        val pdu = PDU(PDU.DROID_ID, PDU.getECMID(), byteArrayOf(PDU.CMD_VERSION))
        val link = FakeByteLink()

        PduFraming.writeFrame(link, pdu)

        assertEquals(1, link.written.size)
        assertArrayEquals(pdu.getBytes(), link.written.single())
    }

    /**
     * `assertThrows` cannot run a suspend body, and `runBlocking` inside
     * `runTest` would deadlock against the virtual clock -- the delayed
     * deliveries in the budget scenario would never fire.
     */
    private suspend fun assertFailsWithIo(block: suspend () -> Unit): IOException {
        try {
            block()
        } catch (e: IOException) {
            return e
        }
        throw AssertionError("expected an IOException, but none was thrown")
    }
}

/**
 * [ByteLink] test double. [deliver] queues a chunk exactly as a radio or
 * socket would push one; [written] records what the codec sent, so a
 * write can be asserted byte-for-byte.
 */
internal class FakeByteLink : ByteLink {
    private val channel = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<ByteArray> = channel

    val written = mutableListOf<ByteArray>()

    fun deliver(chunk: ByteArray) {
        channel.trySend(chunk)
    }

    fun fail(cause: Throwable) {
        channel.close(cause)
    }

    override suspend fun write(bytes: ByteArray) {
        written.add(bytes)
    }

    override suspend fun close() {
        channel.close()
    }
}
