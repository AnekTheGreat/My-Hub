// Versions are pinned deliberately: this project uses no AndroidX or Compose, so the whole build
// is the Android Gradle Plugin, the Kotlin plugin, and the framework the phone already ships.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
