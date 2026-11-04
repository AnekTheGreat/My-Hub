# My Hub

One dark screen that launches your apps from one place, modelled on the **OnePlus Game Space /
Game Hub**: the same navy field, the same list of rounded cards, an icon and a name on the left. Only
yours are your apps instead of games — and the last two cards are slots you fill yourself.

| Card | What it opens |
| --- | --- |
| **FamTM** | the FamTM APK |
| **SideCut** | the SideCut **release** build — the Play build, `com.SideCut.myapp` |
| **MeasureT** | the MeasureT APK |
| **UJournal** | the UJournal APK |
| **Slot 1**, **Slot 2** | whatever app you put in them |

Tapping a card opens that app. Coming back to the hub puts you back at the top of the list, ready
to open the next one.

## The two slots

The last two cards are empty on purpose. A slot is not built for anything: tap it, pick any app on
the phone, and from then on it wears that app's icon and name and opens it like any other card.
Hold a filled slot for **Change the app…** or **Empty this slot**, which puts it back to empty
without touching the time it earned. Nothing about a slot is baked into the APK, so nothing about it
can go stale.

A slot is also how you reach a *second copy* of an app. SideCut's sideloaded **full** build
(`com.SideCut.myapp.full`) is a different app on the phone from the release build above, and one
card cannot open both — put the full build in a slot, or hold the SideCut card and choose it by
hand.

## Install

There is no APK in this repository. GitHub builds one for you:

1. Install it once from the [**Releases**](../../releases) page — take the newest
`MyHub-<version>.apk`. That is the signed build:
[release.yml](../../actions/workflows/release.yml) makes it on every `v*` tag, or when it is
dispatched by hand with a version. The same release carries `MyHub-<version>.aab` for Play Console.
2. After that, the hub says when there is a newer release: it checks when it opens, and when one is
newer it shows what changed. **Update now** takes the release's new *screen* and swaps it in place —
nothing is installed, and no installer appears, because the screen is a page and not a package. See
[Updates](#updates). Only a release that changed the native side asks for the APK, which opens its
release page in your browser and installs from there. **Check for updates** in the gear asks on
demand.

**Installed from Google Play?** Then Play installs the builds and the screen still updates itself
from here, exactly as it does for a sideloaded copy — the hub never points a store install at an
APK, and **Check for updates** opens the Play listing when a new build is waiting. Put it on Play
with [PLAY_STORE.md](PLAY_STORE.md).
3. Prefer a build from a branch? Open the [**Actions**](../../actions/workflows/build.yml) tab, pick
the newest **Build APK + AAB** run (every push to `main` starts one), and download the
**MyHub-apk-aab** artifact — it carries `app-debug.apk`, the signed `app-release.apk`, and both
bundles (`app-debug.aab`, `app-release.aab`).

Install from Releases rather than an artifact if you want the release checks to line up: the updater
compares the version it is running with the newest release, and a debug artifact carries a lower
version.

Or build it yourself — see [Building](#building).

## How a card finds its app

The hub ships knowing which app each card is meant to be, and asks your phone for it:

| Card | Package it looks for | If that is not installed |
| --- | --- | --- |
| FamTM | `com.familytree.app` | looks for an app named FamTM or Family Tree |
| SideCut | `com.SideCut.myapp` (the release build) | looks for an app named "SideCut Play" or "SideCut" |
| MeasureT | `com.CompassEDC.myapp` | looks for an app named "MeasureT", "Measure T", "Measure", "Compass EDC" or "CompassEDC" |
| UJournal | `app.ujournal` | looks for an app named "UJournal" or "Journal" |

SideCut is published twice from one pipeline, and the two builds are made to be installed side by
side: the Play build is `com.SideCut.myapp` and named *SideCut Play*, and the sideloaded full build
is `com.SideCut.myapp.full` and named *SideCut Full*. The SideCut card is the **release** build,
the one the pipeline signs as `SideCut-<version>-release.apk`; the full build has no card of its own
so that neither build can hide the other, and a slot takes it.

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
slot** — and use the gear in the top right for **Refresh**, **Check for updates**, **♥ Support My
Hub** and a short explanation of the whole thing.

## Updates

**The screen is a web page, so it updates over the air** — the way SideCut, MeasureT and FamTM
update theirs, and without an installer ever appearing:

- On launch the hub reads `updates.json` from the newest release and compares it with the page it is
  drawing. Only when the release is newer does anything appear on screen.
- **Update now** fetches that release's `index.html`, checks it carries the `myhub-page` marker and
  the version the release promised, writes it into the app's own storage and loads it in place. No
  APK is downloaded, no installer prompt opens, and Play Protect sees nothing because nothing is
  being installed.
- **The APK is the shell.** It changes only when the native side does — the bridge that lists apps,
  launches one, reads usage time, and holds the tip links. When that happens the update sheet says
  so and offers the release page; only a copy that was not installed by Play is offered it, because
  a Play copy takes its build from Play.
- **From Google Play**, the build comes from Play and the screen still comes from here: the page
  update is offered exactly as it is to a sideloaded copy.
- **Later** changes nothing. So do being offline, being current, and any failed request: a silent
  check never puts anything on screen unless there is an update to offer.
- **A bad page cannot brick the screen.** A download without the marker or without the promised
  version is refused; a page that loads but never reports that it drew is thrown away and the
  bundled copy is loaded; an install deletes any page stored before it. The worst an update can do
  is put back the screen the APK shipped with.
- Nothing else about the hub touches the network. There is no account, no identifier and nothing
  uploaded; the one request is for a public release.

## What it does not do

- **No runtime permissions.** Asking your phone what is installed and starting another app are both
  allowed without one. The hub does declare `<queries>` in the manifest, which is not a permission
  but the API 30+ way of saying which apps it is allowed to *see* — and it asks for **Usage access**,
  a one-off switch in Settings rather than a prompt, so it can read how long an app was in front.
  With the switch off, no time is recorded and nothing is guessed — see below.
- **One connection, and only for updates.** `INTERNET` is the only permission in the manifest, and
  it exists so the hub can ask for the newest release and, when there is one, fetch its page — see
  [Updates](#updates). It sends nothing about you anywhere, and the tip jar does not change that
  either: it hands a Stripe payment link to the browser and the hub itself never opens a connection
  for it. There is deliberately **no `REQUEST_INSTALL_PACKAGES`**: the hub installs nothing.
- **No libraries.** No AndroidX, no Compose, no third-party code — the screen is a page drawn by the
  WebView the framework already has, and the bridge, the updater and the JSON are all framework
  classes. The only things the hub keeps are the name of an app you picked by hand (if you ever
  picked one), how long each card's app stayed open, and a newer copy of the page once it has
  fetched one.

## Seeing the apps at all (Android 11 and later)

From API 30 the system hides other packages from an app unless the app says, in its manifest, which
ones it wants to see. Without that, `getLaunchIntentForPackage()` and `queryIntentActivities()` do
not fail — they quietly answer with nothing, which is how every card first came up saying *Not
installed* on a phone that had all four apps on it, and how a card that had found its app still
could not start it.

The manifest therefore carries a `<queries>` block that declares the launcher signature
(`MAIN` + `LAUNCHER`), which makes every app with a launcher icon visible — exactly the set the cards
and the *Choose the app…* list are built from — plus the package names outright, both SideCut ids
included. It is the purpose-built alternative to `QUERY_ALL_PACKAGES`, which asks for far more than
this needs.

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

Needs JDK 17 and the Android SDK (platform 36, build-tools 36.0.0).

```bash
gradle assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

The build is pinned to Gradle 8.11.1 and Android Gradle Plugin 8.10.1, the oldest plugin that can
compile against API 36. The repository carries no Gradle wrapper binary, so CI installs Gradle
8.11.1 itself — run `gradle wrapper` once if you would rather use `./gradlew` locally. Android
Studio can also just open the folder.

The release build signs itself from the environment, and is left **unsigned** when that environment
is absent, so a plain `assembleDebug` on a machine with no key still works. The key itself is held in
the repository's Actions secrets and rebuilt on the runner for the length of the job, so no key
material is in this repository.

`assemble*` makes an APK, `bundle*` makes an AAB — the bundle is what Play Console takes, the APK
is what a phone installs by hand:

```bash
gradle assembleDebug assembleRelease bundleDebug bundleRelease
# app/build/outputs/{apk,bundle}/{debug,release}/
```

Both workflows then **fail** if `app-release.apk` is missing (Android Gradle Plugin does not fail a
release build without a key, it quietly writes `app-release-unsigned.apk` instead) and if
`app-release.aab` or `app-debug.aab` is missing. They also fail if the page is missing from the
APK, or if it names a version the build does not.

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
    Updater.kt       the release check, the page download, and the fallbacks behind it
    PageStore.kt     the bundled page, the over-the-air copy, and the rules that make it safe
    Bridge.kt        what the page may ask the phone: apps, launching, usage, tips, updates
    UsageSum.kt      the usage-log arithmetic, kept Android-free so dev/ can check it
    MainActivity.kt  the shell: one WebView, the bars, and the watch for a page that drew
app/src/main/assets/
    index.html       the screen itself - cards, sheets, gear - and the file an update replaces
dev/                 the time arithmetic's own check
app/src/main/res/    colours, theme, settings gear, launcher icon
.github/workflows/   build.yml makes the APKs and AABs, release.yml signs, checks and publishes them
```

## Version

`1.9.0` — `com.myhub.app`, minSdk 21 (Android 5), targetSdk 36 (Android 16).

## Licence

My Hub is proprietary. Copyright (c) 2026 AnekTheGreat, all rights reserved — no part of it may be
copied, reused, modified or redistributed without written permission. See [LICENSE](LICENSE).
