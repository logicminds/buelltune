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

import biz.logicminds.buelltune.Constants.DataSource
import biz.logicminds.buelltune.data.BitSetRow
import biz.logicminds.buelltune.data.EcmDefinitionsDatabase

/**
 * Create a BitSet based on definitions in the built-in database.
 *
 * Backed by Room (R6, KTD3) via the [EcmDefinitionsDatabase] [AppContainer]
 * wires in. The `rtoffsets`/`eeoffsets` table switch driven by [DataSource]
 * is preserved by calling the matching DAO.
 */
class DatabaseBitSetProvider(private val db: EcmDefinitionsDatabase) : BitSetProvider() {

    private val cache = HashMap<String, BitSet?>()
    private var currentEcm: String? = null

    override fun getBitSet(ecmId: String, name: String, source: DataSource): BitSet? {
        if (ecmId != currentEcm) {
            cache.clear()
            currentEcm = ecmId
        }
        if (cache.containsKey(name)) {
            return cache[name]
        }
        val row: BitSetRow? = if (source == DataSource.EEPROM) {
            db.eeoffsetsDao().getBitSetRow(ecmId, name)
        } else {
            db.rtoffsetsDao().getBitSetRow(ecmId, name)
        }
        var ret: BitSet? = null
        if (row != null) {
            ret = BitSet(row.varname, row.name, row.offset)
            val bitnames = arrayOf(
                row.bitname1, row.bitname2, row.bitname3, row.bitname4,
                row.bitname5, row.bitname6, row.bitname7, row.bitname8,
            )
            val bitdescs = arrayOf(
                row.bit1, row.bit2, row.bit3, row.bit4,
                row.bit5, row.bit6, row.bit7, row.bit8,
            )
            val dtcs = arrayOf(
                row.dtc1, row.dtc2, row.dtc3, row.dtc4,
                row.dtc5, row.dtc6, row.dtc7, row.dtc8,
            )
            val byteNr = row.byte ?: 0
            for (i in 1..8) {
                var bitname = bitnames[i - 1]
                val bitdesc = bitdescs[i - 1]
                if (Utils.isEmptyString(bitname) && Utils.isEmptyString(bitdesc)) {
                    continue
                }
                if (Utils.isEmptyString(bitname)) {
                    bitname = "${row.varname}.$i"
                }
                val bit = Bit()
                bit.name = bitname
                bit.bitNr = i - 1
                bit.byteNr = byteNr
                bit.offset = row.offset
                bit.type = ECM.Type.getType(row.type)
                bit.remark = bitdesc
                bit.code = dtcs[i - 1]
                ret.add(bit)
            }
        }
        cache[name] = ret
        return ret
    }
}
