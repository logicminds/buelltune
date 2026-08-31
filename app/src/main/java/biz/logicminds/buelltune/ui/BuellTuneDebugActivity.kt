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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Debug-only launcher entry point for [BuellTuneNavHost] (R12). Declared
 * only in `app/src/debug/AndroidManifest.xml`, so this activity has no
 * manifest entry - and is unreachable, no launcher icon, no exported
 * component - in a release build. It is a separate activity rather than a
 * menu entry inside `MainActivity` precisely because KTD4 forbids editing
 * `MainActivity`'s screen logic, and R12 forbids replacing an existing
 * screen; this class and its manifest overlay are the entirety of that
 * edit.
 */
class BuellTuneDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BuellTuneNavHost()
        }
    }
}
