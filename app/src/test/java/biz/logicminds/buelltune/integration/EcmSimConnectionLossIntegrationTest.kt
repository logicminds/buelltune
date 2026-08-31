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
package biz.logicminds.buelltune.integration

import biz.logicminds.buelltune.ECM
import biz.logicminds.buelltune.service.PollRecordLoop
import biz.logicminds.buelltune.service.RecordingSink
import biz.logicminds.buelltune.service.RecordingState
import biz.logicminds.buelltune.transport.ConnectionState
import biz.logicminds.buelltune.transport.FailureCause
import biz.logicminds.buelltune.transport.TcpTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import java.net.InetSocketAddress
import java.net.Socket
import java.sql.Connection
import java.util.Collections

/**
 * R17/AE1: the connection-loss scenarios. Each `@Test` gets its own,
 * disposable `ecmsim` process ([sim] is an instance `@Rule`, not a
 * `@ClassRule`) because one of them kills the process outright -- sharing
 * a simulator with [EcmSimProtocolIntegrationTest] would take the whole
 * class down with it.
 *
 * Both tests drive a real [PollRecordLoop] against a real [TcpTransport]
 * connected to the real simulator, recording into an in-memory sink, then
 * sever the connection two different ways and assert the identical AE1
 * outcome: `state` flips to `Failed(Io)`, polling stops, the sink is
 * flushed/closed exactly once, and every frame recorded before the drop is
 * still present.
 */
@Category(EcmSimIntegrationSuite::class)
class EcmSimConnectionLossIntegrationTest {

    @get:Rule
    val sim = EcmSimRule("BUEIB")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var connection: Connection

    @Before
    fun openDbConnection() {
        connection = AssetDatabase.newConnection()
    }

    @After
    fun tearDown() {
        scope.cancel()
        connection.close()
    }

    private fun newEcm(): ECM = ECM(
        JdbcVariableProvider(connection),
        JdbcBitSetProvider(connection),
        JdbcEcmDefinitionsProvider(connection),
        null,
    )

    @Test
    fun killingTheSimulatorProcessMidRecordingFailsPollingAndFlushesTheSink() {
        var capturedSocket: Socket? = null
        val ecm = newEcm()
        val transport = TcpTransport {
            Socket().apply { connect(InetSocketAddress(sim.host, sim.port), TcpTransport.CONNECT_TIMEOUT_MS) }
                .also { capturedSocket = it }
        }
        ecm.connect(transport, ECM.Protocol.STOCK)
        ecm.setupEEPROM()

        val loop = PollRecordLoop(ecm, scope)
        val sink = CapturingRecordingSink()
        loop.startRecording(sink, 0)

        awaitTrue { loop.recordsLogged >= 3 }
        val framesBeforeDrop = loop.recordsLogged

        // R17's first scenario: terminate the simulator *process* outright.
        sim.underlyingProcess.kill()

        awaitTrue(10_000) { loop.state.value is ConnectionState.Failed }

        assertFalse(loop.isReading())
        assertEquals(RecordingState.Stopped, loop.recordingState.value)
        val failed = loop.state.value as ConnectionState.Failed
        assertTrue("expected FailureCause.Io, got ${failed.cause}", failed.cause is FailureCause.Io)
        assertTrue(sink.closed)
        assertEquals(1, sink.closeCount)

        // Header, then two writes (timestamp + full frame) per recorded
        // frame -- every frame recorded before the kill survives it.
        assertTrue("recordsLogged=${loop.recordsLogged} should be >= the $framesBeforeDrop seen before the kill", loop.recordsLogged >= framesBeforeDrop)
        assertTrue(sink.writes.isNotEmpty())
        assertEquals(5, sink.writes[0].size)
        assertArrayEquals("BUEIB".toByteArray(), sink.writes[0])
        assertEquals(1 + loop.recordsLogged.toInt() * 2, sink.writes.size)
        assertNotNull(capturedSocket)
    }

    @Test
    fun closingTheSocketWithoutKillingTheProcessProducesTheSameOutcome() {
        var capturedSocket: Socket? = null
        val ecm = newEcm()
        val transport = TcpTransport {
            Socket().apply { connect(InetSocketAddress(sim.host, sim.port), TcpTransport.CONNECT_TIMEOUT_MS) }
                .also { capturedSocket = it }
        }
        ecm.connect(transport, ECM.Protocol.STOCK)
        ecm.setupEEPROM()

        val loop = PollRecordLoop(ecm, scope)
        val sink = CapturingRecordingSink()
        loop.startRecording(sink, 0)

        awaitTrue { loop.recordsLogged >= 3 }
        val framesBeforeDrop = loop.recordsLogged

        // R17's second scenario: close *our own* end of the TCP socket
        // directly, bypassing TcpTransport.disconnect() entirely, so the
        // transport has no idea this happened until its next read/write
        // trips over the closed file descriptor. This is a genuinely
        // different failure surface than killing the process: no
        // FIN/EOF/RST arriving from the far end, just a local
        // "Socket is closed" the very next transact() hits -- the
        // simulator process itself is untouched.
        capturedSocket!!.close()

        awaitTrue(10_000) { loop.state.value is ConnectionState.Failed }

        assertFalse(loop.isReading())
        assertEquals(RecordingState.Stopped, loop.recordingState.value)
        val failed = loop.state.value as ConnectionState.Failed
        assertTrue("expected FailureCause.Io, got ${failed.cause}", failed.cause is FailureCause.Io)
        assertTrue(sink.closed)
        assertEquals(1, sink.closeCount)
        assertTrue("recordsLogged=${loop.recordsLogged} should be >= the $framesBeforeDrop seen before the close", loop.recordsLogged >= framesBeforeDrop)
        assertArrayEquals("BUEIB".toByteArray(), sink.writes[0])
        assertEquals(1 + loop.recordsLogged.toInt() * 2, sink.writes.size)

        // Prove this scenario really is distinct from the process-kill one:
        // the simulator process is still alive, and a brand-new connection
        // against it completes a normal handshake.
        assertTrue("expected the ecmsim process to still be running", sim.underlyingProcess.isAlive())
        val ecm2 = newEcm()
        val transport2 = TcpTransport {
            Socket().apply { connect(InetSocketAddress(sim.host, sim.port), TcpTransport.CONNECT_TIMEOUT_MS) }
        }
        ecm2.connect(transport2, ECM.Protocol.STOCK)
        try {
            assertTrue(ecm2.setupEEPROM().startsWith("BUEIB"))
        } finally {
            ecm2.disconnect()
        }
    }

    private fun awaitTrue(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }
}

private class CapturingRecordingSink : RecordingSink {
    val writes: MutableList<ByteArray> = Collections.synchronizedList(mutableListOf())

    @Volatile var closed = false

    @Volatile var closeCount = 0

    override fun write(bytes: ByteArray) {
        writes.add(bytes.copyOf())
    }

    override fun close() {
        closed = true
        closeCount++
    }
}
