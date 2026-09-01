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

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import biz.logicminds.buelltune.data.EcmDefinitionsDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens Room against the shipped `buelltune.db.gz` asset (R6, KTD3) and
 * verifies the prepackaged schema validates and every table's data survived
 * the pipeline intact. This is the test that would catch a prepackaged-schema
 * or `PRAGMA user_version` mismatch, which otherwise only surfaces as a
 * first-launch crash on a rider's phone (`createFromAsset` runs Room's schema
 * validation the first time any DAO touches the database, which happens
 * below on the JVM/instrumentation thread since the database is opened with
 * `allowMainThreadQueries()`).
 */
@RunWith(AndroidJUnit4::class)
class TestEcmDefinitionsDatabase {

    private val db: EcmDefinitionsDatabase
        get() = EcmDefinitionsDatabase.getInstance(ApplicationProvider.getApplicationContext())

    @Test
    fun readsOneRowFromEveryTable() {
        assertNotNull("eeprom", db.eepromDao().getFirst())
        assertNotNull("pages", db.pagesDao().getFirst())
        assertNotNull("eeoffsets", db.eeoffsetsDao().getFirst())
        assertNotNull("rtoffsets", db.rtoffsetsDao().getFirst())
        assertNotNull("names", db.namesDao().getFirst())
        assertNotNull("bits", db.bitsDao().getFirst())
        assertNotNull("adxbits", db.adxbitsDao().getFirst())
    }

    /**
     * Row counts per table, checked against the committed asset (verified
     * directly with the `sqlite3` CLI against the decompressed asset during
     * development) - a cheap guard against shipping a truncated or wrong
     * asset.
     */
    @Test
    fun rowCountsMatchCommittedAsset() {
        assertEquals(19, db.eepromDao().count())
        assertEquals(145, db.pagesDao().count())
        assertEquals(5096, db.eeoffsetsDao().count())
        assertEquals(1785, db.rtoffsetsDao().count())
        assertEquals(850, db.namesDao().count())
        assertEquals(94, db.bitsDao().count())
        assertEquals(248, db.adxbitsDao().count())
    }
}
