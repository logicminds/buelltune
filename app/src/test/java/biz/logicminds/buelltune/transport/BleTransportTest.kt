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
import de.kai_morich.simple_bluetooth_le_terminal.SerialListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * [BleTransport] against [FakeBleSerialSocket] - a fake [BleSerialSocket]
 * that bridges [SerialListener] callbacks onto a real loopback [Socket]
 * connected to [FakePduServer], exactly the seam the approach note calls
 * for since `SerialSocket` itself cannot be constructed or mocked without
 * a real GATT/`Context` stack. This is the *exact same* [EcmTransportContractTest]
 * suite [TcpTransportContractTest] and [BluetoothClassicTransportContractTest]
 * run - no copy-pasted variant - proving [BleTransport] satisfies R7's
 * shared state contract.
 */
class BleTransportContractTest : EcmTransportContractTest() {
    override fun harness(): TransportHarness = BleHarness

    private object BleHarness : TransportHarness {
        override fun transport(server: FakePduServer): EcmTransport =
            BleTransport { fakeBleSerialSocket(server.port) }

        override fun gatedTransport(server: FakePduServer, gate: CompletableDeferred<Unit>): EcmTransport =
            BleTransport { fakeBleSerialSocket(server.port, gate) }
    }
}

class BleTransportTest {

    @Test
    fun connectFactoryThrowingSecurityExceptionYieldsFailedPermissionDenied() = runBlocking {
        // Fakeable without any BLE hardware or GATT stack -- SerialSocket.connect()
        // synchronously throws SecurityException from device.connectGatt() when
        // BLUETOOTH_CONNECT is not granted (Android 12+, #8's exact signature),
        // before any SerialListener callback ever fires.
        val transport = BleTransport {
            object : BleSerialSocket {
                override fun connect(listener: SerialListener) {
                    throw SecurityException("BLUETOOTH_CONNECT not granted")
                }
                override fun write(data: ByteArray) {}
                override fun disconnect() {}
            }
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
        val transport = BleTransport {
            object : BleSerialSocket {
                override fun connect(listener: SerialListener) {
                    throw IOException("connectGatt failed")
                }
                override fun write(data: ByteArray) {}
                override fun disconnect() {}
            }
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

    @Test
    fun onSerialConnectErrorCallbackYieldsFailedIoWithoutHanging() = runBlocking {
        // Unlike the synchronous-throw cases above, this is SerialSocket's
        // normal async failure path: connect() returns immediately and the
        // real failure surfaces later via the listener (e.g. "gatt status
        // 133", "no serial profile found").
        val transport = BleTransport {
            object : BleSerialSocket {
                override fun connect(listener: SerialListener) {
                    listener.onSerialConnectError(IOException("gatt status 133"))
                }
                override fun write(data: ByteArray) {}
                override fun disconnect() {}
            }
        }

        assertThrows(IOException::class.java) {
            runBlocking { transport.connect() }
        }

        val state = transport.state.value
        assertTrue(
            "expected Failed(Io), got $state",
            state is ConnectionState.Failed && state.cause is FailureCause.Io,
        )
    }

    @Test
    fun onSerialIoErrorDuringInFlightTransactTransitionsToFailedIoWithoutHanging() = runBlocking {
        // Covers R8's exact scenario: the fake listener reports onSerialIoError
        // -- this is the callback `f3337a1`'s legacy PipedInputStream/PipedOutputStream
        // bridge could only unstick by explicitly closing the piped stream from
        // inside this very callback. SerialReadBuffer never blocks on read(), so
        // there is nothing to unstick: the transact() call in flight must fail
        // well inside PduFraming's own 1000ms response budget, not hang forever.
        val fake = ManualBleSerialSocket()
        val transport = BleTransport { fake }
        transport.connect()
        assertTrue(transport.state.value == ConnectionState.Connected)

        val started = System.currentTimeMillis()
        val transactFailure = async(Dispatchers.Default) {
            runCatching { transport.transact(PDU.getRequest(pageno = 1, offset = 0, len = 1)) }
        }

        // Deterministically wait until transact() has issued its write (i.e. it
        // is genuinely in flight, blocked reading a response) before dropping
        // the link -- proves this is the in-flight case, not a connect-time one.
        fake.writeStarted.await()
        fake.listener.onSerialIoError(IOException("BLE link lost"))

        val result = transactFailure.await()
        val elapsedMs = System.currentTimeMillis() - started

        assertTrue("expected transact() to fail, got $result", result.isFailure)
        assertTrue(
            "expected an IOException, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is IOException,
        )
        val state = transport.state.value
        assertTrue(
            "expected Failed(Io), got $state",
            state is ConnectionState.Failed && state.cause is FailureCause.Io,
        )
        assertTrue(
            "in-flight failure took ${elapsedMs}ms -- should resolve promptly, not hang or idle out the full response budget",
            elapsedMs < 3000,
        )
    }
}

/**
 * Fake [BleSerialSocket] driven entirely by direct listener calls from the
 * test - the "fake listener you fully control" seam, used where the test
 * needs to trigger a specific [SerialListener] callback deterministically
 * rather than reacting to real byte traffic.
 */
private class ManualBleSerialSocket : BleSerialSocket {
    lateinit var listener: SerialListener
    val writeStarted = CompletableDeferred<Unit>()

    override fun connect(listener: SerialListener) {
        this.listener = listener
        listener.onSerialConnect()
    }

    override fun write(data: ByteArray) {
        writeStarted.complete(Unit)
        // Deliberately does not respond -- the test drives onSerialIoError itself.
    }

    override fun disconnect() {}
}

/**
 * Fake [BleSerialSocket] used by [BleTransportContractTest]: bridges
 * [SerialListener] callbacks onto a real loopback [Socket] connected to
 * [FakePduServer] on a background thread, mirroring how a real
 * `SerialSocket` reports connect/read/error asynchronously rather than
 * through blocking calls. [gate], if given, delays opening the socket
 * until it completes, letting [EcmTransportContractTest] observe
 * [ConnectionState.Connecting] deterministically.
 */
private fun fakeBleSerialSocket(port: Int, gate: CompletableDeferred<Unit>? = null): BleSerialSocket =
    object : BleSerialSocket {
        @Volatile var socket: Socket? = null

        override fun connect(listener: SerialListener) {
            Thread({
                gate?.let { runBlocking { it.await() } }
                try {
                    val s = Socket().apply { connect(InetSocketAddress("127.0.0.1", port)) }
                    socket = s
                    listener.onSerialConnect()
                    val input = s.getInputStream()
                    val buf = ByteArray(256)
                    while (true) {
                        val n = try {
                            input.read(buf)
                        } catch (e: IOException) {
                            listener.onSerialIoError(e)
                            return@Thread
                        }
                        if (n == -1) {
                            listener.onSerialIoError(IOException("EOF"))
                            return@Thread
                        }
                        listener.onSerialRead(buf.copyOf(n))
                    }
                } catch (e: IOException) {
                    listener.onSerialConnectError(e)
                }
            }, "FakeBleSerialSocket").apply {
                isDaemon = true
                start()
            }
        }

        override fun write(data: ByteArray) {
            val s = socket ?: throw IOException("not connected")
            s.getOutputStream().write(data)
        }

        override fun disconnect() {
            runCatching { socket?.close() }
        }
    }
