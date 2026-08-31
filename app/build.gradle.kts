import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.ksp)
    alias(libs.plugins.composeCompiler)
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
        versionCode = 28
        versionName = "0.99.8"
        minSdk = 26
        targetSdk = 36
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
    compileSdk = 36
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
    sourceSets {
        getByName("main") {
            kotlin.srcDir("src/main/java")
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
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
