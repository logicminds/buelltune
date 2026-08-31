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

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Owns the lifecycle of one real `ecmsim` (third_party/ecmsim, U9/KTD6)
 * process: launches the already-built `ecmsim.jar` via [ProcessBuilder],
 * blocks until its own startup log line ("Waiting for incoming connection
 * on port N...") confirms it is listening, and tears it down again. This
 * is the "start a local ECM simulator" half of R16/KTD7 -- [EcmSimRule]
 * wraps it for ordinary JUnit before/after lifecycle, and the two
 * connection-loss tests (R17, [EcmSimConnectionLossIntegrationTest]) call
 * [kill] directly so they can take the process out from under a live
 * connection without disturbing any other test.
 *
 * Requires `third_party/ecmsim/target/ecmsim.jar` to already exist --
 * built via `./gradlew ecmsimBuild -PecmsimJavaHome=/path/to/jdk21`
 * (U9/KTD6). The `ecmsimIntegrationTest` Gradle task (`app/build.gradle.kts`)
 * depends on `ecmsimBuild` so this is a non-issue when run through that
 * task; a bare `./gradlew testDebugUnitTest`/IDE run of these classes
 * needs the jar pre-built (see [jarPath]'s error message).
 */
internal class EcmSimProcess(
    private val model: String,
    private val port: Int,
    private val xprFile: File,
    private val logFile: File,
    private val javaHome: String? = ecmsimJavaHomeFromEnv(),
) {
    @Volatile private var process: Process? = null

    /** Starts the simulator and blocks until it reports readiness or [readyTimeoutMs] elapses. */
    fun start(readyTimeoutMs: Long = 20_000) {
        check(process == null) { "EcmSimProcess is already started" }
        val javaBin = if (javaHome != null) File(javaHome, "bin/java").absolutePath else "java"
        val command = listOf(
            javaBin, "-jar", jarPath().absolutePath, model,
            "--port", port.toString(),
            "--xpr", xprFile.absolutePath,
            "--log", logFile.absolutePath,
        )
        val started = ProcessBuilder(command).redirectErrorStream(true).start()
        process = started

        val readyMarker = "Waiting for incoming connection on port $port"
        val output = StringBuilder()
        val readyLatch = CountDownLatch(1)
        val pump = Thread {
            try {
                BufferedReader(InputStreamReader(started.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val text = line!!
                        synchronized(output) { output.append(text).append('\n') }
                        if (text.contains(readyMarker)) {
                            readyLatch.countDown()
                        }
                    }
                }
            } catch (e: IOException) {
                // Process exited/stream closed -- readyLatch stays at 0 if
                // that happened before readiness, caught by the timeout
                // check below.
            }
        }.apply { isDaemon = true; start() }

        val ready = readyLatch.await(readyTimeoutMs, TimeUnit.MILLISECONDS)
        if (!ready) {
            stop(forcibly = true)
            pump.join(1000)
            error(
                "ecmsim (model=$model, port=$port) did not report readiness within " +
                    "${readyTimeoutMs}ms. Output so far:\n${synchronized(output) { output.toString() }}",
            )
        }
    }

    /** True while the OS process is still running. */
    fun isAlive(): Boolean = process?.isAlive == true

    /**
     * [Process.destroyForcibly] the underlying OS process -- the R17
     * "kill the simulator process" scenario, as distinct from a
     * client-side socket close ([EcmSimConnectionLossIntegrationTest]'s
     * other scenario, which never touches this process at all).
     */
    fun kill() = stop(forcibly = true)

    /** Graceful-then-forceful shutdown, used for ordinary test teardown ([EcmSimRule.after]). Safe to call more than once (e.g. after [kill]). */
    fun stop(forcibly: Boolean = false) {
        val active = process ?: return
        process = null
        if (forcibly) {
            active.destroyForcibly()
        } else {
            active.destroy()
            if (!active.waitFor(2, TimeUnit.SECONDS)) {
                active.destroyForcibly()
            }
        }
        active.waitFor(5, TimeUnit.SECONDS)
    }

    companion object {
        /** Resolves `third_party/ecmsim/target/ecmsim.jar` -- see class doc for how it must be built first. */
        fun jarPath(): File {
            val configured = System.getProperty("buelltune.ecmsimJar")
            val candidate = if (configured != null) {
                File(configured)
            } else {
                File("third_party/ecmsim/target/ecmsim.jar")
            }
            check(candidate.exists()) {
                "ecmsim.jar not found at ${candidate.absolutePath}. Build it first: " +
                    "./gradlew ecmsimBuild -PecmsimJavaHome=/path/to/jdk21 (see AGENTS.md/README.md), " +
                    "or run the whole suite via ./gradlew ecmsimIntegrationTest, which depends on that task."
            }
            return candidate
        }

        private fun ecmsimJavaHomeFromEnv(): String? =
            System.getProperty("buelltune.ecmsimJavaHome") ?: System.getenv("ECMSIM_JAVA_HOME")
    }
}
