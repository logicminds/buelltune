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

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.rule.ServiceTestRule
import biz.logicminds.buelltune.ECM
import biz.logicminds.buelltune.PDU
import biz.logicminds.buelltune.transport.PduFraming
import biz.logicminds.buelltune.transport.TransportFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Instrumented coverage for U8's R13/R2 scenarios that cannot be exercised
 * from a plain JVM test: real broadcast delivery and a real `Service`'s
 * foreground state. **Not executed in this sandbox** (no device/emulator
 * available); verified to compile via `./gradlew assembleDebugAndroidTest`
 * and written to run unattended against a loopback TCP fake ECM (no real
 * BLE/USB/Bluetooth-Classic hardware needed - KTD7's "no protocol changes
 * needed to simulate a dropped link" applies at this smaller scale too:
 * closing the fake server's client socket is enough to trigger a real
 * `TcpTransport` I/O failure).
 *
 * Covers:
 *  - Each of the four legacy broadcast actions firing with its exact
 *    string on the corresponding state change (R13).
 *  - The service entering the foreground on `startReading()` and leaving
 *    it once both reading and recording have stopped (R2).
 *  - `CONNECTION_LOST` firing when the underlying link actually drops
 *    (F1, AE1), driven end-to-end through the real [EcmService] ->
 *    [PollRecordLoop] -> [LegacyBroadcastBridge] chain - not a mock of any
 *    of them.
 */
@RunWith(AndroidJUnit4::class)
class EcmServiceBroadcastInstrumentedTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    // Production code only ever gets here after the user has already
    // granted POST_NOTIFICATIONS through MainActivity's own runtime
    // request flow; this test binds straight to EcmService and skips that
    // UI, so without an explicit grant, startForeground()'s notification
    // silently never posts on API 33+ (the foreground *service* state
    // still applies - it just has no visible notification to assert on).
    // POST_NOTIFICATIONS is only a runtime-dangerous permission from API 33
    // (TIRAMISU) onward; below that it's a normal, install-time-granted
    // permission, and GrantPermissionRule's `pm grant` shell call fails
    // outright ("Failed to grant permissions") if asked to grant a
    // permission the platform doesn't treat as revocable - confirmed on a
    // real API 26 run.
    @get:Rule
    val notificationPermissionRule: TestRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            TestRule { base, _ -> base }
        }

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

    @Test
    fun broadcastsFireWithExactActionStringsAndServiceTracksForegroundState() {
        val ecm = ECM.getInstance(context)
        ecm.connect(TransportFactory.tcp("127.0.0.1", fakeServer.port), ECM.Protocol.STOCK)
        ecm.setupEEPROM()

        val received = LinkedBlockingQueue<String>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                intent.action?.let { received.add(it) }
            }
        }
        val filter = IntentFilter().apply {
            addAction(EcmService.REALTIME_DATA)
            addAction(EcmService.RECORDING_STARTED)
            addAction(EcmService.RECORDING_STOPPED)
            addAction(EcmService.CONNECTION_LOST)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)

        try {
            val binder = serviceRule.bindService(Intent(context, EcmService::class.java))
            val service = (binder as EcmService.EcmServiceBinder).getService()

            // -- REALTIME_DATA + foreground on startReading() --
            service.startReading()
            assertEquals(EcmService.REALTIME_DATA, received.poll(10, TimeUnit.SECONDS))
            assertTrueEventually("no active foreground notification after startReading()") {
                activeNotificationCount() > 0
            }

            // -- RECORDING_STARTED / RECORDING_STOPPED --
            val logFile = File.createTempFile("instrumented-test-log", ".bin")
            try {
                service.startRecording(FileOutputStream(logFile), 0, ecm)
                assertEquals(EcmService.RECORDING_STARTED, received.poll(10, TimeUnit.SECONDS))

                service.stopRecording()
                assertEquals(EcmService.RECORDING_STOPPED, received.poll(10, TimeUnit.SECONDS))
            } finally {
                logFile.delete()
            }

            service.stopReading()
            assertFalse(service.isReading())
            assertFalse(service.isRecording())
            assertTrueEventually("foreground notification still active after both reading and recording stopped") {
                activeNotificationCount() == 0
            }

            // -- CONNECTION_LOST on a real link drop (F1, AE1) --
            service.startReading()
            assertEquals(EcmService.REALTIME_DATA, received.poll(10, TimeUnit.SECONDS))
            fakeServer.close()
            assertEquals(EcmService.CONNECTION_LOST, received.poll(10, TimeUnit.SECONDS))
            assertTrueEventually("foreground notification still active after a connection loss") {
                activeNotificationCount() == 0
            }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private fun activeNotificationCount(): Int {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.activeNotifications.size
    }

    private fun assertTrueEventually(message: String, timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(25)
        }
        org.junit.Assert.assertTrue(message, condition())
    }
}

/**
 * Minimal protocol-aware fake ECM over a real loopback TCP socket, built on
 * the production [PduFraming] codec directly rather than duplicating it -
 * matching one connection, replying to a version request and any number of
 * runtime-data requests, until [close] drops the accepted client socket to
 * simulate a real link loss.
 */
internal class FakeTcpEcmServer : AutoCloseable {
    private val serverSocket = ServerSocket(0)
    val port: Int get() = serverSocket.localPort

    @Volatile private var clientSocket: Socket? = null

    private val thread = Thread {
        try {
            val socket = serverSocket.accept()
            clientSocket = socket
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            while (!socket.isClosed) {
                val request = try {
                    PduFraming.readFrame(input)
                } catch (e: IOException) {
                    break
                }
                try {
                    PduFraming.writeFrame(output, respond(request))
                } catch (e: IOException) {
                    break
                }
            }
        } catch (e: IOException) {
            // Listening or client socket closed -- the test is done with us.
        }
    }.apply {
        isDaemon = true
        start()
    }

    private fun respond(request: PDU): PDU {
        val command = request.getPayload().getOrNull(0)
        val data = when (command) {
            PDU.CMD_VERSION -> "BUEIB310 12-11-03".toByteArray(Charsets.US_ASCII)
            else -> ByteArray(50)
        }
        val payload = ByteArray(1 + data.size)
        payload[0] = PDU.ACK
        System.arraycopy(data, 0, payload, 1, data.size)
        return PDU(PDU.getECMID(), PDU.DROID_ID, payload)
    }

    override fun close() {
        try {
            clientSocket?.close()
        } catch (e: IOException) {
            // Already closed.
        }
        try {
            serverSocket.close()
        } catch (e: IOException) {
            // Already closed.
        }
    }
}
