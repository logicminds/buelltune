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
package biz.logicminds.buelltune.service

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import biz.logicminds.buelltune.ECM
import biz.logicminds.buelltune.R
import biz.logicminds.buelltune.activities.MainActivity
import biz.logicminds.buelltune.transport.TransportFactory
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Covers R13's other half: `DataChannelFragment` and `LogFragment` -
 * completely unedited beyond U8's one permitted binder-type edit - observe
 * the exact same UI transitions they did before the port, driven by real
 * broadcasts from the real [EcmService]/[PollRecordLoop]/
 * [LegacyBroadcastBridge] chain over a loopback TCP fake ECM
 * ([FakeTcpEcmServer], shared with [EcmServiceBroadcastInstrumentedTest]).
 * **Not executed in this sandbox** (no device/emulator); verified to
 * compile via `./gradlew assembleDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class LegacyFragmentBridgeInstrumentedTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private lateinit var context: Context
    private lateinit var fakeServer: FakeTcpEcmServer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeServer = FakeTcpEcmServer()
    }

    @After
    fun tearDown() {
        fakeServer.close()
    }

    /**
     * `DataChannelFragment.connectionLostReceiver` - registered in
     * `onResume()`, never touched by this unit - disables and unchecks
     * `toggleLiveChannels` on `CONNECTION_LOST` (F1, AE1, R13). Reaches the
     * fragment through `MainActivity`'s existing `CURRENT_FRAGMENT` intent
     * extra rather than driving nav-drawer clicks.
     */
    @Test
    fun dataChannelFragmentToggleDisablesOnRealConnectionLoss() {
        val ecm = ECM.getInstance(context)
        ecm.connect(TransportFactory.tcp("127.0.0.1", fakeServer.port), ECM.Protocol.STOCK)
        ecm.setupEEPROM()

        // EcmService is normally started/bound by MainActivity.onCreate() itself;
        // this extra bind just keeps it alive deterministically for the rule's teardown.
        serviceRule.bindService(Intent(context, EcmService::class.java))

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.CURRENT_FRAGMENT, R.id.nav_datachannels)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            onView(withId(R.id.toggleLiveChannels)).check(matches(isEnabled()))
            onView(withId(R.id.toggleLiveChannels)).perform(click())
            onView(withId(R.id.toggleLiveChannels)).check(matches(isChecked()))

            fakeServer.close()

            waitFor(timeoutMs = 10_000) {
                try {
                    onView(withId(R.id.toggleLiveChannels)).check(matches(not(isEnabled())))
                    true
                } catch (e: AssertionError) {
                    false
                }
            }
            onView(withId(R.id.toggleLiveChannels)).check(matches(not(isEnabled())))
            onView(withId(R.id.toggleLiveChannels)).check(matches(not(isChecked())))
        }
    }

    /**
     * `LogFragment.receiver` - registered in `onResume()`, never touched by
     * this unit - refreshes `logStatusValue` off the real `RECORDING_STARTED`
     * broadcast (R13). Recording is started directly through the bound
     * service (bypassing the SAF storage-location picker `LogFragment`'s
     * own record button requires) so this test can run unattended; the
     * broadcast->receiver->UI path exercised is identical either way.
     */
    @Test
    fun logFragmentStatusUpdatesOnRealRecordingStartedBroadcast() {
        val ecm = ECM.getInstance(context)
        ecm.connect(TransportFactory.tcp("127.0.0.1", fakeServer.port), ECM.Protocol.STOCK)
        ecm.setupEEPROM()

        val binder = serviceRule.bindService(Intent(context, EcmService::class.java))
        val service = (binder as EcmService.EcmServiceBinder).getService()

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.CURRENT_FRAGMENT, R.id.nav_log)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val logFile = File.createTempFile("instrumented-fragment-log", ".bin")
        ActivityScenario.launch<MainActivity>(intent).use {
            onView(withId(R.id.logStatusValue)).check(matches(withText(R.string.status_idle)))

            service.startRecording(FileOutputStream(logFile), 0, ecm)

            waitFor(timeoutMs = 10_000) {
                try {
                    onView(withId(R.id.logStatusValue)).check(matches(not(withText(R.string.status_idle))))
                    true
                } catch (e: AssertionError) {
                    false
                }
            }
            onView(withId(R.id.logStatusValue)).check(matches(not(withText(R.string.status_idle))))

            service.stopRecording()
        }
        logFile.delete()
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
    }
}
