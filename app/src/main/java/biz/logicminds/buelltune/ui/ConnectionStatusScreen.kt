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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import biz.logicminds.buelltune.transport.ConnectionState

/**
 * Placeholder screen (R12): renders the live [ConnectionState] and current
 * RPM the real service is observing, proving the
 * ViewModel -> StateFlow -> recomposition path end-to-end against the
 * real [biz.logicminds.buelltune.service.EcmService]. Replaces no
 * existing screen (R12) and is reached only via the debug-only launcher
 * entry point (`BuellTuneDebugActivity`, `src/debug/AndroidManifest.xml`).
 *
 * [collectAsStateWithLifecycle] is this unit's precedent for lifecycle-safe
 * StateFlow collection: the collector starts/stops with the surrounding
 * `Lifecycle` instead of the composition, so backgrounding and returning
 * neither leaks a collector nor misses an update.
 */
@Composable
fun ConnectionStatusScreen(
    viewModel: ConnectionStatusViewModel = viewModel(
        factory = ConnectionStatusViewModel.Factory(LocalContext.current),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ConnectionStatusContent(uiState)
}

@Composable
internal fun ConnectionStatusContent(uiState: ConnectionStatusUiState, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Connection: ${uiState.connectionState.describe()}",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "RPM: ${uiState.rpm?.toString() ?: "--"}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** Collapses [ConnectionState.Failed] into "disconnected" - the same distinction-free treatment AE1 describes for the UI's connection indicator. */
private fun ConnectionState.describe(): String = when (this) {
    ConnectionState.Disconnected -> "disconnected"
    ConnectionState.Connecting -> "connecting"
    ConnectionState.Connected -> "connected"
    is ConnectionState.Failed -> "disconnected"
}
