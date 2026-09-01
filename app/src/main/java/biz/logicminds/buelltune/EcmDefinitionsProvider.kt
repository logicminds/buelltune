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

import biz.logicminds.buelltune.data.EcmDefinitionsDatabase

/**
 * Supplies the EEPROM page-layout definitions [ECM.setupEEPROM] needs, and
 * the ECM-id/size lookups the static [EEPROM.get]/[EEPROM.size2id] legacy
 * facades expose, decoupled from a bare [android.content.Context] (KTD7).
 * `setupEEPROM()` used to call the Context-taking `EEPROM.get(id, Context)`
 * directly; routing it through this injected interface instead means a
 * later, non-Android JVM implementation reading the same reference database
 * off the test classpath (U10's `ecmsim` harness) can be substituted without
 * touching [ECM] or [EEPROM].
 *
 * Deliberately scoped to exactly what [ECM]/[EEPROM] need today, not the
 * full definitions surface [VariableProvider]/[BitSetProvider] already
 * cover as their own interfaces.
 */
interface EcmDefinitionsProvider {
    /**
     * Build the page-layout skeleton for [ecmId] (only the first 5
     * characters are significant, matching the legacy `EEPROM.get`
     * truncation), or null if the id is unknown.
     */
    fun getEeprom(ecmId: String): EEPROM?

    /** Candidate ECM ids whose EEPROM size or extended size matches [length]. */
    fun size2id(length: Int): List<String>
}

/**
 * [EcmDefinitionsProvider] backed by [EcmDefinitionsDatabase] (Room, R6,
 * KTD3) - the production implementation [AppContainer] wires in.
 */
class RoomEcmDefinitionsProvider(private val db: EcmDefinitionsDatabase) : EcmDefinitionsProvider {

    override fun getEeprom(ecmId: String): EEPROM? {
        val name = if (ecmId.length > 5) ecmId.substring(0, 5) else ecmId
        return EEPROM.fromPageRows(name, db.eepromDao().getPages(name))
    }

    override fun size2id(length: Int): List<String> = db.eepromDao().size2id(length)
}
