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
package biz.logicminds.buelltune.activities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import biz.logicminds.buelltune.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #8 regression lock (Android 12 Bluetooth Classic permission dead-end,
 * `ecmdroid/ecmdroid#8` / `logicminds/buelltune#2`).
 *
 * Uses the strongest revoke mechanism actually reachable from an
 * instrumented test with no `adb` client in this sandbox:
 * [android.app.UiAutomation.executeShellCommand] (real shell access
 * granted to the instrumentation process) running `pm revoke`/`pm
 * grant` - the exact `adb shell pm revoke` U11a's own approach called
 * for, just invoked through `UiAutomation` instead of an external `adb`
 * process.
 *
 * `MainActivity.showDevices()` is invoked by reflection (it is private).
 * Driving it through the connect FAB instead would additionally require
 * real Bluetooth Classic hardware to be present and enabled on the test
 * device/emulator (`connect()` early-returns with a "Bluetooth not
 * available" `Toast` before ever calling `showDevices()` otherwise) -
 * a precondition entirely orthogonal to #8's permission-handling bug.
 * Calling the real (unmodified) private method directly keeps this lock
 * independent of whatever Bluetooth hardware a given CI device happens
 * to expose, while still exercising the exact production permission
 * chain: `ActivityCompat.checkSelfPermission` ->
 * `ActivityCompat.requestPermissions` ->
 * `MainActivity.onRequestPermissionsResult` (public, called directly,
 * exactly as the OS would after the user taps "Allow") -> `showDevices()`
 * resumed automatically, with no second tap from this test.
 *
 * **Not executed in this sandbox** (no device/emulator available, no
 * `/dev/kvm`); verified to compile via `./gradlew assembleDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityBluetoothPermissionInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runShellCommand("pm revoke ${context.packageName} ${Manifest.permission.BLUETOOTH_CONNECT}")
        waitFor {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
        }
    }

    @After
    fun tearDown() {
        // Leave the real OS permission state as this suite found it so unrelated
        // tests elsewhere in the run are never left with BLUETOOTH_CONNECT revoked.
        runShellCommand("pm grant ${context.packageName} ${Manifest.permission.BLUETOOTH_CONNECT}")
    }

    @Test
    fun deniedThenGrantedBluetoothConnectResumesDeviceListWithoutSecondTap() {
        assertNotEquals(
            "Precondition: BLUETOOTH_CONNECT must actually be revoked before this test drives showDevices()",
            PackageManager.PERMISSION_GRANTED,
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // -- Denied: showDevices() must request the permission, not crash or
            // silently no-op, and must not yet show the paired-device dialog. --
            // Real ActivityCompat.requestPermissions() on API 31+ launches the
            // system's GrantPermissionsActivity in the foreground, which pauses
            // MainActivity down to STARTED - that's the OS actually presenting a
            // permission prompt, not a bug; asserting RESUMED here would mean
            // the permission dialog never appeared. isAtLeast(STARTED) checks
            // the one thing this step actually claims: no crash, not destroyed.
            scenario.onActivity { activity -> invokeShowDevices(activity) }
            assertTrue(scenario.state.isAtLeast(Lifecycle.State.STARTED))

            // -- The permission is actually (re-)granted at the OS level... --
            runShellCommand("pm grant ${context.packageName} ${Manifest.permission.BLUETOOTH_CONNECT}")
            waitFor {
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
            }

            // -- ...and the real onRequestPermissionsResult() callback fires exactly
            // as the OS would after the user taps "Allow" - resuming showDevices()
            // itself, with no second tap from this test (R10, AE3, F2). --
            scenario.onActivity { activity ->
                activity.onRequestPermissionsResult(
                    1,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    intArrayOf(PackageManager.PERMISSION_GRANTED),
                )
            }

            onView(withText(R.string.paired_devices)).inRoot(isDialog()).check(matches(isDisplayed()))
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    private fun invokeShowDevices(activity: MainActivity) {
        val method = MainActivity::class.java.getDeclaredMethod("showDevices")
        method.isAccessible = true
        method.invoke(activity)
    }

    private fun runShellCommand(command: String) {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    private fun waitFor(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
    }
}
