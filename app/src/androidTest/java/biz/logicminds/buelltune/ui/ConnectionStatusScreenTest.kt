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
package biz.logicminds.buelltune.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import biz.logicminds.buelltune.transport.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for R12's Compose acceptance scenario: the screen
 * renders "disconnected" initially, then reflects a connected state and a
 * non-zero RPM once the (fake) service emits - proving the full
 * ViewModel -> StateFlow -> recomposition path. **Not executed in this
 * sandbox** (no device/emulator available); verified to compile via
 * `./gradlew assembleDebugAndroidTest`.
 *
 * Hosted on the real debug-only [BuellTuneDebugActivity] launcher entry
 * point rather than a bare test activity, then [FakeEcmLiveDataSource]
 * swaps in for [ServiceEcmLiveDataSource] via [ConnectionStatusScreen]'s
 * `viewModel` parameter - the same seam a real
 * [biz.logicminds.buelltune.service.EcmService] connects through in
 * production, so no real service binding, broadcast registration, or ECM
 * connection is needed to exercise the reactive pipeline.
 */
@RunWith(AndroidJUnit4::class)
class ConnectionStatusScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<BuellTuneDebugActivity>()

    @Test
    fun rendersDisconnectedThenConnectedWithLiveRpm() {
        val source = FakeEcmLiveDataSource()
        val viewModel = ConnectionStatusViewModel(source)

        composeTestRule.setContent {
            ConnectionStatusScreen(viewModel = viewModel)
        }

        composeTestRule.onNodeWithText("Connection: disconnected").assertIsDisplayed()
        composeTestRule.onNodeWithText("RPM: --").assertIsDisplayed()

        composeTestRule.runOnUiThread {
            source.connectionStateFlow.value = ConnectionState.Connected
            source.rpmFlow.value = 3200
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Connection: connected").assertIsDisplayed()
        composeTestRule.onNodeWithText("RPM: 3200").assertIsDisplayed()
    }

    @Test
    fun connectionLostFallsBackToDisconnected() {
        val source = FakeEcmLiveDataSource()
        val viewModel = ConnectionStatusViewModel(source)

        composeTestRule.setContent {
            ConnectionStatusScreen(viewModel = viewModel)
        }

        composeTestRule.runOnUiThread {
            source.connectionStateFlow.value = ConnectionState.Connected
            source.rpmFlow.value = 4100
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Connection: connected").assertIsDisplayed()

        composeTestRule.runOnUiThread {
            source.connectionStateFlow.value =
                ConnectionState.Failed(biz.logicminds.buelltune.transport.FailureCause.Io(java.io.IOException("dropped")))
            source.rpmFlow.value = null
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Connection: disconnected").assertIsDisplayed()
        composeTestRule.onNodeWithText("RPM: --").assertIsDisplayed()
    }
}

/**
 * Test double for [EcmLiveDataSource]: exposes the backing
 * [MutableStateFlow]s directly so a test can drive emissions without any
 * real [biz.logicminds.buelltune.service.EcmService], `bindService`, or
 * broadcast receiver - simulating "the fake/test service instance emits"
 * from R12's acceptance scenario.
 */
internal class FakeEcmLiveDataSource : EcmLiveDataSource {
    val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val rpmFlow = MutableStateFlow<Int?>(null)

    override val connectionState: StateFlow<ConnectionState> = connectionStateFlow
    override val rpm: StateFlow<Int?> = rpmFlow

    override fun close() = Unit
}
