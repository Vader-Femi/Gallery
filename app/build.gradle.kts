plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Room writes the schema of every database version here; keep the files in version control so migrations can be checked.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.femi.gallery"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.femi.gallery"
        minSdk = 31
        targetSdk = 37
        // The release workflow sets this to its run number, so that every published APK is newer than the last.
        versionCode = System.getenv("RELEASE_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // The key of the APKs the release workflow publishes: it comes from the workflow's secrets (see README).
        val releaseKeystore = System.getenv("RELEASE_KEYSTORE_PATH")
        if (!releaseKeystore.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // With the secrets the APK is signed with the real release key, so that it can update an installed copy.
            // Without them (a build on your own computer) it uses the debug key: for testing the (much faster)
            // release build on your phone only.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        noCompress += "onnx"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    // The index database: scan results and file fingerprints.
    implementation(libs.androidx.room.runtime)
    // Keeps the library scans going when the app is closed.
    implementation(libs.androidx.work.runtime.ktx)
    // Runs the image and text models of describe-a-photo on the phone (the model files are installed by the person).
    implementation(libs.onnxruntime.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.paging.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.tink.android)
    implementation(libs.androidx.exifinterface)
    // Reads the text in photos. This is the bundled model (it is part of the app and works offline), not the
    // one that downloads through Google Play services.
    implementation(libs.mlkit.text.recognition)
    // Names objects and scenes in photos (bundled model, offline).
    implementation(libs.mlkit.image.labeling)
    // Detects faces in photos for people grouping.
    implementation(libs.mlkit.face.detection)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.telephoto.zoomable.image.coil3)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}