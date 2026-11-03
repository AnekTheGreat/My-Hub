# My Hub

One dark screen that launches your apps from one place, modelled on the **OnePlus Game Space /
Game Hub**: the same navy field, the same list of rounded cards, an icon and a name on the left. Only
yours are your apps instead of games — and the last two cards are slots you fill yourself.

| Card | What it opens |
| --- | --- |
| **FamTM** | the FamTM APK |
| **SideCut** | the SideCut APK, the Play build |
| **SideCut Full** | the SideCut APK, the sideloaded full build |
| **MeasureT** | the MeasureT APK |
| **Slot 1**, **Slot 2** | whatever app you put in them |

Tapping a card opens that app. Coming back to the hub puts you back at the top of the list, ready
to open the next one.

## The two slots

The last two cards are empty on purpose. A slot is not built for anything: tap it, pick any app on
the phone, and from then on it wears that app's icon and name and opens it like any other card.
Hold a filled slot for **Change the app…** or **Empty this slot**, which puts it back to empty
without touching the time it earned. Nothing about a slot is baked into the APK, so nothing about it
can go stale.

## Install

There is no APK in this repository. GitHub builds one for you:

1. Open the [**Releases**](../../releases) page and take the newest `MyHub-<version>.apk`. That is
the signed build: [release.yml](../../actions/workflows/release.yml) makes it on every `v*` tag, or
when it is dispatched by hand with a version.
2. Prefer a build from a branch? Open the [**Actions**](../../actions/workflows/build.yml) tab, pick
the newest **Build APK** run (every push to `main` starts one), and download the **MyHub-apk**
artifact — it carries both `app-debug.apk` and the signed `app-release.apk`.
3. Copy that file to your phone and tap it. Android will ask you to allow installing from this
   source the first time; that is normal for an app that did not come from the Play Store.

Or build it yourself — see [Building](#building).

## How a card finds its app

The hub ships knowing which app each card is meant to be, and asks your phone for it:

| Card | Package it looks for | If that is not installed |
| --- | --- | --- |
| FamTM | `com.familytree.app` | looks for an app named FamTM or Family Tree |
| SideCut | `com.SideCut.myapp` | looks for an app named "SideCut Play" or "SideCut" |
| SideCut Full | `com.SideCut.myapp.full` | looks for an app named "SideCut Full" |
| MeasureT | `com.CompassEDC.myapp` | looks for an app named "MeasureT", "Measure T", "Measure", "Compass EDC" or "CompassEDC" |
| UJournal | `app.ujournal` | looks for an app named "UJournal" or "Ultimate Journal" |

SideCut is published twice from one pipeline, and the two builds are made to be installed side by
side: the Play build is `com.SideCut.myapp` and named *SideCut*, and the sideloaded full build is
`com.SideCut.myapp.full` and named *SideCut Full*. Because they are two separate apps on the phone,
they get a card each — one card could only ever open one of them. If you have one of the two, the
other card says so, and tapping it points the card somewhere else.

MeasureT is built out of the Compass-EDC repository, and the applicationId that repository's
Capacitor config gives the APK is `com.CompassEDC.myapp` — so that is the package its card looks
for. The name it shipped under before it was renamed, *Compass EDC*, is looked for too, so a phone
still carrying that install opens from the same card rather than from one that says there is
nothing there. UJournal installs from the Journal repository as `app.ujournal`.

If a card still finds nothing at all, **press and hold it** and choose the app from a list of
everything installed on the phone. The hub remembers the choice, and *Use the default* on that same
screen puts the card back to searching by itself.

The card also tells you where it stands: the app's own icon and name when it found something, and a
quiet *"Not installed · tap to choose an app"* when it did not.

Press and hold any card for **Open**, **Choose the app…** and **App info** — a slot adds **Empty this
slot** — and use the gear in the top right for **Refresh**, **♥ Support My Hub** and a short
explanation of the whole thing.

## What it does not do

- **No runtime permissions.** Asking your phone what is installed and starting another app are both
  allowed without one. The hub does declare `<queries>` in the manifest, which is not a permission
  but the API 30+ way of saying which apps it is allowed to *see* — and it asks for **Usage access**,
  a one-off switch in Settings rather than a prompt, so it can read how long an app was in front.
  With the switch off, no time is recorded and nothing is guessed — see below.
- **No network.** There is no internet permission in the manifest, so nothing can be sent anywhere.
  The tip jar does not change that: it hands a Stripe payment link to the browser and the hub itself
  never opens a connection.
- **No libraries.** No AndroidX, no Compose, no third-party code — the APK is a few hundred KB of
  framework calls, and the only things it keeps are the name of an app you picked by hand (if you
ever picked one) and how long each card's app stayed open.

## Seeing the apps at all (Android 11 and later)

From API 30 the system hides other packages from an app unless the app says, in its manifest, which
ones it wants to see. Without that, `getLaunchIntentForPackage()` and `queryIntentActivities()` do
not fail — they quietly answer with nothing, which is how every card first came up saying *Not
installed* on a phone that had all four apps on it, and how a card that had found its app still
could not start it.

The manifest therefore carries a `<queries>` block that declares the launcher signature
(`MAIN` + `LAUNCHER`), which makes every app with a launcher icon visible — exactly the set the cards
and the *Choose the app…* list are built from — plus the four package names outright. It is the
purpose-built alternative to `QUERY_ALL_PACKAGES`, which asks for far more than this needs.

## Time on each app

Every card shows how long its app was **actually in front of you**, taken from the phone's own usage
log between the moment you left the hub for that app and the moment you came back. The log stops
counting when the screen does or when you move to something else, so a night's sleep, or a detour
through another app, is not charged to the card. A card also says *over how many visits* the time
was earned, and when it was last opened from the hub.

That read needs **Usage access**, which is one switch, not a permission dialog: the gear offers it
until it is on, and it opens Settings → Usage access, where **My Hub** is turned on. Until it is on,
nothing is recorded and nothing fills the gap with a guess.

Why it is built this way: the first version measured the same visit with a clock, from the tap to
the moment the hub came back. That clock kept running while the phone slept and while you used
something else, and a card that was tapped once and returned to the next morning claimed hours. The
version that added the usage log clears those old totals once, on first run, because they cannot be
separated from time that was never spent.

A card only grows that second line once it has time to show, so a fresh hub is just icons and
names; the header does the same, and appears only when a card is still empty or there is time to
report. Press and hold a card for **Reset this card's time**, or use the gear for **Reset all
tracked time**.

## Support

**♥ Support My Hub**, in the gear menu, offers five amounts — $2, $5, $10, $25 and $50. They are the
same five Stripe Payment Links SideCut's Donate tab opens: a fixed amount each, paid on a card page
Stripe hosts, in the browser.

Nothing unlocks, because there is nothing to unlock — the hub is the same app whether a tip is paid
or not. That is also why there is no receipt to check, no account, and no key of any kind in the
APK: a Payment Link is a public URL. With no browser installed the app says so and nothing else
happens.

## Building

Needs JDK 17 and the Android SDK (platform 34, build-tools 34.0.0).

```bash
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

The build is pinned to Gradle 8.9 and Android Gradle Plugin 8.5.2. The repository carries no
Gradle wrapper binary, so CI installs Gradle 8.9 itself — run `gradle wrapper` once if you would
rather use `./gradlew` locally. Android Studio can also just open the folder.

The release build signs itself from the environment, and is left **unsigned** when that environment
is absent, so a plain `assembleDebug` on a machine with no key still works. To sign one:

```bash
export SIGNING_STORE_FILE=/path/to/myhub.p12   # a PKCS12, made with openssl or keytool
export SIGNING_STORE_PASSWORD='…'              # store and key share it
export SIGNING_KEY_ALIAS=myhub
gradle assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

CI does the same thing from two repository secrets — `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD` — and
writes the keystore to the runner's temp folder for the length of the job, so no key material is in
this repository. Both workflows then **fail** if `app-release.apk` is missing: Android Gradle Plugin
does not fail a release build without a key, it quietly writes `app-release-unsigned.apk` instead.

The arithmetic that turns usage-log lines into a card's time lives in `UsageSum.kt` with no Android
dependency, so it has its own check — the overnight, detour and launch-line cases included:

```bash
sh dev/check-usage-sum.sh
# usage-sum: 10 passed, 0 failed
```

## Layout

```
app/src/main/java/com/myhub/app/
    Apps.kt          the apps, how each is found, the remembered choice, and the time kept
    Support.kt       the tip jar: five Stripe Payment Links, and the browser hand-off
    UsageSum.kt      the usage-log arithmetic, kept Android-free so dev/ can check it
    MainActivity.kt  the hub screen, its cards, the picker, and the clock
 dev/               the time arithmetic's own check
app/src/main/res/    colours, theme, settings gear, launcher icon
.github/workflows/   build.yml makes the APKs, release.yml signs one and publishes it
```

## Version

`1.5.0` — `com.myhub.app`, minSdk 21 (Android 5), targetSdk 34 (Android 14).
