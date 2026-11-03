plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signing comes from the environment, so no key material lives in this repository. CI writes the
// keystore out of the KEYSTORE_BASE64 secret and points SIGNING_STORE_FILE at it. The names here
// are not storePassword/keyAlias: inside signingConfigs those are the DSL's own properties, and a
// local val of the same name would win the lookup on the right of the assignment.
val signingStorePath: String? = System.getenv("SIGNING_STORE_FILE")
val signingStorePass: String? = System.getenv("SIGNING_STORE_PASSWORD")
val signingAlias: String = System.getenv("SIGNING_KEY_ALIAS") ?: "myhub"

android {
    namespace = "com.myhub.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.myhub.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 7
        versionName = "1.5.0"
    }

    signingConfigs {
        if (signingStorePath != null && signingStorePass != null) {
            create("release") {
                // The key is a PKCS12 made with openssl, not a JKS.
                storeFile = file(signingStorePath)
                storeType = "PKCS12"
                storePassword = signingStorePass
                keyAlias = signingAlias
                keyPassword = signingStorePass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Null when no keystore was supplied, which leaves the release APK unsigned rather
            // than failing a debug build on a machine that has no key.
            signingConfig = signingConfigs.findByName("release")
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
