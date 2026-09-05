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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The two operations [UsbSerialTransport] needs from a live USB-serial
 * link, once its typed connection factory has installed a
 * [SerialInputOutputManager.Listener] and started reading: a blocking,
 * timed write and teardown. Reads never appear here - they arrive
 * exclusively through the listener's `onNewData`/`onRunError` callbacks,
 * which [UsbSerialTransport] wraps with `callbackFlow`. This narrow shape
 * (KTD11 step 4's "typed connection factory") is what makes USB-serial
 * fakeable in a JVM test without a real `UsbSerialPort`/`UsbDeviceConnection`
 * pair - see [TransportFactory.usbSerial] for the production
 * implementation and `UsbSerialTransportContractTest` for the fake.
 */
interface UsbSerialConnection {
    /**
     * Write [data] with [timeoutMs], matching the legacy
     * `ECM.connect(UsbSerialPort, Protocol)`'s direct
     * `UsbSerialPort.write(byte[], int)` call - *not*
     * [SerialInputOutputManager.writeAsync], which the legacy code never
     * used for writes (verified against `git show
     * 717634b~1:app/src/main/java/org/ecmdroid/ECM.java`, the last
     * revision before the rebrand rewrote this transport: `this.out = new
     * OutputStream() { ... uart.write(b, 2000); ... }` - the manager was
     * wired up for reads only).
     */
    @Throws(IOException::class)
    fun write(data: ByteArray, timeoutMs: Int)

    /** Tear down the IO manager/port. Must not throw. */
    fun close()
}

/**
 * The two [SerialInputOutputManager.Listener] callbacks, carried as a
 * single ordered stream so a data burst and a run error are observed in
 * the order the listener actually delivered them.
 */
private sealed class UsbSerialEvent {
    data class Data(val bytes: ByteArray) : UsbSerialEvent()
    data class Error(val exception: IOException) : UsbSerialEvent()
}

/**
 * `usb-serial-for-android` 3.9.0 [SerialInputOutputManager]-backed
 * [EcmTransport] (R7, R8, KD3, KTD11) - zero `PipedInputStream`/
 * `PipedOutputStream` bridge, unlike the legacy
 * `ECM.connect(UsbSerialPort, Protocol)` it replaces.
 *
 * [connectionFactory] is the typed connection factory (KTD11 step 4):
 * given the [SerialInputOutputManager.Listener] this transport wants wired
 * up, it must open/parameterize the port, start reading, and return a
 * [UsbSerialConnection] - or throw, letting a test inject a fake without a
 * real `UsbSerialPort`. [TransportFactory.usbSerial] supplies the
 * production factory over a port `MainActivity.findCOMDevice()` has
 * already opened and baud-configured (9600/19200 per
 * [biz.logicminds.buelltune.ECM.Protocol] - KTD4 forbids touching that
 * method, so this transport does not repeat the selection).
 *
 * `onNewData`/`onRunError` are wrapped with `callbackFlow` into a
 * [UsbSerialEvent] stream and fed into a [ChannelByteLink] (R1, R2,
 * KTD2) - a coroutine channel [PduFraming.readFrame] suspends on,
 * replacing the deleted `PolledByteQueueInputStream` and the
 * `PipedInputStream` bridge before it. Writes go straight through
 * [UsbSerialConnection.write], preserving the legacy 2000ms write
 * timeout ([WRITE_TIMEOUT_MS]).
 */
class UsbSerialTransport(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectionFactory: suspend (SerialInputOutputManager.Listener) -> UsbSerialConnection,
) : EcmTransport {

    private val mutex = Mutex()
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    @Volatile private var connection: UsbSerialConnection? = null
    @Volatile private var link: ChannelByteLink? = null
    @Volatile private var eventsScope: CoroutineScope? = null

    /** Reused across every [transact] call (KTD11: safe because `mutex` serializes them). */
    private val frameBuffer = ByteArray(256)

    override suspend fun connect() {
        _state.value = ConnectionState.Connecting
        val opened = CompletableDeferred<UsbSerialConnection>()
        val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
        val byteLink = ChannelByteLink(
            sink = { bytes -> opened.await().write(bytes, WRITE_TIMEOUT_MS) },
        )
        scope.launch {
            events(opened).collect { event ->
                when (event) {
                    is UsbSerialEvent.Data -> byteLink.offer(event.bytes)
                    is UsbSerialEvent.Error -> byteLink.fail(event.exception)
                }
            }
        }
        try {
            val c = opened.await()
            connection = c
            link = byteLink
            eventsScope = scope
            _state.value = ConnectionState.Connected
        } catch (e: SecurityException) {
            scope.cancel()
            _state.value = ConnectionState.Failed(FailureCause.PermissionDenied(e))
            throw e
        } catch (e: IOException) {
            scope.cancel()
            _state.value = ConnectionState.Failed(FailureCause.Io(e))
            throw e
        }
    }

    /**
     * Bridges [connectionFactory]'s [SerialInputOutputManager.Listener]
     * callbacks into a cold [Flow], and completes [opened] with the
     * connection the factory returns (or its failure) so [connect] can
     * observe the outcome without racing the callback thread. [awaitClose]
     * runs when collection is cancelled ([closeQuietly]/a failed
     * [connect]), tearing down whatever the factory built.
     */
    private fun events(opened: CompletableDeferred<UsbSerialConnection>): Flow<UsbSerialEvent> = callbackFlow {
        val listener = object : SerialInputOutputManager.Listener {
            override fun onNewData(data: ByteArray) {
                trySend(UsbSerialEvent.Data(data))
            }

            override fun onRunError(e: Exception) {
                trySend(UsbSerialEvent.Error(e as? IOException ?: IOException(e)))
            }
        }
        val built = try {
            connectionFactory(listener).also { opened.complete(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            opened.completeExceptionally(e)
            null
        }
        awaitClose { built?.close() }
    }

    override suspend fun transact(request: PDU): PDU {
        try {
            return mutex.withLock {
                withContext(ioDispatcher) {
                    val l = link ?: throw IOException("Not connected to ECM.")
                    PduFraming.writeFrame(l, request)
                    PduFraming.readFrame(l, buffer = frameBuffer)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // KTD11: mutex.withLock{} above has already released the lock
            // (its internal try/finally unlocks even when action() throws) -
            // this catch runs in the caller's scope, never from inside it.
            closeQuietly()
            _state.value = ConnectionState.Failed(FailureCause.Io(e))
            throw e
        }
    }

    override suspend fun disconnect() {
        mutex.withLock { closeQuietly() }
        _state.value = ConnectionState.Disconnected
    }

    private fun closeQuietly() {
        eventsScope?.cancel()
        eventsScope = null
        connection?.let { c -> runCatching { c.close() } }
        connection = null
        link = null
    }

    companion object {
        /**
         * Matches the legacy `ECM.connect(UsbSerialPort, Protocol)`'s
         * direct `uart.write(b, 2000)` call (verified against `git show
         * 717634b~1:app/src/main/java/org/ecmdroid/ECM.java`, the last
         * revision before the rebrand rewrote this transport).
         */
        const val WRITE_TIMEOUT_MS = 2000
    }
}
