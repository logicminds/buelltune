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

import org.junit.rules.ExternalResource
import java.net.ServerSocket

/**
 * JUnit lifecycle rule (R16 approach step 1): grabs a free TCP port,
 * starts a fresh [EcmSimProcess] for [model] against the padded BUEIB
 * fixtures ([EcmSimFixtures]), and blocks until it reports readiness
 * before the wrapped test(s) run; always stops it afterward
 * (`ExternalResource.after()` runs even on test failure).
 *
 * Usable either as a `@ClassRule` (one simulator process shared by every
 * `@Test` in a class, each opening/closing its own TCP connection
 * sequentially -- `ecmsim` serves one connection at a time in a loop, per
 * U9's own verification notes, so sequential reconnects against the same
 * process are exactly what it expects) or as an instance `@Rule` (a
 * brand-new process per test method) -- [EcmSimConnectionLossIntegrationTest]
 * uses the latter because killing the process in one test must never
 * disturb another.
 */
class EcmSimRule(private val model: String = "BUEIB") : ExternalResource() {
    var host: String = "127.0.0.1"
        private set
    var port: Int = -1
        private set

    private lateinit var process: EcmSimProcess

    /** The underlying process, exposed so [EcmSimConnectionLossIntegrationTest] can [EcmSimProcess.kill] it directly. */
    internal val underlyingProcess: EcmSimProcess get() = process

    override fun before() {
        port = ServerSocket(0).use { it.localPort }
        process = EcmSimProcess(
            model = model,
            port = port,
            xprFile = EcmSimFixtures.paddedEeprom(),
            logFile = EcmSimFixtures.runtimeLog(),
        )
        process.start()
    }

    override fun after() {
        process.stop()
    }
}
