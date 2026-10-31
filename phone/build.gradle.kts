plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "tv.jetflix.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = "tv.jetflix.phone"
        minSdk = 26
        targetSdk = 35
        // The phone build number. Phone releases are their own phone-build-N line, separate from the TV build-N releases.
        versionCode = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1.0"
    }

    val releaseKeystore = System.getenv("JETFLIX_KEYSTORE_FILE")
    if (releaseKeystore != null) {
        signingConfigs {
            create("ci") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("JETFLIX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("JETFLIX_KEY_ALIAS")
                keyPassword = System.getenv("JETFLIX_KEY_PASSWORD")
            }
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("phone/keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "800m"
                // Robolectric + Compose state piles up in a long-lived JVM and makes whichever UI
                // test runs late stop rendering (clicks land, nothing recomposes, awaits time out).
                // A fresh JVM every few classes keeps every class on the clean slate it expects.
                it.forkEvery = 4L
                it.maxParallelForks = 1
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("ci")
            isMinifyEnabled = false
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // One checked-in debug key for every build machine, so a new phone APK always
            // installs over the last one. A signature change forces an uninstall, which
            // wipes the saved sign-in; the default per-machine debug key did exactly that.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

// CI failures need the full assertion message (console default drops it).
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    testLogging {
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
