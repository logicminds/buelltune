/*
 * Builds and runs the `ecmsim` ECM simulator (https://github.com/ecmdroid/ecmsim), vendored as a
 * pinned git submodule at third_party/ecmsim. See README.md's "Running the simulator" section and
 * docs/DEVELOPER_GUIDE.md for usage.
 *
 * ecmsim is a standalone Maven project (maven.compiler.source/target 21) with its own bundled
 * Maven wrapper (`mvnw`) — it is built and run independently of the Android app module and its
 * Gradle/AGP toolchain. Building it requires a JDK 21+ JAVA_HOME; this is a separate requirement
 * from (and may exceed) whatever JDK the Android build itself uses. Export JAVA_HOME to a JDK 21+
 * install before invoking these tasks, or pass -PecmsimJavaHome=/path/to/jdk21 (or set
 * ECMSIM_JAVA_HOME) to point just these two tasks at one without touching the rest of the build.
 */

val windows = org.gradle.internal.os.OperatingSystem.current().isWindows

val ecmsimDir = file("third_party/ecmsim")
val ecmsimJar = file("third_party/ecmsim/target/ecmsim.jar")
val ecmsimJavaHome: String? = (project.findProperty("ecmsimJavaHome") as String?) ?: System.getenv("ECMSIM_JAVA_HOME")

val ecmsimBuild by tasks.registering(Exec::class) {
    group = "ecmsim"
    description = "Builds the ecmsim ECM simulator (third_party/ecmsim) via its bundled Maven " +
        "wrapper. Requires a JDK 21+ JAVA_HOME (see -PecmsimJavaHome=/path/to/jdk21)."

    workingDir = ecmsimDir
    commandLine(if (windows) "mvnw.cmd" else "./mvnw", "-q", "package")
    if (ecmsimJavaHome != null) {
        environment("JAVA_HOME", ecmsimJavaHome)
    }

    // Gradle's content-hash based up-to-date check (not a hand-rolled timestamp comparison):
    // re-runs only when the submodule's sources/build files changed since the jar was produced.
    inputs.files(fileTree(ecmsimDir) { include("src/**", "pom.xml", ".mvn/**") })
        .withPropertyName("ecmsimSources")
    outputs.file(ecmsimJar)
}

val ecmsimRun by tasks.registering(Exec::class) {
    group = "ecmsim"
    description = "Runs the ecmsim ECM simulator jar built by ecmsimBuild. Configure with " +
        "-PecmsimModel=, -PecmsimPort=, -PecmsimXpr=, -PecmsimLog= (all optional; defaults " +
        "exercise the bundled BUEIB fixtures on port 6280, distinct from ecmsim's own default " +
        "port 6275 so a manually running instance is not disturbed)."
    dependsOn(ecmsimBuild)

    val model = (project.findProperty("ecmsimModel") as String?) ?: "BUEIB"
    val port = (project.findProperty("ecmsimPort") as String?) ?: "6280"
    val log = (project.findProperty("ecmsimLog") as String?) ?: "app/src/androidTest/resources/BUEIB_log.bin"

    // ecmsim requires an --xpr dump whose length exactly matches its BUEIB definition (1210
    // bytes: 1206 bytes of pages 1-6 plus a 4-byte page-zero header). The androidTest BUEIB.eeprom
    // fixture is 1206 bytes — the app itself tolerates page-zero-less dumps (EEPROM.hasPageZero()
    // is simply false for them, see EEPROM.java) but ecmsim's Main.prepareEEPROM() does not. When
    // -PecmsimXpr isn't given, zero-pad a copy of the default fixture with ecmsim's missing 4-byte
    // page zero (ecmsim never reads/writes page zero over TCP, so the padding is behaviorally
    // inert) instead of touching the real fixture file. An explicit -PecmsimXpr= is used verbatim.
    val xprOverride = project.findProperty("ecmsimXpr") as String?
    val xpr = xprOverride ?: run {
        val rawFixture = file("app/src/androidTest/resources/BUEIB.eeprom")
        val padded = file("${layout.buildDirectory.get()}/ecmsim/BUEIB-with-page-zero.eeprom")
        val paddedBytes = rawFixture.readBytes() + ByteArray(4)
        if (!padded.exists() || !padded.readBytes().contentEquals(paddedBytes)) {
            padded.parentFile.mkdirs()
            padded.writeBytes(paddedBytes)
        }
        padded.absolutePath
    }

    val javaBin = if (ecmsimJavaHome != null) {
        file("$ecmsimJavaHome/bin/${if (windows) "java.exe" else "java"}").absolutePath
    } else {
        "java"
    }

    val cmd = mutableListOf(javaBin, "-jar", ecmsimJar.absolutePath, model, "--port", port)
    if (xpr.isNotBlank()) cmd += listOf("--xpr", xpr)
    if (log.isNotBlank()) cmd += listOf("--log", log)
    commandLine(cmd)
}
