plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose) // Enables Compose compiler plugin
}

android {
    namespace = "com.example.yu_gi_ohlogger" // Make sure this matches your package name
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.yu_gi_ohlogger"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true // Tells Android Studio to enable Jetpack Compose UI
    }
}

dependencies {
    // Android Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose Bill of Materials (BOM) & Material 3 UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Icons
    implementation(libs.androidx.compose.material.icons.extended)

    // Retrofit & Gson (YGOPRODeck API)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // Coil (Image Loading)
    implementation(libs.coil.compose)
}