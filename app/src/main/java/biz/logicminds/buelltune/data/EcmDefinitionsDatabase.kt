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

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for the bundled, read-only ECM reference database
 * (`assets/buelltune.db.gz`, decompressed by aapt to `buelltune.db`).
 *
 * Replaces `DBHelper`'s hand-rolled `SQLiteOpenHelper`/asset-copy flow
 * (R6, KD3, KTD3). This is a prepackaged database opened via
 * [Room.databaseBuilder]'s `createFromAsset`, never created or migrated by
 * Room itself: the asset's `PRAGMA user_version` (set to 1 by U1's rename)
 * must match [version] below, and there are no [androidx.room.Migration]s
 * because a content refresh means shipping a new asset with a bumped
 * version, not an in-place schema change (KTD3.1).
 *
 * `allowMainThreadQueries()` is required (KTD3.3): the legacy Fragment
 * screens call the provider APIs backed by this database synchronously from
 * the UI thread, and R13 forbids restructuring them to be asynchronous in
 * this slice.
 */
@Database(
    entities = [
        EepromEntity::class,
        PagesEntity::class,
        EeoffsetsEntity::class,
        RtoffsetsEntity::class,
        NamesEntity::class,
        BitsEntity::class,
        AdxbitsEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class EcmDefinitionsDatabase : RoomDatabase() {
    abstract fun eepromDao(): EepromDao
    abstract fun pagesDao(): PagesDao
    abstract fun eeoffsetsDao(): EeoffsetsDao
    abstract fun rtoffsetsDao(): RtoffsetsDao
    abstract fun namesDao(): NamesDao
    abstract fun bitsDao(): BitsDao
    abstract fun adxbitsDao(): AdxbitsDao

    companion object {
        private const val DB_NAME = "buelltune.db"

        @Volatile
        private var instance: EcmDefinitionsDatabase? = null

        /**
         * Returns the process-wide singleton, building it on first access.
         * Mirrors the old `DBHelper`/`DatabaseVariableProvider`/
         * `DatabaseBitSetProvider`/`EEPROM` call sites, each of which used to
         * open its own `SQLiteOpenHelper`; Room's own connection pooling
         * makes a single shared instance the correct replacement rather than
         * one per caller.
         */
        @JvmStatic
        fun getInstance(context: Context): EcmDefinitionsDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EcmDefinitionsDatabase::class.java,
                    DB_NAME,
                )
                    // Asset filename has no ".gz" - matches aapt's
                    // extension-stripping behavior the legacy DBHelper relied on.
                    .createFromAsset(DB_NAME)
                    .allowMainThreadQueries()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
