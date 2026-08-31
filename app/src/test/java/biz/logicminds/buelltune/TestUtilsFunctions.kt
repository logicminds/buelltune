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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Direct coverage for [Utils.hexdump]/[Utils.toHex] (R14). `hexdump` already
 * had incidental coverage via `TestPDU`'s `Utils.hexdump(pdu.getBytes())`
 * assertions; `toHex` had none (only called from `EEPROMAdapter`/
 * `EEPROMFragment`, both Android-only UI code) -- both get direct,
 * intentional JVM tests here.
 */
class TestUtilsFunctions {

    @Test
    fun hexdumpFormatsBytesColonSeparatedUppercase() {
        assertEquals("01:02:FF", Utils.hexdump(byteArrayOf(0x01, 0x02, 0xFF.toByte())))
    }

    @Test
    fun hexdumpOfEmptyArrayReturnsPlaceholder() {
        assertEquals("<empty>", Utils.hexdump(ByteArray(0)))
    }

    @Test
    fun hexdumpWithOffsetAndLenStopsAtArrayBounds() {
        // len (10) runs past the end of the 3-byte array -- hexdump must
        // stop at the array bound rather than throwing.
        assertEquals("02:03", Utils.hexdump(byteArrayOf(1, 2, 3), 1, 10))
    }

    @Test
    fun toHexPadsToRequestedWidth() {
        assertEquals("0FF", Utils.toHex(0xFF, 3))
        assertEquals("00FF", Utils.toHex(0xFF, 4))
    }

    @Test
    fun toHexWithoutWidthDefaultsToTwoDigits() {
        assertEquals("FF", Utils.toHex(0xFF))
        assertEquals("0A", Utils.toHex(0x0A))
    }

    @Test
    fun isEmptyStringTreatsNullAndBlankAsEmpty() {
        assertEquals(true, Utils.isEmptyString(null))
        assertEquals(true, Utils.isEmptyString(""))
        assertEquals(true, Utils.isEmptyString("   "))
        assertEquals(false, Utils.isEmptyString("x"))
    }
}
