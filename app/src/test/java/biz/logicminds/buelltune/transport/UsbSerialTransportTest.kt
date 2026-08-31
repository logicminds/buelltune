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
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * [UsbSerialTransport] against a fake [SerialInputOutputManager.Listener]
 * bridged to a real loopback [Socket] connected to [FakePduServer] -
 * `usb-serial-for-android`'s `UsbSerialPort`/`UsbDeviceConnection` need a
 * real USB device and cannot run in a JVM test, so [fakeUsbConnection]
 * plays the role a real [SerialInputOutputManager] would: a background
 * thread reads the socket and calls `onNewData`/`onRunError`, and `write()`
 * writes to the socket directly - matching the legacy
 * `ECM.connect(UsbSerialPort, Protocol)`'s direct
 * `UsbSerialPort.write(byte[], int)` contract - giving [UsbSerialTransport]'s
 * production code real, blocking byte-stream I/O rather than a hand-rolled
 * fake stream, exactly like [TcpTransportContractTest] and
 * [BluetoothClassicTransportContractTest].
 */
class UsbSerialTransportContractTest : EcmTransportContractTest() {
    override fun harness(): TransportHarness = UsbHarness

    private object UsbHarness : TransportHarness {
        override fun transport(server: FakePduServer): EcmTransport =
            UsbSerialTransport { listener -> fakeUsbConnection(server.port, listener) }

        override fun gatedTransport(server: FakePduServer, gate: CompletableDeferred<Unit>): EcmTransport =
            UsbSerialTransport { listener ->
                gate.await()
                fakeUsbConnection(server.port, listener)
            }
    }
}

class UsbSerialTransportTest {

    @Test
    fun connectFactoryThrowingSecurityExceptionYieldsFailedPermissionDenied() = runBlocking {
        // Fakeable without any USB hardware -- the factory is just a lambda
        // the test controls (KTD11 step 4), e.g. a revoked/never-granted
        // UsbManager permission surfacing as SecurityException at open time.
        val transport = UsbSerialTransport { throw SecurityException("USB permission not granted") }

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
        val transport = UsbSerialTransport { throw IOException("could not open COM port") }

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
    fun onRunErrorTransitionsToFailedIoWithoutHangingTheInFlightTransact() = runBlocking {
        FakePduServer { null }.use { server -> // never respond -- only the fake listener's onRunError should end this
            lateinit var listener: SerialInputOutputManager.Listener
            val transport = UsbSerialTransport { l ->
                listener = l
                fakeUsbConnection(server.port, l)
            }
            transport.connect()

            // Simulate the USB device vanishing mid-request -- exactly the
            // stall the deleted PipedInputStream/PipedOutputStream bridge
            // used to cause (R8, U13/U14's shared risk).
            Thread {
                Thread.sleep(50)
                listener.onRunError(IOException("USB device disconnected"))
            }.apply { isDaemon = true; start() }

            val started = System.currentTimeMillis()
            assertThrows(IOException::class.java) {
                runBlocking { transport.transact(PDU.getRequest(pageno = 1, offset = 0, len = 1)) }
            }
            val elapsedMs = System.currentTimeMillis() - started

            assertTrue(
                "expected Failed(Io), got ${transport.state.value}",
                (transport.state.value as ConnectionState.Failed).cause is FailureCause.Io,
            )
            // onRunError should fail the in-flight read immediately (UsbSerialInputStream.available()
            // rethrows once its buffer is drained), well inside PduFraming.RESPONSE_TIMEOUT_MS (1000ms)
            // -- proof this never degrades into a PipedInputStream-style hang.
            assertTrue(
                "onRunError took ${elapsedMs}ms to fail the in-flight transact -- should be near-immediate, not a timeout-bounded hang",
                elapsedMs < 500,
            )
        }
    }
}

/**
 * Stands in for a real `SerialInputOutputManager` bound to a real
 * `UsbSerialPort`: a background thread reads [port]'s loopback [Socket]
 * and calls [listener]'s `onNewData` for each chunk (or `onRunError` on
 * EOF/failure), and `write()` writes straight to the socket - the same
 * "manager reads, port writes directly" split the legacy
 * `ECM.connect(UsbSerialPort, Protocol)` used (git history), which
 * [TransportFactory.usbSerial] also preserves.
 */
private fun fakeUsbConnection(port: Int, listener: SerialInputOutputManager.Listener): UsbSerialConnection {
    val socket = Socket().apply { connect(InetSocketAddress("127.0.0.1", port), TcpTransport.CONNECT_TIMEOUT_MS) }
    val readerThread = Thread {
        try {
            val buffer = ByteArray(256)
            val input = socket.getInputStream()
            while (!socket.isClosed) {
                val n = input.read(buffer)
                if (n == -1) {
                    listener.onRunError(IOException("EOF from fake USB-serial peer"))
                    return@Thread
                }
                listener.onNewData(buffer.copyOf(n))
            }
        } catch (e: IOException) {
            if (!socket.isClosed) listener.onRunError(e)
        }
    }.apply {
        isDaemon = true
        start()
    }

    return object : UsbSerialConnection {
        override fun write(data: ByteArray, timeoutMs: Int) {
            socket.getOutputStream().write(data)
        }

        override fun close() {
            readerThread.interrupt()
            runCatching { socket.close() }
        }
    }
}
