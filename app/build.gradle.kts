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
        versionCode = 1
        versionName = "2.4.1" // matches "ShortsCap v2.4.1" footer string in Drawer/About

        vectorDrawables { useSupportLibrary = true }
    }

    buildFeatures {
        compose = true
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
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
