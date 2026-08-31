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
package biz.logicminds.buelltune.ui

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import biz.logicminds.buelltune.Constants
import biz.logicminds.buelltune.ECM
import biz.logicminds.buelltune.service.EcmService
import biz.logicminds.buelltune.transport.ConnectionState
import biz.logicminds.buelltune.transport.FailureCause
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/**
 * Live [ConnectionState]/RPM signal for the Compose shell (R12). Abstracted
 * behind an interface so [ConnectionStatusViewModel] is exercisable in a
 * Compose UI test against a fake implementation with no real
 * [EcmService], `bindService`, or broadcast machinery involved - only
 * [ServiceEcmLiveDataSource] touches any of that.
 */
interface EcmLiveDataSource : AutoCloseable {
    val connectionState: StateFlow<ConnectionState>
    val rpm: StateFlow<Int?>
}

/**
 * [EcmLiveDataSource] backed by the real, already-running [EcmService]
 * (R12's "against the real service" requirement) - reached through the
 * exact same public contract the legacy Fragments already consume
 * (KTD4's compatibility bridge: [EcmService.REALTIME_DATA] /
 * [EcmService.CONNECTION_LOST] and the shared [ECM] singleton), the same
 * way `MainActivity`/`DataChannelFragment`/`LogFragment` bind and
 * register today. This unit adds no new public surface to the service or
 * transport packages; [EcmService.state]/[ECM]'s runtime-data flow stay
 * internal, and this class only reads what those files already publish.
 *
 * [EcmService.REALTIME_DATA] fires once per successful poll cycle, so its
 * arrival is itself proof of a live connection; [EcmService.CONNECTION_LOST]
 * fires exactly once when the service's poll loop observes the link drop
 * (R8, R11, F1). Between those two signals this class has everything the
 * legacy screens use to render "connected"/"disconnected" and the current
 * RPM (`ecm.getRuntimeValue(Variables.RPM)`, the same lookup
 * `LogFragment.refresh()` already performs).
 */
class ServiceEcmLiveDataSource(
    private val context: Context,
    private val ecm: ECM,
) : EcmLiveDataSource {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _rpm = MutableStateFlow<Int?>(null)
    override val rpm: StateFlow<Int?> = _rpm.asStateFlow()

    /** Binding keeps [EcmService] alive for this screen's lifetime; the actual signal comes from [receiver] below. */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder) = Unit
        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                EcmService.REALTIME_DATA -> {
                    _connectionState.value = ConnectionState.Connected
                    _rpm.value = ecm.getRuntimeValue(Constants.Variables.RPM)?.getIntValue()
                }
                EcmService.CONNECTION_LOST -> {
                    _connectionState.value = ConnectionState.Failed(FailureCause.Io(IOException("Connection lost")))
                    _rpm.value = null
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(EcmService.REALTIME_DATA)
            addAction(EcmService.CONNECTION_LOST)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        context.bindService(Intent(context, EcmService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun close() {
        context.unbindService(serviceConnection)
        context.unregisterReceiver(receiver)
    }
}
