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

import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * [BluetoothClassicTransport] against a Mockito-mocked `BluetoothSocket` -
 * `android.bluetooth.BluetoothSocket` is `final` with a package-private
 * constructor (verified against the platform stub jar), so it cannot be
 * subclassed or built directly from test code; Mockito's default (inline)
 * mock maker is what makes it fakeable at all. The mock's
 * `inputStream`/`outputStream`/`close()` delegate to a real loopback
 * [Socket], so [BluetoothClassicTransport]'s production code path is
 * exercised against real, blocking byte-stream I/O - not a hand-rolled fake
 * stream.
 */
class BluetoothClassicTransportContractTest : EcmTransportContractTest() {
    override fun harness(): TransportHarness = BtHarness

    private object BtHarness : TransportHarness {
        override fun transport(server: FakePduServer): EcmTransport =
            BluetoothClassicTransport { mockConnectedSocket(server.port) }

        override fun gatedTransport(server: FakePduServer, gate: CompletableDeferred<Unit>): EcmTransport =
            BluetoothClassicTransport {
                gate.await()
                mockConnectedSocket(server.port)
            }
    }
}

class BluetoothClassicTransportTest {

    @Test
    fun connectFactoryThrowingSecurityExceptionYieldsFailedPermissionDenied() = runBlocking {
        // Fakeable without any Bluetooth hardware or Android runtime -- the
        // factory is just a lambda the test controls (KTD11 step 4). This
        // is the #8 bug's exact signature: a missing BLUETOOTH_CONNECT
        // permission throws SecurityException at socket-connect time.
        val transport = BluetoothClassicTransport {
            throw SecurityException("BLUETOOTH_CONNECT not granted")
        }

        assertThrows(SecurityException::class.java) {
            runBlocking { transport.connect() }
        }

        val state = transport.state.value
        assertTrue(
            "expected Failed(PermissionDenied), got $state",
            state is ConnectionState.Failed && state.cause is FailureCause.PermissionDenied,
        )
    }

    @Test
    fun connectFactoryThrowingIOExceptionYieldsFailedIoDistinguishableFromPermissionDenied() = runBlocking {
        val transport = BluetoothClassicTransport {
            throw IOException("socket connect failed")
        }

        assertThrows(IOException::class.java) {
            runBlocking { transport.connect() }
        }

        val state = transport.state.value
        assertTrue(
            "expected Failed(Io), distinguishable from Failed(PermissionDenied), got $state",
            state is ConnectionState.Failed && state.cause is FailureCause.Io,
        )
    }
}

private fun mockConnectedSocket(port: Int): BluetoothSocket {
    val backing = Socket().apply { connect(InetSocketAddress("127.0.0.1", port)) }
    val socket = mock(BluetoothSocket::class.java)
    `when`(socket.inputStream).thenReturn(backing.getInputStream())
    `when`(socket.outputStream).thenReturn(backing.getOutputStream())
    doAnswer { backing.close() }.`when`(socket).close()
    return socket
}
