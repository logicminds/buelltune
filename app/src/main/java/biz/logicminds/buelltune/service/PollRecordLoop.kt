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
package biz.logicminds.buelltune.service

import biz.logicminds.buelltune.ECM
import biz.logicminds.buelltune.transport.ConnectionState
import biz.logicminds.buelltune.transport.FailureCause
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Destination for a recording session's raw bytes (R14, KTD7). This is the
 * Android-free [PollRecordLoop]'s only I/O seam, replacing the legacy
 * `EcmDroidService.startRecording`'s direct `DataOutputStream(FileOutputStream)`
 * usage so the loop never needs `java.io.File`/SAF/Android storage APIs.
 * [PollRecordLoop] is the only caller: it writes the 5-byte ECM-ID header
 * once, then a big-endian record-timestamp `int` and the full PDU record
 * body once per polled frame while recording (see [PollRecordLoop]'s class
 * doc for the exact byte layout, R11 step 3).
 */
interface RecordingSink {
    /** Append [bytes] verbatim to the sink. Never called concurrently by [PollRecordLoop]. */
    fun write(bytes: ByteArray)

    /**
     * Flush and release the underlying resource. Called exactly once per
     * recording session, whether the session ended because the rider
     * stopped it or because the connection was lost (R11, AE1). Idempotent
     * implementations are not required - [PollRecordLoop] never calls this
     * twice for the same [startRecording] call.
     */
    fun close()
}

/**
 * Millisecond wall-clock time, injected so JVM tests can control elapsed
 * time deterministically instead of racing real sleeps (R14).
 */
fun interface Clock {
    fun currentTimeMillis(): Long
}

/** The [Clock] backed by the real wall clock; used by [biz.logicminds.buelltune.service.EcmService] in production. */
object SystemClock : Clock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

/**
 * Whether a [PollRecordLoop] session is currently writing to a
 * [RecordingSink]. Small and closed on purpose (KD4): the legacy service
 * only ever had "recording" or "not recording".
 */
sealed class RecordingState {
    object Stopped : RecordingState()
    object Recording : RecordingState()
}

/**
 * Android-free replacement for `EcmDroidService`'s `ReaderThread` and
 * recording logic (U8, R11, R13, R14, F1, AE1; instantiates KD4, KTD4,
 * KTD7). Nothing here - or in [ECM], [RecordingSink], or [Clock] - depends
 * on `android.*`, which is what makes this class constructible and
 * verifiable from a plain JVM test (R14): a test cannot instantiate an
 * `android.app.Service`, and this extraction is what makes that
 * unnecessary rather than reaching for Robolectric.
 *
 * [EcmService] hosts one instance of this class for its lifetime and
 * re-exposes [state], [recordingState], and [runtimeData] to
 * [LegacyBroadcastBridge]. This class owns three things, exactly like the
 * legacy `ReaderThread` + `startRecording`/`stopRecording`/`logPacket` did:
 * poll cadence, recording lifecycle, and the connection-loss reaction.
 *
 * **Poll loop.** Replaces the legacy `Thread` + `synchronized`/`wait`/
 * `notify` dance with a coroutine launched on [ioDispatcher] only while
 * [isReading] or recording is requested; cancellation (the coroutine
 * simply returning once both are false, or once a failure is handled)
 * replaces the `running` flag and `shutdown()`/`join()` call. The
 * poll-cadence gate (`ecm.isConnected() && (reading || recording)`) and the
 * per-cycle sleep/backoff arithmetic are ported verbatim from
 * `EcmDroidService.ReaderThread.run()`.
 *
 * **Recording format (R11 step 3, byte-for-byte).** Verified against the
 * current (and, per git history, never-changed-since-the-original-commit)
 * `EcmDroidService.startRecording`/`logPacket`:
 *  1. Once per session, a 5-byte header: the connected ECM's id string
 *     (`ecm.getEEPROM()?.id`, or `"UNKWN"` if no EEPROM has been read yet),
 *     truncated/asserted to exactly 5 bytes - matching the legacy
 *     `currentLog.write(id.getBytes(), 0, 5)`, including its
 *     `ArrayIndexOutOfBoundsException` if the id were ever shorter than 5
 *     characters (every real ECM id - "BUEIB", "BUE2D", etc. - is exactly
 *     5 characters, so this never fires in practice; preserved rather than
 *     silently changed).
 *  2. Per polled frame while recording: a big-endian 4-byte `int` of
 *     `((now - recordingStarted) / 10)` (matching
 *     `DataOutputStream.writeInt`, integer division truncated exactly as
 *     the legacy `(int) (System.currentTimeMillis() - recordingStarted) / 10`
 *     does), followed by the **full** PDU byte array [ECM.readRTData]
 *     returns - SOH header, sender/recipient/length bytes, EOH/SOT, the
 *     payload, EOT, and the trailing XOR checksum. `ECM.readRTData()`
 *     returns `PDU.getBytes()` (the complete frame), not the payload
 *     extracted via `getEEPromData()`/`getPayload()`; logging anything less
 *     would silently invalidate every existing `.bin` log and the
 *     committed `BUE2D_log.msl` reference, which `Bin2MslConverter` decodes
 *     assuming the full frame is present (it re-derives and checks the
 *     frame's own XOR checksum while parsing each record).
 *
 * **Connection-loss reaction (R11, F1, AE1).** On any read failure where
 * [ECM.isConnected] reports false afterward - the transport has already
 * moved itself to [ConnectionState.Failed] and released its resources
 * before rethrowing (KTD11) - this class, in order: stops polling (returns
 * from the loop instead of scheduling another cycle), stops recording
 * (flips [recordingState] to [RecordingState.Stopped] and flushes/closes
 * the sink), and only then publishes [ConnectionState.Failed] on [state].
 * The sink close runs in its own `try`/`catch`: AE1's "flip to disconnected
 * within one poll cycle" guarantee must hold even if a rider's removable
 * storage volume detaches at the exact moment the link drops, so a sink
 * failure is surfaced on [sinkFailures] instead of ever suppressing or
 * delaying the [state] transition.
 */
class PollRecordLoop(
    private val ecm: ECM,
    private val scope: CoroutineScope,
    private val clock: Clock = SystemClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        /** Matches `EcmDroidService.ReaderThread.DEFAULT_INTERVAL`: the failure-backoff floor. */
        private const val DEFAULT_INTERVAL_MS = 250

        /** Matches `EcmDroidService.ReaderThread.MINIMUM_INTERVAL`: the poll-cadence floor and idle-gate poll period. */
        private const val MINIMUM_INTERVAL_MS = 50
    }

    private val lock = Any()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    /** Mirrors what this loop has itself observed about the connection while polling (R11, F1, AE1). */
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Stopped)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _runtimeData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)

    /** Every full PDU frame [ECM.readRTData] returns while a poll session is active. */
    val runtimeData: Flow<ByteArray> = _runtimeData.asSharedFlow()

    private val _sinkFailures = MutableSharedFlow<IOException>(extraBufferCapacity = 8)

    /**
     * A [RecordingSink.close] failure that happened while reacting to a
     * connection loss (or a user-initiated stop) - surfaced separately so
     * it is never swallowed and never delays or blocks the [state]
     * transition that caused it (R11, AE1).
     */
    val sinkFailures: Flow<IOException> = _sinkFailures.asSharedFlow()

    @Volatile private var reading = false
    private var recordingInterval = 0
    private var recordingStarted = 0L
    private var sink: RecordingSink? = null
    private var pollJob: Job? = null

    var bytesLogged: Long = 0
        private set
    var recordsLogged: Long = 0
        private set
    var readFailures: Long = 0
        private set

    fun isReading(): Boolean = reading

    fun isRecording(): Boolean = _recordingState.value is RecordingState.Recording

    fun getRecordingInterval(): Int = recordingInterval

    /** Verbatim port of `EcmDroidService.getLogsPerSecond()`, integer-division truncation included - unused by any caller, kept for API parity. */
    fun logsPerSecond(): Float = (recordsLogged / (clock.currentTimeMillis() - recordingStarted) * 1000.0).toFloat()

    fun startReading() {
        synchronized(lock) {
            reading = true
            ensurePollingLocked()
        }
    }

    fun stopReading() {
        synchronized(lock) {
            reading = false
        }
    }

    /**
     * Begin a recording session into [sink] at the given [interval] (ms;
     * matches the legacy `startRecording(FileOutputStream, int, ECM)`'s
     * `interval` parameter). A no-op if already recording, matching the
     * legacy `if (recording) return;` guard.
     */
    fun startRecording(sink: RecordingSink, interval: Int) {
        synchronized(lock) {
            if (_recordingState.value is RecordingState.Recording) {
                return
            }
            recordingInterval = interval
            bytesLogged = 0
            recordsLogged = 0
            readFailures = 0
            this.sink = sink
            val id = ecm.getEEPROM()?.id ?: "UNKWN"
            try {
                sink.write(id.toByteArray().copyOfRange(0, 5))
            } catch (e: IOException) {
                closeSinkLocked()
                throw e
            }
            recordingStarted = clock.currentTimeMillis()
            _recordingState.value = RecordingState.Recording
            ecm.setRecording(true)
            ensurePollingLocked()
        }
    }

    /** User-initiated (or connection-loss-driven, via [handleConnectionLostLocked]) recording stop. Flushes/closes the sink identically either way (R11). */
    fun stopRecording() {
        synchronized(lock) {
            stopRecordingLocked()
        }
    }

    private fun stopRecordingLocked() {
        if (_recordingState.value !is RecordingState.Recording) {
            return
        }
        closeSinkLocked()
        ecm.setRecording(false)
        recordingInterval = 0
        _recordingState.value = RecordingState.Stopped
    }

    private fun closeSinkLocked() {
        val active = sink ?: return
        sink = null
        try {
            active.close()
        } catch (ioe: IOException) {
            _sinkFailures.tryEmit(ioe)
        }
    }

    private fun ensurePollingLocked() {
        if (pollJob?.isActive == true) {
            return
        }
        pollJob = scope.launch(ioDispatcher) { pollLoop() }
    }

    private suspend fun pollLoop() {
        while (true) {
            if (!(ecm.isConnected() && (reading || isRecording()))) {
                if (!reading && !isRecording()) {
                    return
                }
                delay(MINIMUM_INTERVAL_MS.toLong())
                continue
            }

            val intervalMs = maxOf(MINIMUM_INTERVAL_MS, recordingInterval)
            val cycleStart = clock.currentTimeMillis()
            var sleepMs = intervalMs
            try {
                val data = ecm.readRTData()
                _runtimeData.tryEmit(data)
                if (isRecording()) {
                    record(data)
                }
                if (_state.value !is ConnectionState.Connected) {
                    _state.value = ConnectionState.Connected
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                readFailures++
                if (!ecm.isConnected()) {
                    handleConnectionLost(e)
                    return
                }
                sleepMs = maxOf(intervalMs, DEFAULT_INTERVAL_MS)
            }
            val elapsed = clock.currentTimeMillis() - cycleStart
            val toSleep = sleepMs - elapsed
            if (toSleep > 0) {
                delay(toSleep)
            }
        }
    }

    /** Writes one record (timestamp + full frame). `internal` so [biz.logicminds.buelltune.service.RecordingFormatTest] can drive it directly without waiting on real poll-cycle timing. */
    internal fun record(data: ByteArray) {
        synchronized(lock) {
            val active = sink ?: return
            val elapsedTicks = ((clock.currentTimeMillis() - recordingStarted).toInt()) / 10
            active.write(bigEndianInt(elapsedTicks))
            active.write(data)
            bytesLogged += data.size + 4
            recordsLogged++
        }
    }

    private fun handleConnectionLost(cause: Exception) {
        synchronized(lock) {
            reading = false
            if (isRecording()) {
                stopRecordingLocked()
            }
            _state.value = ConnectionState.Failed(FailureCause.Io(cause))
        }
    }
}

private fun bigEndianInt(value: Int): ByteArray = byteArrayOf(
    (value ushr 24).toByte(),
    (value ushr 16).toByte(),
    (value ushr 8).toByte(),
    value.toByte(),
)
