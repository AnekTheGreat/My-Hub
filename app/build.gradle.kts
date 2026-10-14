plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.myhub.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.myhub.app"
        minDk = 21
        targetSdk = 34
        versionCode = 3
        versionName = "1.1.1"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // The launcher icon is a vector, which lint flags for very old devices. It renders on
        // API 21+ and the adaptive icon takes over from API 26, so this stays a warning.
        abortOnError = false
    }
}

// No dependencies on purpose. The hub is built from classes the Android framework already has,
// so there is nothing to download, nothing to conflict, and nothing to keep up to date.
