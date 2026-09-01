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
package biz.logicminds.buelltune.integration

import biz.logicminds.buelltune.Bit
import biz.logicminds.buelltune.BitSet
import biz.logicminds.buelltune.BitSetProvider
import biz.logicminds.buelltune.Constants
import biz.logicminds.buelltune.Constants.DataSource
import biz.logicminds.buelltune.ECM
import biz.logicminds.buelltune.EEPROM
import biz.logicminds.buelltune.EcmDefinitionsProvider
import biz.logicminds.buelltune.Units
import biz.logicminds.buelltune.Utils
import biz.logicminds.buelltune.Variable
import biz.logicminds.buelltune.Variable.DataType
import biz.logicminds.buelltune.VariableProvider
import biz.logicminds.buelltune.data.BitNamesRow
import biz.logicminds.buelltune.data.BitSetRow
import biz.logicminds.buelltune.data.EeVariableRow
import biz.logicminds.buelltune.data.EepromPageRow
import biz.logicminds.buelltune.data.RtVariableRow
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Decompresses the bundled `assets/buelltune.db.gz` (R6's Room asset,
 * KTD3) into a real `.db` file once per JVM and opens plain
 * `org.xerial:sqlite-jdbc` connections against it -- the JVM/non-Android
 * side of KTD7's seam. Room itself is Android-only (`Room.databaseBuilder`
 * needs a `Context`, and its default driver needs Android's bundled
 * SQLite), so this is a deliberately separate, harness-only read path
 * against the exact same database file Room serves in production, never
 * the Room APIs themselves.
 */
internal object AssetDatabase {
    private val decompressed: File by lazy { decompress() }

    /** A fresh JDBC connection to the decompressed database. Callers own closing it. */
    fun newConnection(): Connection = DriverManager.getConnection("jdbc:sqlite:${decompressed.absolutePath}")

    private fun decompress(): File {
        val gz = locateAsset()
        val target = File.createTempFile("buelltune-definitions", ".db")
        target.deleteOnExit()
        GZIPInputStream(gz.inputStream()).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        return target
    }

    private fun locateAsset(): File {
        val configuredDir = System.getProperty("buelltune.assetsDir")
        val candidates = buildList {
            if (configuredDir != null) add(File(configuredDir, "buelltune.db.gz"))
            add(File("app/src/main/assets/buelltune.db.gz"))
            add(File("src/main/assets/buelltune.db.gz"))
        }
        return candidates.firstOrNull { it.exists() }
            ?: error(
                "buelltune.db.gz not found. Tried: ${candidates.joinToString { it.absolutePath }}. " +
                    "Run via the ecmsimIntegrationTest Gradle task (sets -Dbuelltune.assetsDir), or from " +
                    "the repo root/app module directory.",
            )
    }
}

private fun ResultSet.nullableInt(column: String): Int? {
    val value = getInt(column)
    return if (wasNull()) null else value
}

/**
 * JVM-side (non-Room) [EcmDefinitionsProvider] reading [AssetDatabase] via
 * plain JDBC (KTD7) -- the seam U6 deliberately deferred building, now
 * supplied for U10's `ecmsim` harness. Mirrors
 * [biz.logicminds.buelltune.RoomEcmDefinitionsProvider] query-for-query,
 * substituting one raw SQL statement per Room `@Query`
 * ([biz.logicminds.buelltune.data.EepromDao]).
 */
internal class JdbcEcmDefinitionsProvider(private val connection: Connection) : EcmDefinitionsProvider {
    override fun getEeprom(ecmId: String): EEPROM? {
        val name = if (ecmId.length > 5) ecmId.substring(0, 5) else ecmId
        val rows = mutableListOf<EepromPageRow>()
        connection.prepareStatement(
            "SELECT eeprom.xsize AS xsize, eeprom.type AS type, pages.page AS page, pages.size AS pgsize" +
                " FROM eeprom, pages" +
                " WHERE pages.category = eeprom.category" +
                " AND eeprom.name = ?" +
                " ORDER BY pages.page",
        ).use { stmt ->
            stmt.setString(1, name)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    rows.add(
                        EepromPageRow(
                            xsize = rs.nullableInt("xsize"),
                            type = rs.getString("type"),
                            page = rs.getInt("page"),
                            pgsize = rs.getInt("pgsize"),
                        ),
                    )
                }
            }
        }
        return EEPROM.fromPageRows(name, rows)
    }

    override fun size2id(length: Int): List<String> {
        val ids = mutableListOf<String>()
        connection.prepareStatement("SELECT name FROM eeprom WHERE size = ? OR xsize = ? ORDER BY name").use { stmt ->
            stmt.setInt(1, length)
            stmt.setInt(2, length)
            stmt.executeQuery().use { rs -> while (rs.next()) ids.add(rs.getString("name")) }
        }
        return ids
    }
}

/**
 * JVM-side (non-Room) [VariableProvider] reading [AssetDatabase] via plain
 * JDBC (KTD7). Mirrors [biz.logicminds.buelltune.DatabaseVariableProvider]
 * query-for-query and `convert()`-for-`convert()`, including its
 * per-ECM `HashMap` cache -- this is queried once per polled runtime-data
 * frame during the R16 poll scenario, exactly like production.
 */
internal class JdbcVariableProvider(private val connection: Connection) : VariableProvider() {
    private val cache = HashMap<String, Variable?>()
    private var currentEcm: String? = null

    override fun getRtVariableNames(ecm: String): Collection<String> = getRtVariableNames(ecm, null)

    override fun getScalarRtVariableNames(ecm: String): Collection<String> = getRtVariableNames(ecm, DataType.SCALAR)

    override fun getBitfieldRtVariableNames(ecm: String): Collection<String> = getRtVariableNames(ecm, DataType.BITFIELD)

    private fun getRtVariableNames(ecm: String, type: DataType?): Collection<String> {
        val typeUpper = type?.toString()?.uppercase(Locale.ENGLISH)
        val names = mutableListOf<String>()
        connection.prepareStatement(
            "SELECT names.origname AS origname" +
                " FROM names, rtoffsets, eeprom" +
                " WHERE eeprom.name = ? AND rtoffsets.category = eeprom.category" +
                " AND names.varname = rtoffsets.varname" +
                " AND rtoffsets.secret = 0 AND names.secret = 0" +
                " AND (? IS NULL OR UPPER(rtoffsets.type) = ?)" +
                " ORDER BY UPPER(names.origname)",
        ).use { stmt ->
            stmt.setString(1, ecm)
            stmt.setString(2, typeUpper)
            stmt.setString(3, typeUpper)
            stmt.executeQuery().use { rs -> while (rs.next()) names.add(rs.getString("origname")) }
        }
        return names
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
        val ret = convert(queryRtVariable(ecm, name))
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
        val ret = convert(queryEeVariable(ecm, name))
        cache[key] = ret
        return ret
    }

    override fun getNearestEEPROMVariable(ecm: String, offset: Int): Variable? = convert(queryNearestEeVariable(ecm, offset))

    override fun getName(varname: String): String? {
        val matcher = Constants.BIT_PATTERN.matcher(varname)
        if (matcher.matches()) {
            val name = matcher.group(1)!!
            val bit = matcher.group(2)!!.split(",")[0].toInt()
            return getName(name, bit)
        }
        connection.prepareStatement("SELECT name FROM names WHERE varname = ? LIMIT 1").use { stmt ->
            stmt.setString(1, varname)
            stmt.executeQuery().use { rs -> return if (rs.next()) rs.getString("name") else null }
        }
    }

    override fun getName(varname: String, bitnumber: Int): String? {
        if (bitnumber < 0 || bitnumber > 7) {
            return null
        }
        val row = queryBitNames(varname) ?: return null
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

    private fun queryRtVariable(ecm: String, name: String): RtVariableRow? {
        connection.prepareStatement(
            "SELECT" +
                " rtoffsets.uniqueid AS uniqueid," +
                " eeprom.type AS ecm_type," +
                " names.origname AS origname," +
                " rtoffsets.varname AS varname," +
                " rtoffsets.type AS type," +
                " rtoffsets.size AS size," +
                " rtoffsets.offset AS offset," +
                " rtoffsets.scale AS scale," +
                " rtoffsets.translate AS translate," +
                " names.format AS format," +
                " names.name AS name," +
                " names.remark AS remark," +
                " names.description AS description," +
                " names.units AS units," +
                " rtoffsets.low AS low," +
                " rtoffsets.high AS high," +
                " rtoffsets.ulow AS ulow," +
                " rtoffsets.uhigh AS uhigh" +
                " FROM rtoffsets, eeprom, names" +
                " WHERE eeprom.name = ? AND names.origname = ?" +
                " AND rtoffsets.category = eeprom.category" +
                " AND names.varname = rtoffsets.varname" +
                " AND names.secret = 0" +
                " AND rtoffsets.secret = 0",
        ).use { stmt ->
            stmt.setString(1, ecm)
            stmt.setString(2, name)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return null
                return RtVariableRow(
                    uniqueid = rs.getInt("uniqueid"),
                    ecmType = rs.getString("ecm_type"),
                    origname = rs.getString("origname"),
                    varname = rs.getString("varname"),
                    type = rs.getString("type"),
                    size = rs.getInt("size"),
                    offset = rs.getInt("offset"),
                    scale = rs.getString("scale"),
                    translate = rs.getString("translate"),
                    format = rs.getString("format"),
                    name = rs.getString("name"),
                    remark = rs.getString("remark"),
                    description = rs.getString("description"),
                    units = rs.getString("units"),
                    low = rs.getString("low"),
                    high = rs.getString("high"),
                    ulow = rs.getString("ulow"),
                    uhigh = rs.getString("uhigh"),
                )
            }
        }
    }

    private fun queryEeVariable(ecm: String, name: String): EeVariableRow? {
        connection.prepareStatement(
            "SELECT" +
                " eeoffsets.uniqueid AS uniqueid," +
                " eeprom.type AS ecm_type," +
                " names.origname AS origname," +
                " eeoffsets.varname AS varname," +
                " eeoffsets.type AS type," +
                " eeoffsets.size AS size," +
                " eeoffsets.offset AS offset," +
                " eeoffsets.scale AS scale," +
                " eeoffsets.translate AS translate," +
                " names.format AS format," +
                " names.name AS name," +
                " names.remark AS remark," +
                " names.description AS description," +
                " names.units AS units," +
                " eeoffsets.elemsize AS elemsize," +
                " eeoffsets.cols AS cols," +
                " eeoffsets.rows AS rows" +
                " FROM eeoffsets, eeprom, names" +
                " WHERE eeprom.name = ? AND names.varname = ?" +
                " AND eeoffsets.category = eeprom.category" +
                " AND eeoffsets.varname = names.varname",
        ).use { stmt ->
            stmt.setString(1, ecm)
            stmt.setString(2, name)
            stmt.executeQuery().use { rs -> return rs.toEeVariableRowOrNull() }
        }
    }

    private fun queryNearestEeVariable(ecm: String, targetOffset: Int): EeVariableRow? {
        connection.prepareStatement(
            "SELECT" +
                " eeoffsets.uniqueid AS uniqueid," +
                " eeprom.type AS ecm_type," +
                " names.origname AS origname," +
                " eeoffsets.varname AS varname," +
                " eeoffsets.type AS type," +
                " eeoffsets.size AS size," +
                " eeoffsets.offset AS offset," +
                " eeoffsets.scale AS scale," +
                " eeoffsets.translate AS translate," +
                " names.format AS format," +
                " names.name AS name," +
                " names.remark AS remark," +
                " names.description AS description," +
                " names.units AS units," +
                " eeoffsets.elemsize AS elemsize," +
                " eeoffsets.cols AS cols," +
                " eeoffsets.rows AS rows" +
                " FROM eeoffsets, eeprom, names" +
                " WHERE eeprom.name = ? AND eeoffsets.offset <= ?" +
                " AND eeoffsets.category = eeprom.category" +
                " AND eeoffsets.varname = names.varname" +
                " ORDER BY eeoffsets.offset DESC LIMIT 1",
        ).use { stmt ->
            stmt.setString(1, ecm)
            stmt.setInt(2, targetOffset)
            stmt.executeQuery().use { rs -> return rs.toEeVariableRowOrNull() }
        }
    }

    private fun ResultSet.toEeVariableRowOrNull(): EeVariableRow? {
        if (!next()) return null
        return EeVariableRow(
            uniqueid = getInt("uniqueid"),
            ecmType = getString("ecm_type"),
            origname = getString("origname"),
            varname = getString("varname"),
            type = getString("type"),
            size = getInt("size"),
            offset = getInt("offset"),
            scale = getString("scale"),
            translate = getString("translate"),
            format = getString("format"),
            name = getString("name"),
            remark = getString("remark"),
            description = getString("description"),
            units = getString("units"),
            elemsize = nullableInt("elemsize"),
            cols = nullableInt("cols"),
            rows = nullableInt("rows"),
        )
    }

    private fun queryBitNames(varname: String): BitNamesRow? {
        connection.prepareStatement(
            "SELECT bitname1, bitname2, bitname3, bitname4, bitname5, bitname6, bitname7, bitname8" +
                " FROM bits WHERE varname = ? LIMIT 1",
        ).use { stmt ->
            stmt.setString(1, varname)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return null
                return BitNamesRow(
                    bitname1 = rs.getString("bitname1"),
                    bitname2 = rs.getString("bitname2"),
                    bitname3 = rs.getString("bitname3"),
                    bitname4 = rs.getString("bitname4"),
                    bitname5 = rs.getString("bitname5"),
                    bitname6 = rs.getString("bitname6"),
                    bitname7 = rs.getString("bitname7"),
                    bitname8 = rs.getString("bitname8"),
                )
            }
        }
    }

    // Duplicated verbatim from DatabaseVariableProvider.convert() (KTD7:
    // that class is Room-bound, so this JVM/JDBC harness carries its own
    // copy against the same Row DTOs rather than depending on Room).
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
        private fun parseDouble(value: String?): Double = value?.toDoubleOrNull() ?: 0.0
        private fun parseInt(value: String?): Int = parseDouble(value).toInt()
    }
}

/**
 * JVM-side (non-Room) [BitSetProvider] reading [AssetDatabase] via plain
 * JDBC (KTD7). Mirrors [biz.logicminds.buelltune.DatabaseBitSetProvider]
 * query-for-query and decode-loop-for-decode-loop.
 */
internal class JdbcBitSetProvider(private val connection: Connection) : BitSetProvider() {
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
        val row: BitSetRow? = if (source == DataSource.EEPROM) queryEeBitSetRow(ecmId, name) else queryRtBitSetRow(ecmId, name)
        var ret: BitSet? = null
        if (row != null) {
            ret = BitSet(row.varname, row.name, row.offset)
            val bitnames = arrayOf(row.bitname1, row.bitname2, row.bitname3, row.bitname4, row.bitname5, row.bitname6, row.bitname7, row.bitname8)
            val bitdescs = arrayOf(row.bit1, row.bit2, row.bit3, row.bit4, row.bit5, row.bit6, row.bit7, row.bit8)
            val dtcs = arrayOf(row.dtc1, row.dtc2, row.dtc3, row.dtc4, row.dtc5, row.dtc6, row.dtc7, row.dtc8)
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

    private fun queryRtBitSetRow(ecmId: String, name: String): BitSetRow? {
        connection.prepareStatement(
            "SELECT" +
                " rtoffsets.varname AS varname," +
                " bits.name AS name," +
                " rtoffsets.offset AS offset," +
                " rtoffsets.type AS type," +
                " bits.byte AS byte," +
                " bits.bit1 AS bit1, bits.bit2 AS bit2, bits.bit3 AS bit3, bits.bit4 AS bit4," +
                " bits.bit5 AS bit5, bits.bit6 AS bit6, bits.bit7 AS bit7, bits.bit8 AS bit8," +
                " bits.dtc1 AS dtc1, bits.dtc2 AS dtc2, bits.dtc3 AS dtc3, bits.dtc4 AS dtc4," +
                " bits.dtc5 AS dtc5, bits.dtc6 AS dtc6, bits.dtc7 AS dtc7, bits.dtc8 AS dtc8," +
                " bits.bitname1 AS bitname1, bits.bitname2 AS bitname2, bits.bitname3 AS bitname3, bits.bitname4 AS bitname4," +
                " bits.bitname5 AS bitname5, bits.bitname6 AS bitname6, bits.bitname7 AS bitname7, bits.bitname8 AS bitname8" +
                " FROM rtoffsets, bits, eeprom" +
                " WHERE rtoffsets.varname = ?" +
                " AND bits.varname = rtoffsets.varname" +
                " AND eeprom.name = ?" +
                " AND rtoffsets.category = eeprom.category" +
                " AND rtoffsets.secret = 0",
        ).use { stmt ->
            stmt.setString(1, name)
            stmt.setString(2, ecmId)
            stmt.executeQuery().use { rs -> return rs.toBitSetRowOrNull() }
        }
    }

    private fun queryEeBitSetRow(ecmId: String, name: String): BitSetRow? {
        connection.prepareStatement(
            "SELECT" +
                " eeoffsets.varname AS varname," +
                " bits.name AS name," +
                " eeoffsets.offset AS offset," +
                " eeoffsets.type AS type," +
                " bits.byte AS byte," +
                " bits.bit1 AS bit1, bits.bit2 AS bit2, bits.bit3 AS bit3, bits.bit4 AS bit4," +
                " bits.bit5 AS bit5, bits.bit6 AS bit6, bits.bit7 AS bit7, bits.bit8 AS bit8," +
                " bits.dtc1 AS dtc1, bits.dtc2 AS dtc2, bits.dtc3 AS dtc3, bits.dtc4 AS dtc4," +
                " bits.dtc5 AS dtc5, bits.dtc6 AS dtc6, bits.dtc7 AS dtc7, bits.dtc8 AS dtc8," +
                " bits.bitname1 AS bitname1, bits.bitname2 AS bitname2, bits.bitname3 AS bitname3, bits.bitname4 AS bitname4," +
                " bits.bitname5 AS bitname5, bits.bitname6 AS bitname6, bits.bitname7 AS bitname7, bits.bitname8 AS bitname8" +
                " FROM eeoffsets, bits, eeprom" +
                " WHERE eeoffsets.varname = ?" +
                " AND bits.varname = eeoffsets.varname" +
                " AND eeprom.name = ?" +
                " AND eeoffsets.category = eeprom.category",
        ).use { stmt ->
            stmt.setString(1, name)
            stmt.setString(2, ecmId)
            stmt.executeQuery().use { rs -> return rs.toBitSetRowOrNull() }
        }
    }

    private fun ResultSet.toBitSetRowOrNull(): BitSetRow? {
        if (!next()) return null
        return BitSetRow(
            varname = getString("varname"),
            name = getString("name"),
            offset = getInt("offset"),
            type = getString("type"),
            byte = nullableInt("byte"),
            bit1 = getString("bit1"),
            bit2 = getString("bit2"),
            bit3 = getString("bit3"),
            bit4 = getString("bit4"),
            bit5 = getString("bit5"),
            bit6 = getString("bit6"),
            bit7 = getString("bit7"),
            bit8 = getString("bit8"),
            dtc1 = getString("dtc1"),
            dtc2 = getString("dtc2"),
            dtc3 = getString("dtc3"),
            dtc4 = getString("dtc4"),
            dtc5 = getString("dtc5"),
            dtc6 = getString("dtc6"),
            dtc7 = getString("dtc7"),
            dtc8 = getString("dtc8"),
            bitname1 = getString("bitname1"),
            bitname2 = getString("bitname2"),
            bitname3 = getString("bitname3"),
            bitname4 = getString("bitname4"),
            bitname5 = getString("bitname5"),
            bitname6 = getString("bitname6"),
            bitname7 = getString("bitname7"),
            bitname8 = getString("bitname8"),
        )
    }
}
