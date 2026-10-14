# My Hub

One dark screen that launches your apps from one place, modelled on the **OnePlus Game Space /
Game Hub**: the same navy field, the same list of big rounded cards, an icon and a name on the left
and a red launch button on the right. Only yours are your apps instead of games.

| Card | What it opens |
| --- | --- |
| **FamTM** | the FamTM APK |
| **SideCut** | the SideCut APK, the Play build |
| **SideCut Full** | the SideCut APK, the sideloaded full build |
| **MeasureT** | the MeasureT APK |

Tapping a card opens that app. Coming back to the hub puts you back at the top of the list, ready
to open the next one.

## Install

There is no APK in this repository. GitHub builds one for you:

1. Open the [**Actions**](../../actions/workflows/build.yml) tab and pick the newest **Build APK**
   run (every push to `main` starts one).
2. Download the **MyHub-debug-apk** artifact and unzip it — you get `app-debug.apk`.
3. Copy that file to your phone and tap it. Android will ask you to allow installing from this
   source the first time; that is normal for an app that did not come from the Play Store.

Or build it yourself — see [Building](#building).

## How a card finds its app

The hub ships knowing which app each card is meant to be, and asks your phone for it:

| Card | Package it looks for | If that is not installed |
| --- | --- | --- |
| FamTM | `com.familytree.app` | looks for an app named FamTM or Family Tree |
| SideCut | `com.SideCut.myapp` | looks for an app named "SideCut" |
| SideCut Full | `com.SideCut.myapp.full` | looks for an app named "SideCut Full" |
| MeasureT | *(nothing known)* | looks for an app named "MeasureT" or "Measure" |

SideCut is published twice from one pipeline, and the two builds are made to be installed side by
side: the Play build is `com.SideCut.myapp` and named *SideCut*, and the sideloaded full build is
`com.SideCut.myapp.full` and named *SideCut Full*. Because they are two separate apps on the phone,
they get a card each — one card could only ever open one of them. If you have one of the two, the
other card says so and offers the `+` to point it somewhere else.

MeasureT's package name is not published anywhere, so its card is the one most likely to need a
hand: **press and hold the card** and choose the app from a list of everything installed on the
phone. The hub remembers the choice. On that same screen, *Use the default* puts the card back to
searching by itself.

The card also tells you where it stands: its app icon and package name when it found something,
and *"Not installed · tap to point this card at an app"* with a `+` button when it did not. The
line under the title counts how many of the cards found their app.

Press and hold any card for **Open**, **Choose the app…** and **App info**, and use the gear in the
top right for **Refresh** and a short explanation of the whole thing.

## What it does not do

- **No permissions.** Asking your phone what is installed and starting another app are both allowed
  without one, so the hub requests none.
- **No network.** There is no internet permission in the manifest, so nothing can be sent anywhere.
- **No libraries.** No AndroidX, no Compose, no third-party code — the APK is a few hundred KB of
  framework calls, and the only thing it keeps is the name of an app you picked by hand, if you
  ever picked one.

## Building

Needs JDK 17 and the Android SDK (platform 34, build-tools 34.0.0).

```bash
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

The build is pinned to Gradle 8.9 and Android Gradle Plugin 8.5.2. The repository carries no
Gradle wrapper binary, so CI installs Gradle 8.9 itself — run `gradle wrapper` once if you would
rather use `./gradlew` locally. Android Studio can also just open the folder.

To build a release APK you will need your own keystore; the release build type is there but
unsigned.

## Layout

```
app/src/main/java/com/myhub/app/
    Apps.kt          the apps, how each is found, and the remembered choice
    MainActivity.kt  the hub screen, its cards, and the picker
app/src/main/res/    colours, theme, settings gear, launcher icon
.github/workflows/   builds the APK on every push
```

## Version

`1.1.0` — `com.myhub.app`, minSdk 21 (Android 5), targetSdk 34 (Android 14).
