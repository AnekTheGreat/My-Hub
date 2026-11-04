// Versions are pinned deliberately: this project uses no AndroidX or Compose, so the whole build
// is the Android Gradle Plugin, the Kotlin plugin, and the framework the phone already ships.
//
// AGP 8.10.1 is the oldest plugin that can compile against API 36, which is what Play requires of
// every upload since 31 August 2026; it in turn wants Gradle 8.11.1, which CI pins. Kotlin moves
// with it to 2.0.21 - the hub has no dependencies, so there is nothing else in the way.
plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
