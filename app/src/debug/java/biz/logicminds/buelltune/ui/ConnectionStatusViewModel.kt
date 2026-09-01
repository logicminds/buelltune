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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import biz.logicminds.buelltune.AppContainer
import biz.logicminds.buelltune.transport.ConnectionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What [ConnectionStatusScreen] renders - the live connection state plus the current RPM, or `null` while none has been observed yet. */
data class ConnectionStatusUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val rpm: Int? = null,
)

/**
 * This unit's precedent for a Compose screen's ViewModel (R12, KD2):
 * constructed with an already-resolved [EcmLiveDataSource] rather than
 * reaching for `Context`/[AppContainer] itself, so [uiState] - the
 * ViewModel -> StateFlow half of the path a Compose UI test needs to
 * exercise - is driveable against a fake source with no real
 * [biz.logicminds.buelltune.service.EcmService] or Android broadcast
 * machinery involved. [Factory] is what wires the real one in production.
 */
class ConnectionStatusViewModel(private val source: EcmLiveDataSource) : ViewModel() {

    val uiState: StateFlow<ConnectionStatusUiState> =
        combine(source.connectionState, source.rpm) { connectionState, rpm ->
            ConnectionStatusUiState(connectionState, rpm)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionStatusUiState())

    override fun onCleared() {
        source.close()
    }

    /**
     * Resolves the real [ServiceEcmLiveDataSource] against this app's
     * [AppContainer] (KTD5) - later screen-migration slices reach the
     * service/domain layer from Compose the same way: a small
     * [ViewModelProvider.Factory] that pulls its dependencies from
     * [AppContainer.from], never a bare `Context` field on the ViewModel
     * itself.
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val ecm = AppContainer.from(appContext).ecm
            @Suppress("UNCHECKED_CAST")
            return ConnectionStatusViewModel(ServiceEcmLiveDataSource(appContext, ecm)) as T
        }
    }
}
