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
package biz.logicminds.buelltune.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

/**
 * Row shape for [EepromDao.getPages]: one EEPROM page per row, ordered by
 * page number, joined with the owning `eeprom` row's `xsize`/`type`. Mirrors
 * `EEPROM.get(String, Context)`'s original raw query column-for-column.
 */
data class EepromPageRow(
    val xsize: Int?,
    val type: String?,
    val page: Int,
    val pgsize: Int,
)

@Dao
interface EepromDao {
    @Query("SELECT * FROM eeprom LIMIT 1")
    fun getFirst(): EepromEntity?

    @Query("SELECT COUNT(*) FROM eeprom")
    fun count(): Int

    @Query(
        "SELECT eeprom.xsize AS xsize, eeprom.type AS type, pages.page AS page, pages.size AS pgsize" +
            " FROM eeprom, pages" +
            " WHERE pages.category = eeprom.category" +
            " AND eeprom.name = :name" +
            " ORDER BY pages.page",
    )
    fun getPages(name: String): List<EepromPageRow>

    @Query("SELECT name FROM eeprom WHERE size = :length OR xsize = :length ORDER BY name")
    fun size2id(length: Int): List<String>
}

@Dao
interface PagesDao {
    @Query("SELECT * FROM pages LIMIT 1")
    fun getFirst(): PagesEntity?

    @Query("SELECT COUNT(*) FROM pages")
    fun count(): Int
}

/**
 * Row shape for a runtime-data variable, ported from
 * `DatabaseVariableProvider.getRtVariable`'s `rtoffsets, eeprom, names` join.
 * Every column is aliased explicitly (KTD3.5): `rtoffsets` and `names` both
 * declare `uniqueid`/`secret`/`varname`, and `rtoffsets`/`eeprom` both declare
 * `type` - the legacy `cursor.getColumnIndex(...)` resolved every one of
 * those to the *first* table in the `SELECT rtoffsets.*, names.*, ...` list,
 * i.e. `rtoffsets`. Explicit aliasing reproduces that first-match resolution
 * instead of leaving it to Room's (different) ambiguous-column handling.
 */
data class RtVariableRow(
    val uniqueid: Int,
    @ColumnInfo(name = "ecm_type") val ecmType: String?,
    val origname: String?,
    val varname: String,
    val type: String,
    val size: Int,
    val offset: Int,
    val scale: String?,
    val translate: String?,
    val format: String?,
    val name: String?,
    val remark: String?,
    val description: String?,
    val units: String?,
    val low: String?,
    val high: String?,
    val ulow: String?,
    val uhigh: String?,
)

/**
 * Row shape for an EEPROM variable, ported from
 * `DatabaseVariableProvider.getEEPROMVariable`/`getNearestEEPROMVariable`'s
 * `eeoffsets, eeprom, names` join. Same explicit-aliasing rationale as
 * [RtVariableRow]: `eeoffsets`/`names` both declare `uniqueid`/`varname`, and
 * `eeoffsets`/`eeprom` both declare `type`; legacy first-match resolution
 * picked `eeoffsets` for both.
 */
data class EeVariableRow(
    val uniqueid: Int,
    @ColumnInfo(name = "ecm_type") val ecmType: String?,
    val origname: String?,
    val varname: String,
    val type: String,
    val size: Int,
    val offset: Int,
    val scale: String?,
    val translate: String?,
    val format: String?,
    val name: String?,
    val remark: String?,
    val description: String?,
    val units: String?,
    val elemsize: Int?,
    val cols: Int?,
    val rows: Int?,
)

/**
 * Row shape shared by [RtoffsetsDao.getBitSetRow] and
 * [EeoffsetsDao.getBitSetRow], ported from
 * `DatabaseBitSetProvider.getBitSet`'s `<offsets>, bits, eeprom` join.
 *
 * `type` is deliberately aliased from the offsets table (`rtoffsets`/
 * `eeoffsets`), not `eeprom` - the legacy `SELECT * FROM offsets AS offsets,
 * bits, eeprom` put `offsets` first, so `cursor.getColumnIndex("type")`
 * always resolved to the offsets table's `type` column (a `DataType` string
 * such as "Bitfield", never a `DDFI*` ECM type string). Passed through
 * `ECM.Type.getType(...)` this always yields `null` - a pre-existing legacy
 * quirk reproduced here byte-for-byte rather than "fixed".
 */
data class BitSetRow(
    val varname: String,
    val name: String?,
    val offset: Int,
    val type: String,
    val byte: Int?,
    val bit1: String?,
    val bit2: String?,
    val bit3: String?,
    val bit4: String?,
    val bit5: String?,
    val bit6: String?,
    val bit7: String?,
    val bit8: String?,
    val dtc1: String?,
    val dtc2: String?,
    val dtc3: String?,
    val dtc4: String?,
    val dtc5: String?,
    val dtc6: String?,
    val dtc7: String?,
    val dtc8: String?,
    val bitname1: String?,
    val bitname2: String?,
    val bitname3: String?,
    val bitname4: String?,
    val bitname5: String?,
    val bitname6: String?,
    val bitname7: String?,
    val bitname8: String?,
)

@Dao
interface RtoffsetsDao {
    @Query("SELECT * FROM rtoffsets LIMIT 1")
    fun getFirst(): RtoffsetsEntity?

    @Query("SELECT COUNT(*) FROM rtoffsets")
    fun count(): Int

    @Query(
        "SELECT names.origname AS origname" +
            " FROM names, rtoffsets, eeprom" +
            " WHERE eeprom.name = :ecm" +
            " AND rtoffsets.category = eeprom.category" +
            " AND names.varname = rtoffsets.varname" +
            " AND rtoffsets.secret = 0" +
            " AND names.secret = 0" +
            " AND (:type IS NULL OR UPPER(rtoffsets.type) = :type)" +
            " ORDER BY UPPER(names.origname)",
    )
    fun getRtVariableNames(ecm: String, type: String?): List<String>

    @Query(
        "SELECT" +
            " rtoffsets.uniqueid AS uniqueid," +
            " eeprom.type AS ecm_type," +
            " names.origname AS origname," +
            " rtoffsets.varname AS varname," +
            " rtoffsets.type AS type," +
            " rtoffsets.size AS size," +
            " rtoffsets.`offset` AS `offset`," +
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
            " WHERE eeprom.name = :ecm AND names.origname = :name" +
            " AND rtoffsets.category = eeprom.category" +
            " AND names.varname = rtoffsets.varname" +
            " AND names.secret = 0" +
            " AND rtoffsets.secret = 0",
    )
    fun getRtVariable(ecm: String, name: String): RtVariableRow?

    // rtoffsets and eeprom both declare "type"; bits and eeprom both declare
    // "name" - every source column below is table-qualified so SQLite itself
    // never has to resolve an ambiguous bare reference (a bare "name" or
    // "type" here would be a genuine "ambiguous column name" SQL error, not
    // just a Room result-mapping concern). "offset" is additionally
    // backtick-quoted: Room's own SQL grammar (used to validate @Query at
    // compile time) reserves OFFSET as a keyword, so an unquoted "offset"
    // identifier/alias fails KSP with "mismatched input 'offset' expecting
    // {IDENTIFIER, STRING_LITERAL}" even though plain SQLite allows it.
    @Query(
        "SELECT" +
            " rtoffsets.varname AS varname," +
            " bits.name AS name," +
            " rtoffsets.`offset` AS `offset`," +
            " rtoffsets.type AS type," +
            " bits.byte AS byte," +
            " bits.bit1 AS bit1, bits.bit2 AS bit2, bits.bit3 AS bit3, bits.bit4 AS bit4," +
            " bits.bit5 AS bit5, bits.bit6 AS bit6, bits.bit7 AS bit7, bits.bit8 AS bit8," +
            " bits.dtc1 AS dtc1, bits.dtc2 AS dtc2, bits.dtc3 AS dtc3, bits.dtc4 AS dtc4," +
            " bits.dtc5 AS dtc5, bits.dtc6 AS dtc6, bits.dtc7 AS dtc7, bits.dtc8 AS dtc8," +
            " bits.bitname1 AS bitname1, bits.bitname2 AS bitname2, bits.bitname3 AS bitname3, bits.bitname4 AS bitname4," +
            " bits.bitname5 AS bitname5, bits.bitname6 AS bitname6, bits.bitname7 AS bitname7, bits.bitname8 AS bitname8" +
            " FROM rtoffsets, bits, eeprom" +
            " WHERE rtoffsets.varname = :name" +
            " AND bits.varname = rtoffsets.varname" +
            " AND eeprom.name = :ecmId" +
            " AND rtoffsets.category = eeprom.category" +
            " AND rtoffsets.secret = 0",
    )
    fun getBitSetRow(ecmId: String, name: String): BitSetRow?
}

@Dao
interface EeoffsetsDao {
    @Query("SELECT * FROM eeoffsets LIMIT 1")
    fun getFirst(): EeoffsetsEntity?

    @Query("SELECT COUNT(*) FROM eeoffsets")
    fun count(): Int

    @Query(
        "SELECT" +
            " eeoffsets.uniqueid AS uniqueid," +
            " eeprom.type AS ecm_type," +
            " names.origname AS origname," +
            " eeoffsets.varname AS varname," +
            " eeoffsets.type AS type," +
            " eeoffsets.size AS size," +
            " eeoffsets.`offset` AS `offset`," +
            " eeoffsets.scale AS scale," +
            " eeoffsets.translate AS translate," +
            " names.format AS format," +
            " names.name AS name," +
            " names.remark AS remark," +
            " names.description AS description," +
            " names.units AS units," +
            " eeoffsets.elemsize AS elemsize," +
            " eeoffsets.cols AS cols," +
            " eeoffsets.`rows` AS `rows`" +
            " FROM eeoffsets, eeprom, names" +
            " WHERE eeprom.name = :ecm AND names.varname = :name" +
            " AND eeoffsets.category = eeprom.category" +
            " AND eeoffsets.varname = names.varname",
    )
    fun getEeVariable(ecm: String, name: String): EeVariableRow?

    @Query(
        "SELECT" +
            " eeoffsets.uniqueid AS uniqueid," +
            " eeprom.type AS ecm_type," +
            " names.origname AS origname," +
            " eeoffsets.varname AS varname," +
            " eeoffsets.type AS type," +
            " eeoffsets.size AS size," +
            " eeoffsets.`offset` AS `offset`," +
            " eeoffsets.scale AS scale," +
            " eeoffsets.translate AS translate," +
            " names.format AS format," +
            " names.name AS name," +
            " names.remark AS remark," +
            " names.description AS description," +
            " names.units AS units," +
            " eeoffsets.elemsize AS elemsize," +
            " eeoffsets.cols AS cols," +
            " eeoffsets.`rows` AS `rows`" +
            " FROM eeoffsets, eeprom, names" +
            " WHERE eeprom.name = :ecm AND eeoffsets.`offset` <= :targetOffset" +
            " AND eeoffsets.category = eeprom.category" +
            " AND eeoffsets.varname = names.varname" +
            " ORDER BY eeoffsets.`offset` DESC LIMIT 1",
    )
    fun getNearestEeVariable(ecm: String, targetOffset: Int): EeVariableRow?

    // See RtoffsetsDao.getBitSetRow: every source column is table-qualified
    // to avoid a genuine SQL "ambiguous column name" error (eeoffsets/eeprom
    // both declare "type"; bits/eeprom both declare "name"). eeoffsets has no
    // "secret" column, so unlike the runtime-data query there is no secret
    // filter to add here - matches the legacy code's DataSource.EEPROM branch.
    // "offset" is backtick-quoted for the same Room-grammar-keyword reason
    // documented on RtoffsetsDao.getBitSetRow.
    @Query(
        "SELECT" +
            " eeoffsets.varname AS varname," +
            " bits.name AS name," +
            " eeoffsets.`offset` AS `offset`," +
            " eeoffsets.type AS type," +
            " bits.byte AS byte," +
            " bits.bit1 AS bit1, bits.bit2 AS bit2, bits.bit3 AS bit3, bits.bit4 AS bit4," +
            " bits.bit5 AS bit5, bits.bit6 AS bit6, bits.bit7 AS bit7, bits.bit8 AS bit8," +
            " bits.dtc1 AS dtc1, bits.dtc2 AS dtc2, bits.dtc3 AS dtc3, bits.dtc4 AS dtc4," +
            " bits.dtc5 AS dtc5, bits.dtc6 AS dtc6, bits.dtc7 AS dtc7, bits.dtc8 AS dtc8," +
            " bits.bitname1 AS bitname1, bits.bitname2 AS bitname2, bits.bitname3 AS bitname3, bits.bitname4 AS bitname4," +
            " bits.bitname5 AS bitname5, bits.bitname6 AS bitname6, bits.bitname7 AS bitname7, bits.bitname8 AS bitname8" +
            " FROM eeoffsets, bits, eeprom" +
            " WHERE eeoffsets.varname = :name" +
            " AND bits.varname = eeoffsets.varname" +
            " AND eeprom.name = :ecmId" +
            " AND eeoffsets.category = eeprom.category",
    )
    fun getBitSetRow(ecmId: String, name: String): BitSetRow?
}

@Dao
interface NamesDao {
    @Query("SELECT * FROM names LIMIT 1")
    fun getFirst(): NamesEntity?

    @Query("SELECT COUNT(*) FROM names")
    fun count(): Int

    @Query("SELECT name FROM names WHERE varname = :varname LIMIT 1")
    fun getName(varname: String): String?
}

/** Row shape for [BitsDao.getBitNames] - the dynamic `bitname<N>` lookup has no typed DAO equivalent (KTD3 approach step 4). */
data class BitNamesRow(
    val bitname1: String?,
    val bitname2: String?,
    val bitname3: String?,
    val bitname4: String?,
    val bitname5: String?,
    val bitname6: String?,
    val bitname7: String?,
    val bitname8: String?,
)

@Dao
interface BitsDao {
    @Query("SELECT * FROM bits LIMIT 1")
    fun getFirst(): BitsEntity?

    @Query("SELECT COUNT(*) FROM bits")
    fun count(): Int

    @Query(
        "SELECT bitname1, bitname2, bitname3, bitname4, bitname5, bitname6, bitname7, bitname8" +
            " FROM bits WHERE varname = :varname LIMIT 1",
    )
    fun getBitNames(varname: String): BitNamesRow?
}

@Dao
interface AdxbitsDao {
    @Query("SELECT * FROM adxbits LIMIT 1")
    fun getFirst(): AdxbitsEntity?

    @Query("SELECT COUNT(*) FROM adxbits")
    fun count(): Int
}
