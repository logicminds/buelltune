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
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Plain `java.net.Socket` [EcmTransport] for the TCP/IP connection type -
 * zero Android dependency, so this is both the transport the `ecmsim` JVM
 * harness (U10, KTD7) exercises and the one this unit's own JVM tests drive
 * directly against a local `ServerSocket`.
 *
 * [socketFactory] is the typed connection factory (KTD11 step 4): it must
 * return an already-connected [Socket] or throw, so a test can inject a
 * fake without a real network. [TransportFactory.tcp] supplies the
 * production factory, which mirrors the legacy `ECM.connect(host, port,
 * protocol)`'s 5000ms connect timeout ([CONNECT_TIMEOUT_MS]).
 */
class TcpTransport(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val socketFactory: suspend () -> Socket,
) : EcmTransport {

    private val mutex = Mutex()
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    @Volatile private var socket: Socket? = null
    @Volatile private var input: InputStream? = null
    @Volatile private var output: OutputStream? = null

    /** Reused across every [transact] call (KTD11: safe because `mutex` serializes them). */
    private val frameBuffer = ByteArray(256)

    override suspend fun connect() {
        _state.value = ConnectionState.Connecting
        var opened: Socket? = null
        try {
            val s = withContext(ioDispatcher) { socketFactory() }
            opened = s
            socket = s
            input = s.getInputStream()
            output = s.getOutputStream()
            _state.value = ConnectionState.Connected
        } catch (e: SocketTimeoutException) {
            closeQuietly(opened)
            _state.value = ConnectionState.Failed(FailureCause.Timeout(e))
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
                    val out = output ?: throw IOException("Not connected to ECM.")
                    val inp = input ?: throw IOException("Not connected to ECM.")
                    PduFraming.writeFrame(out, request)
                    PduFraming.readFrame(inp, buffer = frameBuffer)
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

    private fun closeQuietly(s: Socket?) {
        try {
            s?.close()
        } catch (e: IOException) {
        }
        socket = null
        input = null
        output = null
    }

    companion object {
        /** Matches the legacy `ECM.connect(host, port, Protocol)`'s `TCP_CONNECT_TIMEOUT`. */
        const val CONNECT_TIMEOUT_MS = 5000
    }
}
