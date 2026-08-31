plugins {
    alias(libs.plugins.androidApplication) apply false
}

apply(from = "gradle/ecmsim.gradle.kts")
