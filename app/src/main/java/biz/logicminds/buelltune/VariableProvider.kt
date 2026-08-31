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

/**
 * Provider for Runtime- and EEPROM Variable Definitions and names.
 */
abstract class VariableProvider {
    /**
     * Get all available Runtime Data variable names for an ECM.
     */
    abstract fun getRtVariableNames(ecm: String): Collection<String>

    /**
     * Get a Runtime Variable definition.
     */
    abstract fun getRtVariable(ecm: String, name: String): Variable?

    /**
     * Get all available scalar Runtime Variable names for an ECM.
     */
    abstract fun getScalarRtVariableNames(ecm: String): Collection<String>

    /**
     * Get all available bitfield runtime variable names for an ECM.
     */
    abstract fun getBitfieldRtVariableNames(ecm: String): Collection<String>

    /**
     * Get a EEPROM Variable definition.
     */
    abstract fun getEEPROMVariable(ecm: String, name: String): Variable?

    /**
     * Get the descriptive name for a variable name or bit.
     */
    abstract fun getName(varname: String): String?

    /**
     * Get the descriptive name for a specific bit.
     */
    abstract fun getName(varname: String, bitnumber: Int): String?

    /**
     * Get EEPROM Variable definition at given offset or just in front of it (variable offset <= given offset).
     */
    abstract fun getNearestEEPROMVariable(ecm: String, offset: Int): Variable?

    companion object {
        /**
         * Legacy singleton facade (KTD5): resolves the process-wide
         * [AppContainer] from [ctx] instead of building/caching its own
         * `@SuppressLint("StaticFieldLeak")` instance.
         */
        @JvmStatic
        fun getInstance(ctx: Context): VariableProvider = AppContainer.from(ctx).variableProvider
    }
}
