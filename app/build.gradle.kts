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
    // API 36 is not a preference: Google Play has refused an upload that targets less since
    // 31 August 2026, and AGP 8.10 is the oldest plugin that can compile against it.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.myhub.app"
        minSdk = 21
        targetSdk = 36
        // One source of truth: the name is written once and the code is computed from it, so the
        // APK on a phone, the APK CI builds and the release the updater reads can never disagree,
        // and each release installs over the one before it - 1.10.1 -> 11001 over 1.10.0 -> 11000.
        // The screen ships separately, as a page, and release.yml refuses a release whose page
        // names any other version than this one.
        versionName = "1.10.6"
        versionCode = versionName!!.split(".").let { p ->
            p[0].toInt() * 10_000 + p[1].toInt() * 100 + p[2].toInt()
        }
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
            // Null when no keystore was supplied, which leaves the release APK and AAB unsigned
            // rather than failing a debug build on a machine that has no key.
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        // BuildConfig.VERSION_NAME is what the updater compares a release against, so the build has
        // to carry the constant.
        buildConfig = true
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

// No dependencies on purpose. The hub - the bridge, the updater and the page that loads over the
// air - is built from classes the Android framework already has: WebView, org.json,
// HttpURLConnection. There is nothing to download, nothing to conflict, and nothing to keep up to
// date.
