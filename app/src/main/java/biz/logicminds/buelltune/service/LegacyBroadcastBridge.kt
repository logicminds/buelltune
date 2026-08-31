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

import android.content.Context
import android.content.Intent
import biz.logicminds.buelltune.transport.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Re-emits [PollRecordLoop]'s state as the four legacy broadcast Intents
 * the untouched Fragments already register for (R13, KTD4). This is the
 * *only* piece of the new service layer that is `Context`-bound - which is
 * exactly why it is its own class instead of living in [PollRecordLoop]:
 * everything upstream of this bridge stays JVM-testable (R14), and this
 * bridge itself can only be exercised by an instrumented test.
 *
 * Per KTD4, not one Fragment `BroadcastReceiver` or screen-logic line
 * changes: the action strings below are copied verbatim from the legacy
 * `EcmDroidService` constants (now [EcmService]'s), and every broadcast
 * carries no extras, matching the legacy `sendBroadcast(new Intent(ACTION))`
 * payload shape exactly.
 */
class LegacyBroadcastBridge(
    private val context: Context,
    private val loop: PollRecordLoop,
    private val scope: CoroutineScope,
) {
    /** Begins collecting [loop]'s flows for the lifetime of [scope]. Call once, from [EcmService.onCreate]. */
    fun start() {
        loop.runtimeData
            .onEach { context.sendBroadcast(Intent(EcmService.REALTIME_DATA)) }
            .launchIn(scope)

        // drop(1): a fresh collector always receives the StateFlow's current
        // value first: without dropping it, a bridge started while a
        // recording session already happened to be Stopped (its initial
        // value) would fire a spurious RECORDING_STOPPED broadcast that the
        // legacy service never sent at startup.
        loop.recordingState
            .drop(1)
            .onEach { recordingState ->
                val action = when (recordingState) {
                    is RecordingState.Recording -> EcmService.RECORDING_STARTED
                    is RecordingState.Stopped -> EcmService.RECORDING_STOPPED
                }
                context.sendBroadcast(Intent(action))
            }
            .launchIn(scope)

        loop.state
            .filterIsInstance<ConnectionState.Failed>()
            .onEach { context.sendBroadcast(Intent(EcmService.CONNECTION_LOST)) }
            .launchIn(scope)
    }
}
