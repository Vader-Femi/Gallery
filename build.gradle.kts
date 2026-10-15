// AGP 9 bundles Kotlin Gradle Plugin 2.2.10. Some dependencies (e.g. Coil, Compose) are now built
// with Kotlin 2.4, so the compiler must be upgraded too. Keep this in sync with `kotlin` in
// gradle/libs.versions.toml.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}