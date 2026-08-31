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

/**
 * JUnit `@Category` marker for the `ecmsim`-backed integration suite (R16,
 * R17, AE5). Every test class in this package carries
 * `@Category(EcmSimIntegrationSuite::class)` so `app/build.gradle.kts` can
 * exclude the whole suite from the default `test`/`testDebugUnitTest`
 * task (starting a real simulator process per test class is too slow for
 * the inner dev loop) while still running it, in full, from the dedicated
 * `ecmsimIntegrationTest` Gradle task -- one documented command, per the
 * plan's U10 approach step 6.
 */
interface EcmSimIntegrationSuite
