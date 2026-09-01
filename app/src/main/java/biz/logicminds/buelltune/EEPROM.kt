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
import android.util.Log
import biz.logicminds.buelltune.data.EepromPageRow
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/**
 * This class allows manipulating the bytes stored in the ECMs EEPROM.
 */
class EEPROM(val id: String) {

    private val pageList = ArrayList<Page>()
    private var length = 0
    private var data: ByteArray? = null
    private var eepromRead = false
    private var touched = false
    private var xsize = 0

    /** No public setter - only ever assigned while building a fresh instance from [fromPageRows]. */
    var type: ECM.Type? = null
        private set

    var version: String? = null

    fun length(): Int = length

    fun getBytes(): ByteArray? = data

    fun setBytes(data: ByteArray) {
        this.data = data
        this.length = data.size
    }

    fun getPages(): Collection<Page> = pageList

    fun addPage(page: Page) {
        pageList.add(page)
    }

    override fun toString(): String =
        "EEPROM[id: $id, type: $type, version: $version, length: $length, number of pages: ${pageList.size}]"

    fun getPageCount(): Int = pageList.size

    fun getPage(pageno: Int): Page? = pageList.firstOrNull { it.nr() == pageno }

    fun getXsize(): Int = xsize

    fun isEepromRead(): Boolean = eepromRead

    fun setEepromRead(eepromRead: Boolean) {
        this.eepromRead = eepromRead
    }

    fun touch(offset: Int, length: Int) {
        Log.d(TAG, "touch ($offset,$length)")
        // Mark page dirty
        for (pg in pageList) {
            if ((pg.nr() == 0 && offset < 0) || (offset >= pg.start() && offset < pg.start() + pg.length())) {
                pg.touch()
            }
        }
        touched = true
    }

    fun isTouched(): Boolean = touched

    fun saved() {
        touched = false
    }

    fun hasPageZero(): Boolean = length == xsize

    /**
     * A single EEPROM page. Holds an outer-instance reference via
     * [getParent] -- this must stay a Kotlin `inner class` (not a plain
     * nested class) or that reference silently breaks.
     */
    inner class Page(private val pageNr: Int, private val pageLength: Int) {
        private var startOffset = 0
        private var pageTouched = false

        fun setStart(start: Int) {
            startOffset = start
        }

        fun nr(): Int = pageNr

        fun start(): Int = startOffset

        fun length(): Int = pageLength

        fun getParent(): EEPROM = this@EEPROM

        fun getBytes(offset: Int, length: Int, buffer: ByteArray, bufferPos: Int): ByteArray {
            val src = getParent().getBytes()!!
            System.arraycopy(src, startOffset + offset, buffer, bufferPos, length)
            return buffer
        }

        override fun toString(): String =
            String.format(Locale.ENGLISH, "Page[#: %2d, start: %04X, length: %04X]", pageNr, startOffset, pageLength)

        fun touch() {
            Log.d(TAG, "Page $pageNr marked dirty")
            pageTouched = true
        }

        fun saved() {
            pageTouched = false
        }

        fun isTouched(): Boolean = pageTouched
    }

    companion object {
        private const val TAG = "EEPROM"

        /**
         * Pure page-layout construction from already-fetched rows -- the
         * part of the legacy `EEPROM.get(String, Context)` that has no
         * database or `Context` dependency. Reused by [RoomEcmDefinitionsProvider]
         * today and, per KTD7, by a future non-Android `EcmDefinitionsProvider`
         * (U10) reading the same row shape via JDBC.
         */
        internal fun fromPageRows(name: String, rows: List<EepromPageRow>): EEPROM? {
            if (rows.isEmpty()) {
                return null
            }
            val eeprom = EEPROM(name)
            var pc = 0
            for (row in rows) {
                if (eeprom.length == 0) {
                    val xs = row.xsize ?: 0
                    eeprom.length = xs
                    eeprom.xsize = xs
                    eeprom.type = ECM.Type.getType(row.type)
                    eeprom.data = ByteArray(eeprom.length)
                }
                val pnr = row.page
                val sz = row.pgsize
                val pg = eeprom.Page(pnr, sz)
                if (pnr == 0) {
                    pg.setStart(eeprom.length - pg.length())
                } else {
                    pg.setStart(pc)
                    pc += pg.length()
                }
                eeprom.pageList.add(pg)
            }
            return eeprom
        }

        /**
         * Legacy facade (KTD5): resolves [AppContainer] from [context] and
         * delegates to its [EcmDefinitionsProvider] - the same interface
         * [ECM.setupEEPROM] is now constructor-injected with (KTD7).
         */
        @JvmStatic
        fun get(name: String?, context: Context): EEPROM? {
            if (name == null) {
                return null
            }
            return AppContainer.from(context).definitionsProvider.getEeprom(name)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun load(context: Context, id: String, `in`: InputStream): EEPROM {
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var length: Int
            while (`in`.read(buffer).also { length = it } > 0) {
                bytes.write(buffer, 0, length)
            }
            bytes.flush()
            `in`.close()

            val data = bytes.toByteArray()

            val eeprom = get(id, context) ?: throw FileNotFoundException(context.getString(R.string.unsupported_eeprom, id))
            eeprom.setBytes(data)
            for (pg in eeprom.getPages()) {
                pg.touch()
            }
            eeprom.setEepromRead(true)
            return eeprom
        }

        @JvmStatic
        @Throws(IOException::class)
        fun size2id(context: Context, length: Int): Array<String> {
            val ret = AppContainer.from(context).definitionsProvider.size2id(length)
            if (ret.isEmpty()) {
                throw IOException(context.getString(R.string.unable_to_determine_ecm_type))
            }
            Log.d(TAG, "EEPROM ID(s) from size: $ret")
            return ret.toTypedArray()
        }
    }
}
