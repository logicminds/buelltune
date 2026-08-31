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

import biz.logicminds.buelltune.Variable.DataType
import biz.logicminds.buelltune.data.BitNamesRow
import biz.logicminds.buelltune.data.EcmDefinitionsDatabase
import biz.logicminds.buelltune.data.EeVariableRow
import biz.logicminds.buelltune.data.RtVariableRow
import java.util.LinkedList
import java.util.Locale

/**
 * Create a Variable based on definitions in the built-in database.
 *
 * Backed by Room (R6, KTD3) via the [EcmDefinitionsDatabase] [AppContainer]
 * wires in - the DB lookup itself is unchanged from before this Kotlin port,
 * only how this class obtains the database changed (constructor-injected by
 * [AppContainer] instead of resolving its own `Context`-keyed singleton).
 * The cache-then-DAO shape is unchanged: the [HashMap] caches still sit in
 * front of every lookup because these run on every poll cycle.
 */
class DatabaseVariableProvider(private val db: EcmDefinitionsDatabase) : VariableProvider() {

    private val cache = HashMap<String, Variable?>()
    private var currentEcm: String? = null

    override fun getRtVariableNames(ecm: String): Collection<String> = getRtVariableNames(ecm, null)

    override fun getScalarRtVariableNames(ecm: String): Collection<String> = getRtVariableNames(ecm, DataType.SCALAR)

    override fun getBitfieldRtVariableNames(ecm: String): Collection<String> = getRtVariableNames(ecm, DataType.BITFIELD)

    private fun getRtVariableNames(ecm: String, type: DataType?): Collection<String> {
        val typeUpper = type?.toString()?.uppercase(Locale.ENGLISH)
        return LinkedList(db.rtoffsetsDao().getRtVariableNames(ecm, typeUpper))
    }

    override fun getRtVariable(ecm: String, name: String): Variable? {
        if (ecm != currentEcm) {
            cache.clear()
            currentEcm = ecm
        }
        val key = "rt#$name"
        if (cache.containsKey(key)) {
            return cache[key]
        }
        val ret = convert(db.rtoffsetsDao().getRtVariable(ecm, name))
        cache[key] = ret
        return ret
    }

    override fun getEEPROMVariable(ecm: String, name: String): Variable? {
        if (ecm != currentEcm) {
            cache.clear()
            currentEcm = ecm
        }
        val key = "ee#$name"
        if (cache.containsKey(key)) {
            return cache[key]
        }
        val ret = convert(db.eeoffsetsDao().getEeVariable(ecm, name))
        cache[key] = ret
        return ret
    }

    override fun getNearestEEPROMVariable(ecm: String, offset: Int): Variable? =
        convert(db.eeoffsetsDao().getNearestEeVariable(ecm, offset))

    override fun getName(varname: String): String? {
        val matcher = Constants.BIT_PATTERN.matcher(varname)
        if (matcher.matches()) {
            val name = matcher.group(1)!!
            val bit = matcher.group(2)!!.split(",")[0].toInt()
            return getName(name, bit)
        }
        return db.namesDao().getName(varname)
    }

    override fun getName(varname: String, bitnumber: Int): String? {
        if (bitnumber < 0 || bitnumber > 7) {
            return null
        }
        val row: BitNamesRow = db.bitsDao().getBitNames(varname) ?: return null
        return when (bitnumber) {
            0 -> row.bitname1
            1 -> row.bitname2
            2 -> row.bitname3
            3 -> row.bitname4
            4 -> row.bitname5
            5 -> row.bitname6
            6 -> row.bitname7
            else -> row.bitname8
        }
    }

    private fun convert(row: RtVariableRow?): Variable? {
        if (row == null) return null
        val ret = Variable()
        ret.id = row.uniqueid
        ret.ecmType = ECM.Type.getType(row.ecmType)
        ret.name = row.origname ?: row.varname
        ret.type = DataType.valueOf(row.type.uppercase(Locale.ENGLISH))
        ret.size = row.size
        ret.width = ret.size
        ret.offset = row.offset
        ret.scale = parseDouble(row.scale)
        ret.translate = parseDouble(row.translate)
        ret.format = row.format
        ret.label = row.name
        ret.remarks = row.remark
        ret.description = row.description
        ret.unit = row.units
        ret.symbol = Units.getSymbol(ret.unit)
        ret.low = parseDouble(row.low)
        ret.setHigh(parseDouble(row.high))
        ret.ulow = parseInt(row.ulow)
        ret.uhigh = parseInt(row.uhigh)
        ret.init()
        return ret
    }

    private fun convert(row: EeVariableRow?): Variable? {
        if (row == null) return null
        val ret = Variable()
        ret.id = row.uniqueid
        ret.ecmType = ECM.Type.getType(row.ecmType)
        ret.name = row.origname ?: row.varname
        ret.type = DataType.valueOf(row.type.uppercase(Locale.ENGLISH))
        ret.size = row.size
        ret.width = row.elemsize ?: 0
        ret.cols = row.cols ?: 0
        ret.rows = row.rows ?: 0
        ret.offset = row.offset
        ret.scale = parseDouble(row.scale)
        ret.translate = parseDouble(row.translate)
        ret.format = row.format
        ret.label = row.name
        ret.remarks = row.remark
        ret.description = row.description
        ret.unit = row.units
        ret.symbol = Units.getSymbol(ret.unit)
        ret.init()
        return ret
    }

    companion object {
        /**
         * SQLite's TEXT-affinity-to-REAL coercion, applied to the ten
         * numeric-looking `varchar` columns (KTD3.2): a null or
         * unparseable string reads as 0.0, matching `Cursor.getDouble()`'s
         * behavior on the same columns before the Room port.
         */
        private fun parseDouble(value: String?): Double {
            if (value == null) return 0.0
            return value.toDoubleOrNull() ?: 0.0
        }

        /** Same coercion as [parseDouble], truncated to int - matches `Cursor.getInt()` on a TEXT column. */
        private fun parseInt(value: String?): Int = parseDouble(value).toInt()
    }
}
