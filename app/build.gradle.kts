plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android") // Hilt-ready per migration spec; wire @HiltAndroidApp when DI is introduced
    id("kotlin-kapt")
}

android {
    namespace = "com.shortscap.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shortscap.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2026072801
        versionName = "1.1.1"

        vectorDrawables { useSupportLibrary = true }
    }

    buildFeatures {
        compose = true
        // BuildConfig is referenced by BackendConfig (debug/release boundary
        // for the temporary development identity, Phase 19 hardening).
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    buildTypes {
        release {
            // R8 minification + resource shrinking (Phase 19 hardening — a
            // resilience measure, not secrecy). See proguard-rules.pro for
            // the explicit keep rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged manifest/resources for Room tests
            // (P1-2 durable sync queue persistence/reload coverage).
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // 2024.10.01 -> compose ui/foundation 1.7.5 + material3 1.3.1: includes the
    // compose-ui 1.7.x patch fixes for Popup/DropdownMenu crashes when opened
    // while the IME is visible or inside a scrollable container (1.7.0 regression).
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")

    // EXIF orientation metadata (profile picture rotation fix)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // Compose UI / Material3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation Compose (deeper drill-down screens if/when added)
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Hilt-ready architecture
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")

    // Coroutines / StateFlow
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Room — durable offline sync queue + Shorts local store (P1-2).
    // The Phase 20 audit documented this seam as a Room/DataStore-based
    // durable queue; kapt is already configured (Hilt), and SharedPreferences
    // is explicitly ruled out for an arbitrary-size event queue.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    // Robolectric — runs Room + the Room-backed sync queue on the JVM so the
    // persistence/reload/restart behavior is testable without a device.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
