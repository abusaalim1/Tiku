plugins {
    id("com.android.application")
    id("io.github.takahirom.roborazzi") version "1.36.0"
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")  // Firebase project tiku-chat
}
android {
    namespace = "com.tiku.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "tiku.app"
        minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0"
        // ROUND 3, ITEM 6: arm64-v8a ONLY. The owner's + partner's phones are
        // arm64-class; shipping the other ABIs' WebRTC natives was dead weight.
        // TRADEOFF: 32-bit-only phones cannot install this APK (stated in the
        // release notes). ABI filter (NOT splits) keeps the single
        // app-release.apk output path the CI expects.
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildFeatures { compose = true }
    packaging {
        resources {
            excludes += setOf(
                // ROUND 5, ITEM 6: leftover metadata traces (round-4 audit).
                // Neither is read at runtime: DebugProbesKt.bin is consulted
                // only when kotlinx-coroutines debug probes are explicitly
                // installed (never in production), and
                // kotlin-tooling-metadata.json is Kotlin build-tooling metadata.
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    signingConfigs {
        // Stable release certificate (tiku.keystore at repo root, alias "tiku").
        // The password arrives from the TIKU_KEYSTORE_PASSWORD repo secret as env.
        create("stable") {
            storeFile = rootProject.file("tiku.keystore")
            storePassword = System.getenv("TIKU_KEYSTORE_PASSWORD")
            keyAlias = "tiku"
            keyPassword = System.getenv("TIKU_KEYSTORE_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("stable")
            // ROUND 3, ITEM 6: R8 minify + resource shrinking. Keep rules cover
            // WebRTC + the app itself (app/proguard-rules.pro); Firebase /
            // Credential / Coil ship their own consumer rules. The Roborazzi
            // screenshot suite is the smoke check — if R8 breaks something it
            // can't catch, this reverts to minify OFF (ABI cut alone is an
            // acceptable result).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
tasks.withType<Test>().configureEach {
    // diagnostics for the Roborazzi screenshot suite: full stacks in the CI log
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        events = setOf(org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED)
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.core:core:1.15.0")
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // No firebase-storage: the owner is on the free Spark plan; media lives in
    // Firestore chunks (see MediaStore.kt). The storage-only bucket is unused.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Real audio+video calls: maintained WebRTC fork (org.webrtc API), Maven Central.
    implementation("io.getstream:stream-webrtc-android:1.1.1")
    // ---- UI screenshot tests (Roborazzi + Robolectric, headless CI) ----
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testRuntimeOnly("org.robolectric:nativeruntime:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.36.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.36.0")
}
