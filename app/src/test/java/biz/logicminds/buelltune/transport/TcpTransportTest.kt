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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * [TcpTransport] against a real local [ServerSocket] - the one transport
 * path with zero Android dependency (KTD7), and the contract [BluetoothClassicTransportTest]
 * (via a mocked `BluetoothSocket`) is held to as well, through
 * [EcmTransportContractTest].
 */
class TcpTransportContractTest : EcmTransportContractTest() {
    override fun harness(): TransportHarness = TcpHarness

    private object TcpHarness : TransportHarness {
        override fun transport(server: FakePduServer): EcmTransport = TcpTransport {
            Socket().apply { connect(InetSocketAddress("127.0.0.1", server.port), TcpTransport.CONNECT_TIMEOUT_MS) }
        }

        override fun gatedTransport(server: FakePduServer, gate: CompletableDeferred<Unit>): EcmTransport = TcpTransport {
            gate.await()
            Socket().apply { connect(InetSocketAddress("127.0.0.1", server.port), TcpTransport.CONNECT_TIMEOUT_MS) }
        }
    }
}

class TcpTransportTest {

    @Test
    fun connectToAClosedPortFailsWithoutHangingPastTheConnectTimeout() = runBlocking {
        // Bind, then immediately release, a local port -- nothing is
        // listening on it, so the OS refuses the connection.
        val port = ServerSocket(0).use { it.localPort }
        val transport = TcpTransport {
            Socket().apply { connect(InetSocketAddress("127.0.0.1", port), TcpTransport.CONNECT_TIMEOUT_MS) }
        }

        val started = System.currentTimeMillis()
        assertThrows(IOException::class.java) {
            runBlocking { transport.connect() }
        }
        val elapsedMs = System.currentTimeMillis() - started

        val state = transport.state.value
        assertTrue(
            "expected Failed(Timeout) or Failed(Io), got $state",
            state is ConnectionState.Failed && (state.cause is FailureCause.Timeout || state.cause is FailureCause.Io),
        )
        assertTrue(
            "connect() to a closed port took ${elapsedMs}ms -- must never hang past the ${TcpTransport.CONNECT_TIMEOUT_MS}ms connect timeout",
            elapsedMs < TcpTransport.CONNECT_TIMEOUT_MS + 2000,
        )
    }
}
