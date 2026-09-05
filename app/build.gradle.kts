import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.ksp)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val signingEnabled = keystorePropertiesFile.exists()

android {
    if (signingEnabled) {
        val keystoreProperties = Properties()
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
        signingConfigs {
            create("config") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }
    defaultConfig {
        applicationId = "biz.logicminds.buelltune"
        versionCode = 3
        versionName = "0.2.0"
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingEnabled) {
                signingConfig = signingConfigs.getByName("config")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    namespace = "biz.logicminds.buelltune"
    buildFeatures {
        buildConfig = true
        compose = true
    }
    compileSdk = 37
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
    lint {
        // The 28 baselined MissingPermission findings all live in the
        // vendored de.kai_morich.simple_bluetooth_le_terminal package
        // (AGENTS.md: "treat as external, avoid unrelated edits") --
        // pre-existing Bluetooth Classic/BLE calls never guarded by an
        // explicit permission check. Baselining them (rather than
        // disabling MissingPermission project-wide) keeps lint enforcing
        // that check everywhere else, including any new first-party code.
        baseline = file("lint-baseline.xml")
    }
    sourceSets {
        getByName("main") {
            kotlin.srcDir("src/main/java")
        }
        getByName("debug") {
            // R12/AE4 follow-up: the debug-only Compose shell
            // (biz.logicminds.buelltune.ui) lives here, not under
            // src/main/java, so it and the Compose runtime it depends on
            // (see the debugImplementation(libs.compose.*) block below)
            // never compile into a release build - see
            // BuellTuneDebugActivity's class doc.
            kotlin.srcDir("src/debug/java")
        }
        getByName("test") {
            kotlin.srcDir("src/test/java")
            java.srcDir("src/sharedTest/java")
            resources.srcDir("src/androidTest/resources")
        }
        getByName("androidTest") {
            kotlin.srcDir("src/androidTest/java")
            java.srcDir("src/sharedTest/java")
        }
    }
}


dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.drawerlayout)
    implementation(libs.usbserial)
    implementation(libs.documentfile)
    implementation(libs.koog.agents)
    // koog-agents' own umbrella POM pulls stable Anthropic/OpenAI/Ollama/Bedrock
    // clients but not OpenRouter/DeepSeek/Google (KD4): each is a separate
    // publishable artifact on the same 1.2.0 release train, added explicitly.
    implementation(libs.koog.openrouter.client)
    implementation(libs.koog.deepseek.client)
    implementation(libs.koog.google.client)
    // Koog resolves its HTTP client engine via a runtime ServiceLoader-style
    // lookup, not a compile-time default -- without this, every provider
    // client throws IllegalStateException("No KoogHttpClient.Factory
    // provider found on the runtime classpath...") the first time it's
    // actually used. Found via manual smoke testing against ecmsimRun (a
    // real device/emulator run), not caught by any unit/instrumented test
    // since U6's agentic-loop tests use a fake PromptExecutor that never
    // exercises a real provider client's HTTP construction path.
    implementation(libs.koog.http.client.ktor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.tables)
    implementation(libs.preference.ktx)
    implementation(libs.recyclerview)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    // Compose runtime stays module-wide `implementation` rather than
    // `debugImplementation`, even though only the debug-only shell under
    // src/debug/java uses it (see BuellTuneDebugActivity's class doc):
    // the Compose Compiler Gradle plugin (`libs.plugins.composeCompiler`)
    // requires the runtime on every variant's compile classpath -
    // including release's, even with zero `@Composable` sources in that
    // variant - and fails `compileReleaseKotlin` with
    // `IncompatibleComposeRuntimeVersionException` otherwise (verified).
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)
    implementation(libs.compose.lifecycle.viewmodel)
    implementation(libs.compose.lifecycle.runtime)
    implementation(libs.compose.navigation)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.test.rules)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.intents)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    // Registers a bare, manifest-declared androidx.activity.ComponentActivity
    // for createAndroidComposeRule<ComponentActivity>() to launch -- debug-only
    // per Compose's own testing setup guidance, never merged into release.
    debugImplementation(libs.compose.ui.test.manifest)
}

// ecmsim-backed JVM integration suite (R16, R17, AE5, KTD7) -- every class
// under biz.logicminds.buelltune.integration carries this JUnit @Category
// marker so it can be excluded from the default unit test tasks (starting
// a real ecmsim process per test class is too slow for the inner dev loop,
// see AGENTS.md/docs/DEVELOPER_GUIDE.md) and run instead, in full, via the
// dedicated ecmsimIntegrationTest task below -- one documented command.
val ecmSimIntegrationCategory = "biz.logicminds.buelltune.integration.EcmSimIntegrationSuite"

tasks.withType<Test>().configureEach {
    // Neither the bundled reference-database asset nor the ecmsim jar/JDK
    // lives on the JVM test classpath -- both need a real filesystem path,
    // and relying on the Test task's default working directory would be an
    // undocumented assumption. Harmless no-ops for ordinary unit tests that
    // never read these properties.
    systemProperty("buelltune.assetsDir", layout.projectDirectory.dir("src/main/assets").asFile.absolutePath)
    systemProperty(
        "buelltune.ecmsimJar",
        rootProject.layout.projectDirectory.dir("third_party/ecmsim/target").file("ecmsim.jar").asFile.absolutePath,
    )
    // Same -PecmsimJavaHome/ECMSIM_JAVA_HOME convention gradle/ecmsim.gradle.kts
    // already uses for ecmsimBuild/ecmsimRun.
    val ecmsimJavaHome = (project.findProperty("ecmsimJavaHome") as String?) ?: System.getenv("ECMSIM_JAVA_HOME")
    if (ecmsimJavaHome != null) {
        systemProperty("buelltune.ecmsimJavaHome", ecmsimJavaHome)
    }
}

tasks.matching { it.name == "testDebugUnitTest" || it.name == "testReleaseUnitTest" }.configureEach {
    (this as Test).useJUnit {
        excludeCategories(ecmSimIntegrationCategory)
    }
}

val ecmsimIntegrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the ecmsim-backed JVM integration suite (R16, R17, AE5) against a real " +
        "local ecmsim process. Excluded from the default test task since starting a real " +
        "simulator per test class is too slow for the inner dev loop. Requires " +
        "third_party/ecmsim/target/ecmsim.jar (built automatically via the ecmsimBuild " +
        "dependency below; pass -PecmsimJavaHome=/path/to/jdk21 if the default JDK isn't 21+)."
    dependsOn(":ecmsimBuild")
    useJUnit {
        includeCategories(ecmSimIntegrationCategory)
    }
    // Exercises a real external process each run; never treat as up-to-date.
    outputs.upToDateWhen { false }
}

// testDebugUnitTest's testClassesDirs/classpath are only fully resolved
// once AGP has finished registering the variant unit test tasks (in its
// own afterEvaluate) -- deferring this copy the same way keeps
// ecmsimIntegrationTest running the exact same compiled classes/classpath
// as the default suite, without re-deriving Room/Kotlin/AGP's classpath
// wiring by hand.
afterEvaluate {
    val debugUnitTest = tasks.named<Test>("testDebugUnitTest").get()
    ecmsimIntegrationTest.configure {
        testClassesDirs = debugUnitTest.testClassesDirs
        classpath = debugUnitTest.classpath
    }
}
