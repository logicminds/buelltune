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
package biz.logicminds.buelltune

import android.content.Context
import biz.logicminds.buelltune.data.EcmDefinitionsDatabase

/**
 * Owns this app's dependency graph: the Room-backed reference database, the
 * runtime/EEPROM definitions providers, the [EcmDefinitionsProvider] (KTD7)
 * [ECM]/[EEPROM] consume, and the [ECM] protocol facade itself (KTD5).
 *
 * Replaces the ad-hoc, `@SuppressLint("StaticFieldLeak")` singletons `ECM`,
 * `VariableProvider`, and `BitSetProvider` used to build lazily on first
 * access against whatever [Context] happened to be passed to their
 * `getInstance()` - often an `Activity`, retained for the life of the
 * process. A single `AppContainer`, constructed once in
 * [EcmDroidApp.onCreate] against the [android.app.Application] context,
 * removes that leak risk while every legacy `getInstance(Context)` call
 * site keeps compiling unchanged: those facades now just delegate here.
 *
 * No transport factory yet - U7 extends this container once the four
 * `connect()` overloads move out of [ECM] and behind `EcmTransport`.
 */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    val database: EcmDefinitionsDatabase by lazy { EcmDefinitionsDatabase.getInstance(appContext) }

    val definitionsProvider: EcmDefinitionsProvider by lazy { RoomEcmDefinitionsProvider(database) }

    val variableProvider: VariableProvider by lazy { DatabaseVariableProvider(database) }

    val bitSetProvider: BitSetProvider by lazy { DatabaseBitSetProvider(database) }

    val ecm: ECM by lazy { ECM(variableProvider, bitSetProvider, definitionsProvider, appContext) }

    companion object {
        /** Resolve the process-wide container from any [Context] (KTD5's legacy `getInstance(Context)` facades). */
        @JvmStatic
        fun from(context: Context): AppContainer =
            (context.applicationContext as EcmDroidApp).appContainer
    }
}
