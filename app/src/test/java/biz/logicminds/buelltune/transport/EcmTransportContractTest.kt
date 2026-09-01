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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Shared state-contract suite (R7, R8, KTD11 step 4): written once here and
 * run against both [TcpTransportContractTest] and
 * [BluetoothClassicTransportContractTest] through their own typed
 * connection factories. This is the exact contract U13 (BLE) and U14
 * (USB-serial) must also satisfy.
 */
abstract class EcmTransportContractTest {

    /** Builds a working [EcmTransport] wired to [server] via this suite's connection type. */
    abstract fun harness(): TransportHarness

    interface TransportHarness {
        /** A transport whose typed factory connects to [server] and succeeds immediately. */
        fun transport(server: FakePduServer): EcmTransport

        /**
         * A transport whose typed factory connects to [server] but only
         * after [gate] completes - lets a test observe [ConnectionState.Connecting]
         * deterministically instead of racing a `StateFlow` collector against
         * a real connect.
         */
        fun gatedTransport(server: FakePduServer, gate: CompletableDeferred<Unit>): EcmTransport
    }

    @Test
    fun connectTransitionsDisconnectedThenConnectingThenConnected() = runBlocking {
        FakePduServer { ackWithBytes(ByteArray(0)) }.use { server ->
            val gate = CompletableDeferred<Unit>()
            val transport = harness().gatedTransport(server, gate)
            assertEquals(ConnectionState.Disconnected, transport.state.value)

            val job = launch { transport.connect() }
            withTimeout(5000) {
                while (transport.state.value !is ConnectionState.Connecting) yield()
            }
            assertEquals(ConnectionState.Connecting, transport.state.value)

            gate.complete(Unit)
            job.join()
            assertEquals(ConnectionState.Connected, transport.state.value)
        }
    }

    @Test
    fun transactRoundTripsARealFramedPdu() = runBlocking {
        FakePduServer { request -> ackWithBytes(byteArrayOf(request.getPageNr().toByte())) }.use { server ->
            val transport = harness().transport(server)
            transport.connect()

            val response = transport.transact(PDU.getRequest(pageno = 7, offset = 0, len = 1))

            assertTrue(response.isACK())
            assertEquals(7, response.getEEPromData()[0].toInt())
        }
    }

    @Test
    fun serverCloseMidTransactTransitionsToFailedIoWithoutHanging() = runBlocking {
        FakePduServer { null }.use { server -> // never respond -> server closes after the request
            val transport = harness().transport(server)
            transport.connect()

            val started = System.currentTimeMillis()
            assertThrows(IOException::class.java) {
                runBlocking { transport.transact(PDU.getRequest(pageno = 1, offset = 0, len = 1)) }
            }
            val elapsedMs = System.currentTimeMillis() - started

            assertTrue(
                "expected Failed(Io), got ${transport.state.value}",
                (transport.state.value as ConnectionState.Failed).cause is FailureCause.Io,
            )
            // Bounded by PduFraming.RESPONSE_TIMEOUT_MS (1000ms) -- proof KTD11's
            // cleanup-outside-the-lock rule is followed: no reentrant-Mutex hang.
            assertTrue(
                "mid-transact failure took ${elapsedMs}ms -- should resolve within the response budget, not hang",
                elapsedMs < 3000,
            )
        }
    }

    @Test
    fun userInitiatedDisconnectReachesDisconnectedNeverFailed() = runBlocking {
        FakePduServer { ackWithBytes(ByteArray(0)) }.use { server ->
            val transport = harness().transport(server)
            transport.connect()

            transport.disconnect()

            assertEquals(ConnectionState.Disconnected, transport.state.value)
        }
    }

    @Test
    fun concurrentTransactCallsAreSerializedAndEachGetsItsOwnResponse() = runBlocking {
        // An artificial per-request delay widens the window in which a
        // missing Mutex would let two concurrent writes interleave on the
        // wire and garble both frames -- the burn-path safety proof (KTD11).
        FakePduServer { request ->
            Thread.sleep(50)
            ackWithBytes(byteArrayOf(request.getPageNr().toByte()))
        }.use { server ->
            val transport = harness().transport(server)
            transport.connect()

            val a = async { transport.transact(PDU.getRequest(pageno = 11, offset = 0, len = 1)) }
            val b = async { transport.transact(PDU.getRequest(pageno = 22, offset = 0, len = 1)) }
            val responseA = a.await()
            val responseB = b.await()

            assertEquals(11, responseA.getEEPromData()[0].toInt())
            assertEquals(22, responseB.getEEPromData()[0].toInt())
            assertEquals(2, server.requests.size)
            assertEquals(setOf(11, 22), server.requests.map { it.getPageNr() }.toSet())
        }
    }
}
