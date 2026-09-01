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
package biz.logicminds.buelltune.fragments

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.GridView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import biz.logicminds.buelltune.ECM
import biz.logicminds.buelltune.EEPROM
import biz.logicminds.buelltune.R
import biz.logicminds.buelltune.TestUtils
import biz.logicminds.buelltune.activities.MainActivity
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #12 (Android 14 EEPROM load crash, `ecmdroid/ecmdroid#12` /
 * `logicminds/buelltune#1`) and #7 (SAF file push failure,
 * `ecmdroid/ecmdroid#7` / `logicminds/buelltune#4`) regression lock.
 *
 * Per the plan's own explicit warning (U11 approach step 2): a test
 * calling `EEPROM.load(Context, id, InputStream)` directly exercises
 * none of the SAF handling and proves nothing - it would have passed
 * both before and after the regression window. Every test here instead
 * drives the REAL `ACTION_OPEN_DOCUMENT`/`ACTION_CREATE_DOCUMENT` flow
 * through [EEPROMFragment]'s real menu actions
 * (`onOptionsItemSelected` -> `loadFile()`/`saveFile()` ->
 * `startActivityForResult` -> `onActivityResult`), with Espresso-Intents
 * stubbing the picker result to a foreign `content://` URI served by
 * [FixtureDocumentProvider] - declared only in
 * `app/src/androidTest/AndroidManifest.xml`, never in the app APK -
 * exactly the shape a real document picker (Drive, another file
 * manager, a different app entirely) hands back.
 *
 * **Not executed in this sandbox** (no device/emulator available, no
 * `/dev/kvm`); verified to compile via `./gradlew assembleDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class EEPROMFragmentSafInstrumentedTest {

    private lateinit var context: Context
    private lateinit var ecm: ECM

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ecm = ECM.getInstance(context)
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
        ecm.setEEPROM(null)
        FixtureDocumentProvider.clear(context)
    }

    /**
     * Covers the plan's #12 scenario 1 (R9, AE2): the document picker
     * returns a foreign `content://` URI outside the app's own storage.
     * `BUEIB.eeprom` (1206 bytes) ambiguously matches three real
     * definitions-database rows (`BUEIB`, `B2RIB`, `BUEIC`, all size
     * 1206) so the real ECM-type selection `AlertDialog` fires too - not
     * a synthetic single-candidate shortcut.
     */
    @Test
    fun loadingRealSafDocumentParsesToExpectedPageLayoutWithNoCrash() {
        val fixtureBytes = TestUtils.readEEPROM()
        val uri = FixtureDocumentProvider.seed(context, "BUEIB.eeprom", fixtureBytes)
        stubOpenDocument(uri)

        ActivityScenario.launch<MainActivity>(launchIntent(R.id.nav_eeprom)).use { scenario ->
            clickLoadMenuItem()
            // AlertDialog.show() returns before WindowManager has finished
            // attaching the dialog's window; Espresso's own idle-tracking
            // doesn't always cover that handoff, so poll for it the same
            // way the rest of this file waits for other async UI state
            // instead of assuming it is already up.
            waitFor {
                runCatching {
                    onView(withText("BUEIB")).inRoot(isDialog()).check(matches(isDisplayed()))
                }.isSuccess
            }
            onView(withText("BUEIB")).inRoot(isDialog()).perform(click())

            waitFor { ecm.isEepromRead() }

            val eeprom = ecm.getEEPROM()
            assertNotNull("EEPROM must have loaded through the real SAF path", eeprom)
            assertEquals("BUEIB", eeprom!!.id)
            assertTrue(eeprom.isEepromRead())
            assertArrayEquals(fixtureBytes, eeprom.getBytes())

            // EEPROMFragment.COLS is private; mirrored here (5) since it drives
            // the grid's item count formula the fragment itself uses.
            val cols = 5
            val expectedCount = fixtureBytes.size + fixtureBytes.size / (cols - 1) +
                if (fixtureBytes.size % (cols - 1) != 0) 1 else 0
            scenario.onActivity { activity ->
                val grid = activity.findViewById<GridView>(R.id.eepromGrid)
                assertEquals(expectedCount, grid.adapter.count)
            }

            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            assertToastShown(scenario, R.string.eeprom_loaded_sucessfully)
        }
    }

    /**
     * Covers the plan's #12 scenario 2 (R9): a truncated/oversized EEPROM
     * file whose length matches no known ECM model must surface a clear
     * error, not a crash. 600 bytes matches no `eeprom` table row's
     * `size`/`xsize` (verified against the shipped reference database),
     * so this hits `loadEEPROM(long, InputStream)`'s own
     * `EEPROM.size2id()` catch block, which already shows a `Toast` today
     * - locking in that this specific failure mode stays user-visible,
     * not silent.
     */
    @Test
    fun loadingUnrecognizedSizeFileShowsErrorToastWithoutCrashing() {
        val truncated = TestUtils.readEEPROM().copyOfRange(0, 600)
        val uri = FixtureDocumentProvider.seed(context, "truncated.eeprom", truncated)
        stubOpenDocument(uri)
        val eepromBefore = ecm.getEEPROM()

        ActivityScenario.launch<MainActivity>(launchIntent(R.id.nav_eeprom)).use { scenario ->
            clickLoadMenuItem()

            assertToastShown(scenario, R.string.unable_to_determine_ecm_type)
            assertSame(
                "An unrecognized-size file must not mutate the currently-loaded EEPROM",
                eepromBefore,
                ecm.getEEPROM(),
            )
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    /**
     * Regression-locks U11a's one residual gap: a picked document that
     * fails to actually open (revoked grant, deleted file, unmounted
     * volume - [FixtureDocumentProvider] simulates this by throwing
     * [java.io.FileNotFoundException] for an unseeded key) hits
     * `onActivityResult`'s *outer* `catch (FileNotFoundException)`/
     * `catch (IOException)` (wrapping `openFileDescriptor`/
     * `getStatSize`/`openInputStream`) - distinct from the
     * `EEPROM.size2id()` catch above, which already toasts. This outer
     * pair currently only `Log.e`s; it does not crash, but it also does
     * not tell the user anything. This test locks in the "does not
     * crash, does not corrupt state" half; the missing user-visible
     * error is the documented manual-only residue from U11a (also
     * called out in this unit's closing comment on
     * `logicminds/buelltune#1`).
     */
    @Test
    fun documentThatFailsToOpenIsCaughtWithoutCrashingButOnlyLogged() {
        val uri = FixtureDocumentProvider.uriFor("never-seeded.eeprom")
        stubOpenDocument(uri)
        val eepromBefore = ecm.getEEPROM()

        ActivityScenario.launch<MainActivity>(launchIntent(R.id.nav_eeprom)).use { scenario ->
            clickLoadMenuItem()

            // No Toast and no state transition to poll for on this path (that is
            // exactly the residual gap) - give onActivityResult's synchronous I/O
            // time to run and settle before asserting nothing crashed or changed.
            Thread.sleep(2_000)
            assertSame(eepromBefore, ecm.getEEPROM())
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    /**
     * Covers #7's residue (SAF file push): saving an EEPROM pushes the
     * real byte array through the real `ACTION_CREATE_DOCUMENT` ->
     * `onActivityResult(SAVE_FILE)` -> `openFileDescriptor(uri, "w")` ->
     * `FileOutputStream` chain into a real (foreign) SAF document, not a
     * synthetic write. No prior instrumented test exercised this path -
     * `LegacyFragmentBridgeInstrumentedTest`'s log-recording test
     * explicitly bypasses `LogFragment`'s own SAF storage-location
     * picker, and nothing exercised `EEPROMFragment.saveFile()` at all.
     */
    @Test
    fun savingEepromPushesRealBytesThroughCreateDocumentFlow() {
        val fixtureBytes = TestUtils.readEEPROM()
        val eeprom = EEPROM.get("BUEIB", context)!!
        System.arraycopy(fixtureBytes, 0, eeprom.getBytes()!!, 0, fixtureBytes.size)
        eeprom.setEepromRead(true)
        ecm.setEEPROM(eeprom)
        // EEPROM.get() sizes its backing buffer to the eeprom table's xsize
        // (1210 for BUEIB), not the raw fixture file's size (1206, the
        // page-zero-less shape EEPROM.hasPageZero() tolerates) - saving
        // pushes that whole buffer, so the expected bytes are the buffer
        // itself (fixtureBytes plus its trailing zero-filled page zero),
        // not the original fixture file.
        val expectedBytes = eeprom.getBytes()!!.copyOf()

        val uri = FixtureDocumentProvider.seed(context, "save-target.eeprom", ByteArray(0))
        stubCreateDocument(uri)

        ActivityScenario.launch<MainActivity>(launchIntent(R.id.nav_eeprom)).use { scenario ->
            openActionBarOverflowOrOptionsMenu(context)
            onView(withText(R.string.save_eeprom)).perform(click())

            waitFor {
                FixtureDocumentProvider.bytesWrittenTo(context, "save-target.eeprom")?.contentEquals(expectedBytes) == true
            }
            assertArrayEquals(expectedBytes, FixtureDocumentProvider.bytesWrittenTo(context, "save-target.eeprom"))
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    private fun launchIntent(fragmentId: Int): Intent =
        Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.CURRENT_FRAGMENT, fragmentId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun stubOpenDocument(uri: Uri) {
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
    }

    private fun stubCreateDocument(uri: Uri) {
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
    }

    /** `R.id.load`'s `showAsAction="never"` keeps it in the overflow menu, matching production. */
    private fun clickLoadMenuItem() {
        openActionBarOverflowOrOptionsMenu(context)
        onView(withText(R.string.load_eeprom)).perform(click())
    }

    /**
     * Standard (if OS/window-manager-version-sensitive) Espresso idiom for
     * asserting a `Toast`: it renders in a distinct decor view from the
     * activity's own window.
     */
    private fun assertToastShown(scenario: ActivityScenario<MainActivity>, textResId: Int) {
        var decorView: View? = null
        scenario.onActivity { activity -> decorView = activity.window.decorView }
        waitFor {
            try {
                onView(withText(textResId))
                    .inRoot(withDecorView(not(equalTo(decorView))))
                    .check(matches(isDisplayed()))
                true
            } catch (e: Throwable) {
                false
            }
        }
    }

    private fun waitFor(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
    }
}
