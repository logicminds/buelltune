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
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mirrors the `eeprom` table of the bundled reference database: one row per
 * supported ECM model, describing its overall EEPROM layout.
 *
 * No `defaultValue` is declared on any column here (deliberately): the legacy
 * DDL's `DEFAULT NULL` / `DEFAULT ''` values would otherwise make Room compare
 * defaults it cannot know without an explicit declaration and fail prepackaged
 * schema validation.
 */
@Entity(tableName = "eeprom")
data class EepromEntity(
    val category: Int,
    val pages: Int,
    @ColumnInfo(name = "Secret") val secret: Int?,
    val size: Int?,
    val type: String?,
    val xsize: Int?,
    @ColumnInfo(name = "poll_len") val pollLen: Int?,
    val name: String?,
    val file: String?,
    @PrimaryKey val uniqueid: Int,
    val remark: String?,
)

/** Mirrors the `pages` table: EEPROM page layout per ECM category. */
@Entity(tableName = "pages")
data class PagesEntity(
    @PrimaryKey val uniqueid: Int,
    val category: Int,
    val page: Int,
    val size: Int,
)

/** Mirrors the `eeoffsets` table: EEPROM variable/table/axis definitions. */
@Entity(
    tableName = "eeoffsets",
    indices = [Index(name = "eeoffsets_idx_eeoffs_cat_offs", value = ["category", "offset"])],
)
data class EeoffsetsEntity(
    @PrimaryKey val uniqueid: Int,
    val category: Int,
    val varname: String,
    val type: String,
    val size: Int,
    val offset: Int,
    // Numeric-looking but varchar in the legacy DDL (KTD3.2) - nullable String,
    // parsed in the DAO-to-Variable mapping layer, not here.
    val scale: String?,
    val translate: String?,
    val elements: Int?,
    val elemsize: Int?,
    val cols: Int?,
    val rows: Int?,
    val skip: Int?,
    val axistranslate: String?,
    val axisscale: String?,
    val xaxis: String?,
    val yaxis: String?,
)

/** Mirrors the `rtoffsets` table: runtime (live data) variable definitions. */
@Entity(
    tableName = "rtoffsets",
    indices = [
        Index(name = "rtoffsets_idx_rtoffs_cat_offs", value = ["category", "offset"]),
        Index(name = "rtoffsets_idx_rtoffs_varn_offs", value = ["varname", "offset"]),
    ],
)
data class RtoffsetsEntity(
    @PrimaryKey val uniqueid: Int,
    val category: Int,
    val secret: Int?,
    val varname: String,
    val type: String,
    val size: Int,
    val offset: Int,
    // Numeric-looking but varchar in the legacy DDL (KTD3.2) - nullable String,
    // parsed in the DAO-to-Variable mapping layer, not here.
    val scale: String?,
    val translate: String?,
    val low: String?,
    val high: String?,
    val ulow: String?,
    val uhigh: String?,
)

/** Mirrors the `names` table: human-readable labels/descriptions for variables. */
@Entity(
    tableName = "names",
    indices = [Index(name = "names_idx_names_varname", value = ["varname"])],
)
data class NamesEntity(
    @PrimaryKey val uniqueid: Int,
    val secret: Int?,
    val varname: String,
    val origname: String?,
    val name: String?,
    val remark: String?,
    val description: String?,
    val units: String?,
    val format: String?,
    val axisunits: String?,
    val axisformat: String?,
    val monospy: Int?,
    val palmspy: Int?,
    val livedata: String?,
    val priority: Int?,
    val idhash: String?,
)

/** Mirrors the `bits` table: bitfield/DTC definitions for a single bitfield variable. */
@Entity(tableName = "bits")
data class BitsEntity(
    @PrimaryKey val uniqueid: Int,
    val varname: String,
    val name: String?,
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

/**
 * Mirrors the `adxbits` table: ADX bit definitions. Not consumed by any
 * provider yet (no current Java call site reads this table); declared for
 * schema completeness so Room's prepackaged-schema validation covers all
 * seven shipped tables.
 */
@Entity(tableName = "adxbits")
data class AdxbitsEntity(
    @ColumnInfo(name = "UniqueID") @PrimaryKey val uniqueId: Int,
    val varname: String?,
    val title: String?,
    val description: String?,
    val bit: Int?,
    val mtrue: String?,
    val mfalse: String?,
    val idhash: String?,
)
