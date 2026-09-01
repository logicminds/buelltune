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

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/** Route names for [BuellTuneNavHost]'s destinations - one today, per R12/KD2's scope boundary. */
object BuellTuneDestinations {
    const val CONNECTION_STATUS = "connection-status"
}

/**
 * Minimal Compose shell (R12, KD2): one destination today,
 * [ConnectionStatusScreen], proving the ViewModel -> StateFlow -> Compose
 * path against the real service/transport/domain layer. It replaces no
 * existing screen - the legacy Fragment-based UI (R13) is untouched -
 * and is reached only via the debug-only launcher entry point. Later
 * screen-migration slices add destinations to this same `NavHost` rather
 * than building a second one.
 */
@Composable
fun BuellTuneNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = BuellTuneDestinations.CONNECTION_STATUS) {
        composable(BuellTuneDestinations.CONNECTION_STATUS) {
            ConnectionStatusScreen()
        }
    }
}
