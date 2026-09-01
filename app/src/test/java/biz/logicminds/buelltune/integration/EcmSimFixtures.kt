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

import biz.logicminds.buelltune.TestUtils
import java.io.File
import java.nio.file.Files

/**
 * Materializes the bundled `androidTest` BUEIB fixtures ([TestUtils],
 * already on the JVM `test` classpath via `app/build.gradle.kts`'s
 * `resources.srcDir("src/androidTest/resources")`) as real files on disk
 * for `ecmsim`'s `--xpr`/`--log` options, which need a filesystem path,
 * not a classpath resource.
 *
 * The EEPROM dump is zero-padded with `ecmsim`'s missing 4-byte page zero,
 * exactly like `gradle/ecmsim.gradle.kts`'s `ecmsimRun` task does for the
 * manual harness: `ecmsim`'s own `Main.prepareEEPROM()` requires the dump
 * length to match its `ecm.json` configuration exactly (1210 bytes for
 * BUEIB, the androidTest fixture is 1206), and `ecmsim` never reads/writes
 * page zero over TCP, so the padding is behaviorally inert.
 */
internal object EcmSimFixtures {
    private val tempDir: File by lazy {
        Files.createTempDirectory("ecmsim-integration").toFile().apply { deleteOnExit() }
    }

    private val paddedEepromFile: File by lazy {
        val padded = File(tempDir, "BUEIB-with-page-zero.eeprom")
        padded.writeBytes(TestUtils.readEEPROM() + ByteArray(4))
        padded.deleteOnExit()
        padded
    }

    private val runtimeLogFile: File by lazy {
        val log = File(tempDir, "BUEIB_log.bin")
        log.writeBytes(TestUtils.readBinaryLog())
        log.deleteOnExit()
        log
    }

    /** The BUEIB EEPROM dump, padded to `ecmsim`'s expected 1210-byte length. */
    fun paddedEeprom(): File = paddedEepromFile

    /** The BUEIB runtime-data log `ecmsim` cycles through for `CMD_RTDATA` responses. */
    fun runtimeLog(): File = runtimeLogFile
}
