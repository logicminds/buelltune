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
import de.kai_morich.simple_bluetooth_le_terminal.SerialSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import java.io.OutputStream

/**
 * BLE [EcmTransport] wrapping the vendored `SerialSocket`/`SerialListener`
 * pair (R7, R8, KD3, KTD11), replacing the legacy `ECM.connect(Context,
 * BluetoothDevice, Protocol)` overload's `PipedInputStream`/`PipedOutputStream`
 * bridge - deliberately **not** carried forward (see [PolledByteQueueInputStream]).
 *
 * `SerialSocket` isn't directly constructible/fakeable the way
 * `BluetoothSocket` was mocked via Mockito for [BluetoothClassicTransport]
 * (it is a concrete class with GATT/`Context` wiring baked into its
 * constructor and `connect()`), so this transport is built against
 * [BleSerialSocket] instead - a minimal seam over the three methods it
 * actually calls (`connect(listener)`, `write(data)`, `disconnect()`).
 * [TransportFactory.ble] wraps a real [SerialSocket]; tests inject a fake
 * that drives [SerialListener] callbacks directly, without any BLE
 * hardware or GATT stack.
 *
 * [SerialSocket.connect] is asynchronous - it returns immediately and
 * reports success/failure later via [SerialListener.onSerialConnect]/
 * [SerialListener.onSerialConnectError] - unlike [TcpTransport]/
 * [BluetoothClassicTransport]'s `socketFactory`, which performs a blocking
 * connect itself. [socketFactory] here therefore only builds the
 * [BleSerialSocket] wrapper; [connect] does the actual asynchronous
 * handshake by wrapping [SerialListener]'s four callbacks with
 * [callbackFlow] and waiting for the first connect/connect-error event.
 */
class BleTransport(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val socketFactory: () -> BleSerialSocket,
) : EcmTransport {

    private val mutex = Mutex()
    private val eventScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    @Volatile private var socket: BleSerialSocket? = null
    @Volatile private var readBuffer: PolledByteQueueInputStream? = null
    @Volatile private var eventJob: Job? = null

    /** Reused across every [transact] call (KTD11: safe because `mutex` serializes them). */
    private val frameBuffer = ByteArray(256)

    override suspend fun connect() {
        _state.value = ConnectionState.Connecting
        val s = socketFactory()
        val buffer = PolledByteQueueInputStream()
        val connectResult = CompletableDeferred<Unit>()

        val job = eventScope.launch {
            serialEvents(s).collect { event ->
                when (event) {
                    is SerialEvent.Connect -> connectResult.complete(Unit)
                    is SerialEvent.ConnectError -> connectResult.completeExceptionally(event.exception)
                    is SerialEvent.Read -> buffer.offer(event.data)
                    is SerialEvent.IoError -> {
                        // KTD11: this collector runs on its own job, never
                        // inside transact()'s mutex.withLock{} - the state
                        // transition below is always outside the lock.
                        buffer.fail(event.exception)
                        closeQuietly(s)
                        _state.value = ConnectionState.Failed(FailureCause.Io(event.exception))
                    }
                }
            }
        }

        try {
            connectResult.await()
        } catch (e: CancellationException) {
            job.cancel()
            throw e
        } catch (e: SecurityException) {
            job.cancel()
            closeQuietly(s)
            _state.value = ConnectionState.Failed(FailureCause.PermissionDenied(e))
            throw e
        } catch (e: Exception) {
            job.cancel()
            closeQuietly(s)
            val io = e as? IOException ?: IOException(e.message, e)
            _state.value = ConnectionState.Failed(FailureCause.Io(io))
            throw io
        }

        socket = s
        readBuffer = buffer
        eventJob = job
        _state.value = ConnectionState.Connected
    }

    override suspend fun transact(request: PDU): PDU {
        try {
            return mutex.withLock {
                withContext(ioDispatcher) {
                    val s = socket ?: throw IOException("Not connected to ECM.")
                    val input = readBuffer ?: throw IOException("Not connected to ECM.")
                    PduFraming.writeFrame(BleOutputStream(s), request)
                    PduFraming.readFrame(input, buffer = frameBuffer)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // KTD11: mutex.withLock{} above has already released the lock
            // (its internal try/finally unlocks even when action() throws) -
            // this catch runs in the caller's scope, never from inside it.
            closeQuietly(socket)
            _state.value = ConnectionState.Failed(FailureCause.Io(e))
            throw e
        }
    }

    override suspend fun disconnect() {
        mutex.withLock { closeQuietly(socket) }
        _state.value = ConnectionState.Disconnected
    }

    private fun closeQuietly(s: BleSerialSocket?) {
        eventJob?.cancel()
        eventJob = null
        try {
            s?.disconnect()
        } catch (e: Exception) {
        }
        socket = null
        readBuffer = null
    }

    /**
     * Wraps [SerialListener]'s four async callbacks - `onSerialConnect`,
     * `onSerialConnectError`, `onSerialRead`, `onSerialIoError` - as a cold
     * [Flow], the seam the approach note calls for. [socket] receives the
     * listener at subscription time via [BleSerialSocket.connect]; closing
     * collection (e.g. via [Job.cancel]) tears the flow down without
     * touching [socket] itself - callers own disconnecting it.
     */
    private fun serialEvents(socket: BleSerialSocket): Flow<SerialEvent> = callbackFlow {
        val listener = object : SerialListener {
            override fun onSerialConnect() {
                trySend(SerialEvent.Connect)
            }

            override fun onSerialConnectError(e: Exception) {
                trySend(SerialEvent.ConnectError(e))
            }

            override fun onSerialRead(data: ByteArray) {
                trySend(SerialEvent.Read(data))
            }

            override fun onSerialIoError(e: Exception) {
                trySend(SerialEvent.IoError(e))
            }
        }
        try {
            socket.connect(listener)
        } catch (e: SecurityException) {
            trySend(SerialEvent.ConnectError(e))
        } catch (e: IOException) {
            trySend(SerialEvent.ConnectError(e))
        }
        awaitClose { }
    }

    private sealed class SerialEvent {
        object Connect : SerialEvent()
        data class ConnectError(val exception: Exception) : SerialEvent()
        data class Read(val data: ByteArray) : SerialEvent()
        data class IoError(val exception: Exception) : SerialEvent()
    }
}

/**
 * Minimal seam over the three [SerialSocket] methods [BleTransport] calls
 * - `connect(listener)`, `write(data)`, `disconnect()` - so tests can drive
 * [SerialListener] callbacks directly without a real GATT connection or
 * Android runtime, the same role a typed connection factory (KTD11 step 4)
 * plays for [TcpTransport]/[BluetoothClassicTransport]. [RealBleSerialSocket]
 * is the production adapter; [TransportFactory.ble] is the only caller.
 */
interface BleSerialSocket {
    /** Mirrors [SerialSocket.connect] - may throw synchronously, or report failure later via [listener]. */
    fun connect(listener: SerialListener)

    /** Mirrors [SerialSocket.write] - queues [data] for the next GATT characteristic write. */
    fun write(data: ByteArray)

    /** Mirrors [SerialSocket.disconnect]. */
    fun disconnect()
}

/** Production [BleSerialSocket] adapter over a real [SerialSocket]. */
internal class RealBleSerialSocket(private val serialSocket: SerialSocket) : BleSerialSocket {
    override fun connect(listener: SerialListener) = serialSocket.connect(listener)
    override fun write(data: ByteArray) = serialSocket.write(data)
    override fun disconnect() = serialSocket.disconnect()
}

/** Adapts [BleSerialSocket.write] to the [OutputStream] shape [PduFraming.writeFrame] expects. */
private class BleOutputStream(private val socket: BleSerialSocket) : OutputStream() {
    override fun write(b: Int) = socket.write(byteArrayOf(b.toByte()))
    override fun write(b: ByteArray) = socket.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = socket.write(b.copyOfRange(off, off + len))
}
