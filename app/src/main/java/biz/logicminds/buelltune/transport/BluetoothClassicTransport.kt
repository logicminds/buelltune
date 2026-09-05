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
import biz.logicminds.buelltune.PDU
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Bluetooth Classic Serial Port Profile (SPP) [EcmTransport]. [socketFactory]
 * is the typed connection factory (KTD11 step 4): it must build and connect
 * a [BluetoothSocket] (typically `device.createRfcommSocketToServiceRecord(
 * RFCOMM_UUID)` followed by `.connect()`, see [TransportFactory.bluetoothClassic])
 * or throw, letting a test inject a fake without a real
 * [android.bluetooth.BluetoothAdapter]. Blocking stream I/O runs on
 * [ioDispatcher] (`Dispatchers.IO` by default).
 *
 * On Android 12+, a missing `BLUETOOTH_CONNECT` runtime permission makes
 * [socketFactory] throw [SecurityException] - the #8 bug's exact signature.
 * [connect] catches it separately from [IOException] and maps it to
 * [FailureCause.PermissionDenied], distinguishable from a plain I/O drop
 * (R8, R10).
 */
class BluetoothClassicTransport(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val socketFactory: suspend () -> BluetoothSocket,
) : EcmTransport {

    private val mutex = Mutex()
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var link: ByteLink? = null

    /** Reused across every [transact] call (KTD11: safe because `mutex` serializes them). */
    private val frameBuffer = ByteArray(256)

    override suspend fun connect() {
        _state.value = ConnectionState.Connecting
        // A connect() over a live link would strand the previous
        // StreamByteLink's pump on a socket nobody holds a reference to,
        // permanently occupying a Dispatchers.IO worker. Nothing upstream
        // guarantees disconnect() was called first.
        closeQuietly(socket)
        var opened: BluetoothSocket? = null
        try {
            val s = withContext(ioDispatcher) { socketFactory() }
            opened = s
            socket = s
            link = StreamByteLink(s.inputStream, s.outputStream, ioDispatcher)
            _state.value = ConnectionState.Connected
        } catch (e: SecurityException) {
            closeQuietly(opened)
            _state.value = ConnectionState.Failed(FailureCause.PermissionDenied(e))
            throw e
        } catch (e: IOException) {
            closeQuietly(opened)
            _state.value = ConnectionState.Failed(FailureCause.Io(e))
            throw e
        }
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
            closeQuietly(socket)
            _state.value = ConnectionState.Failed(FailureCause.Io(e))
            throw e
        }
    }

    override suspend fun disconnect() {
        mutex.withLock { closeQuietly(socket) }
        _state.value = ConnectionState.Disconnected
    }

    private fun closeQuietly(s: BluetoothSocket?) {
        try {
            s?.close()
        } catch (e: IOException) {
        }
        socket = null
        link = null
    }

    companion object {
        /** RFCOMM SPP UUID, matching the legacy `ECM.connect(BluetoothDevice, Protocol)`. */
        @JvmField
        val RFCOMM_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
