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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import biz.logicminds.buelltune.ECM
import biz.logicminds.buelltune.R
import biz.logicminds.buelltune.activities.MainActivity
import biz.logicminds.buelltune.transport.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Thin Android host for [PollRecordLoop] (U8, R2, R11, R13; instantiates
 * KD4, KTD4, KTD7). Replaces `EcmDroidService`: owns the lifecycle,
 * `startForeground`/`stopForeground`, the binder, and the notification -
 * nothing else. Polling cadence, the recording lifecycle, and the
 * connection-loss reaction all live in the Android-free [PollRecordLoop];
 * translating that loop's state into the legacy broadcast Intents lives in
 * [LegacyBroadcastBridge]. See [PollRecordLoop]'s class doc for the full
 * poll/record/connection-loss contract this service exposes.
 *
 * The public method names, signatures, and the four `REALTIME_DATA`/
 * `RECORDING_STARTED`/`RECORDING_STOPPED`/`CONNECTION_LOST` action strings
 * below are copied verbatim from `EcmDroidService` (unchanged since the
 * original pre-fork commit): `MainActivity`, `DataChannelFragment`, and
 * `LogFragment` call these directly through the binder and are not
 * otherwise edited by this unit (KTD4) - changing this API surface would
 * require editing those three files beyond the one permitted binder-type
 * edit.
 */
class EcmService : Service() {

    companion object {
        const val REALTIME_DATA = "biz.logicminds.buelltune.Service.realtimedataevent"
        const val RECORDING_STARTED = "biz.logicminds.buelltune.Service.recording_started"
        const val RECORDING_STOPPED = "biz.logicminds.buelltune.Service.recording_stopped"
        const val CONNECTION_LOST = "biz.logicminds.buelltune.Service.connectionlost"
        const val TAG = "EcmService"

        private const val RECORDING_ID = 1
    }

    /** Binder handed back to `MainActivity`/`DataChannelFragment`/`LogFragment`'s `ServiceConnection`s. */
    inner class EcmServiceBinder : Binder() {
        fun getService(): EcmService = this@EcmService
    }

    private val binder = EcmServiceBinder()
    private lateinit var nm: NotificationManager
    private lateinit var ecm: ECM
    private lateinit var loop: PollRecordLoop
    private lateinit var bridge: LegacyBroadcastBridge
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ecm = ECM.getInstance(this)
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(RECORDING_ID) // Remove possible left-overs from a crash
        loop = PollRecordLoop(ecm, serviceScope)
        bridge = LegacyBroadcastBridge(applicationContext, loop, serviceScope)
        bridge.start()
        observeConnectionLoss()
        Log.d(TAG, "Service created.")
    }

    override fun onDestroy() {
        stopRecordingInternal()
        loop.stopReading()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "Bound to service.")
        return binder
    }

    fun getBytes(): Long = loop.bytesLogged

    fun getRecords(): Long = loop.recordsLogged

    fun getReadFailures(): Long = loop.readFailures

    fun getLogsPerSecond(): Float = loop.logsPerSecond()

    /** Get the currently used Log file or null, if logging is not active. Always null - verbatim port of the legacy stub. */
    fun getCurrentFile(): File? = null

    /**
     * Begin a recording session. [logStream] and [ecm] are accepted for
     * legacy binary-compatibility with `LogFragment.startRecording()`'s
     * call site (`ecmDroidService.startRecording(out, interval.delay,
     * ecm)`) - [ecm] is always the same [ECM.getInstance] singleton this
     * service already holds, so it is not otherwise used here.
     */
    @Throws(IOException::class)
    fun startRecording(logStream: FileOutputStream, interval: Int, ecm: ECM) {
        loop.startRecording(DataOutputStreamRecordingSink(DataOutputStream(logStream)), interval)
        enterForeground(getString(R.string.app_name), getString(R.string.recording_started))
    }

    fun stopRecording() {
        stopRecordingInternal()
    }

    private fun stopRecordingInternal() {
        loop.stopRecording()
        if (loop.isReading()) {
            enterForeground(getString(R.string.app_name), getString(R.string.reading_started))
        } else {
            exitForeground()
        }
    }

    fun isRecording(): Boolean = loop.isRecording()

    fun getRecordingInterval(): Int = loop.getRecordingInterval()

    fun startReading() {
        loop.startReading()
        enterForeground(getString(R.string.app_name), getString(R.string.reading_started))
    }

    fun stopReading() {
        loop.stopReading()
        if (loop.isRecording()) {
            enterForeground(getString(R.string.app_name), getString(R.string.recording_started))
        } else {
            exitForeground()
        }
    }

    fun isReading(): Boolean = loop.isReading()

    /**
     * Leaves the foreground state on any [ConnectionState.Failed]
     * transition, regardless of whether a recording, plain reading, or
     * both were active. The legacy `ReaderThread` only ever called
     * `stopRecording()` (which itself calls `exitForeground()`) when a
     * recording was in progress at the moment of loss, leaving the
     * notification up indefinitely after a connection drop during a
     * reading-only session - a stale-UI gap F1/AE1 explicitly call out as
     * the outcome to avoid ("no silent stale UI state"). [PollRecordLoop]
     * guarantees both polling and recording have already stopped by the
     * time [PollRecordLoop.state] reaches [ConnectionState.Failed], so
     * exiting foreground here unconditionally closes that gap without
     * touching the byte-for-byte-preserved recording path.
     */
    private fun observeConnectionLoss() {
        loop.state
            .filterIsInstance<ConnectionState.Failed>()
            .onEach { exitForeground() }
            .launchIn(serviceScope)
    }

    /**
     * Enter (or refresh) the connectedDevice foreground state, entered when either live
     * polling (startReading) or log recording (startRecording) begins, per R2/KD10/KTD10.
     */
    private fun enterForeground(label: String, text: String) {
        val notification = buildNotification(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(RECORDING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(RECORDING_ID, notification)
        }
    }

    /**
     * Leave the foreground state. Called once both polling and recording have stopped
     * (or, per [observeConnectionLoss], on any connection loss).
     */
    private fun exitForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(label: String, text: String): Notification {
        val extras = Bundle()
        extras.putInt(MainActivity.CURRENT_FRAGMENT, R.id.nav_log)
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtras(extras)
        val contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)

        val channel = NotificationChannel(
            "buelltune_logrecorder",
            "EcmDroid Log Recorder",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        nm.createNotificationChannel(channel)
        return Notification.Builder(this, channel.id)
            .setContentTitle(label)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setSmallIcon(R.drawable.ic_log)
            .build()
    }
}

/** Adapts the legacy [FileOutputStream]-based recording API to [RecordingSink] without changing its on-disk byte layout. */
private class DataOutputStreamRecordingSink(private val out: DataOutputStream) : RecordingSink {
    override fun write(bytes: ByteArray) {
        out.write(bytes)
    }

    override fun close() {
        out.flush()
        out.close()
    }
}
