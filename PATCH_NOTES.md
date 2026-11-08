# Patch notes

What changed in each release, newest first. Everything here ships as a signed APK for a phone and
the same build as an AAB for Play Console, made by
[release.yml](.github/workflows/release.yml) on a `v*` tag.

## 1.10.4 - it updates itself, and never sends you to a web page

### Changed
- **No more web page.** The update used to open GitHub in the browser. Now the new build downloads
  straight to your downloads folder and asks to install when it has finished - one tap, in place,
  and nothing to read in a browser first.
- **The screen updates by itself.** A new screen arrives on launch with nothing to press: no sheet,
  no notes to get through, just a quiet "Screen updated" when it is done.
- **The sheet is only about the app now**, and it still says each version once.

## 1.10.3 - UNote joins the list, and opens from it

### New
- **UNote is in the hub.** It sits at the bottom of the apps the hub lists, reading "Notes that
  link themselves, behind a PIN", and tapping it opens it. UNote is a page rather than a Play
  listing, so it opens in the phone's browser instead of behind an install button - there is
  nothing to download and nothing to update.

## 1.10.2 - no more asking for an update you already have

### Fixed
- **The update prompt stops nagging.** The hub was comparing the newest release's number against
  the version of the *screen*, which is a different clock: any release whose number had moved on
  while the screen had not asked again on every launch, and accepting it changed nothing, so it
  asked again next time. A screen update is now measured screen to screen, the release that
  publishes the page says which version the page names, and an offer already made is remembered so
  a launch cannot repeat it. Check for updates still answers every time you press it.

### New
- **A screen with a little more life in it.** It greets you by the time of day and says how many
  apps are ready; the time total counts up instead of appearing; a card springs under your finger
  and slides away as it opens; putting an app in a slot throws a little confetti from the card; and
  the backdrop drifts slowly instead of sitting still. All of it is off when the phone asks for
  reduced motion.

## 1.10.1 - the other apps open their Play listings

### Changed
- **The rows behind the gear open Play, not GitHub.** Each one used to open the app's repository,
  which is where the APKs are kept for sideloading rather than where an app is installed from. The
  four rows now open their Play listings, addressed by the package name each app is published
  under, and the sheet says so. FamTM is published (`com.familytree.app`); UJournal, SideCut and
  Compass EDC answer with Play's own not-found page until they are published.

## 1.10.0 — the gear points at the other apps

### Added
- **Check out the other apps.** A row behind the gear lists UJournal, FamTM, SideCut and Compass EDC,
  one line each, and a tap opens that app's page in the browser. The hub already sends someone to a
  page for a tip, so this is one more row over the same call and needed no native code at all.

### Fixed
- **A sheet row's second line wraps instead of being cut off.** The row subtitle was styled for
  package names — one line, monospace, ellipsis — which is wrong for a sentence describing an app.
  It now wraps and uses the same type as the rest of the sheet.

## 1.9.0 — the screen is a page now, so it updates without an installer

### Changed
- **My Hub draws a page instead of building its screen in Kotlin.** The cards, the picker, the gear
  menu and the update sheet are `assets/index.html`, the same shape SideCut, MeasureT and FamTM
  have always had. What stays native is the handful of things a page cannot do on Android: listing
  the apps in the launcher, starting one, reading how long it was in front, the tip links, and the
  update itself. They are reached through one bridge, which exposes nothing else.
- **A screen update now arrives over the air.** On launch the hub reads `updates.json` from the
  newest release; when it names a newer page, the page is fetched, checked for its marker and its
  version, written into the app's own storage and loaded in place. No APK, no browser, no installer
  prompt, and **Update now** is one tap — which is what an update should have felt like all along.
- **The APK is the shell, and only changes when the shell does.** A release that only touched the
  screen costs nothing to install because there is nothing to install; a release that changed the
  bridge still offers the APK, and only to a copy that was not installed by Play.
- **A copy installed from Google Play keeps the screen updates.** 1.8.5 made a Play copy skip the
  check entirely; it now skips only the APK, so Play handles the build while the screen keeps
  arriving on its own.
- **A bad page cannot leave the hub blank.** Four rules: a download without the marker or without
  the promised version is refused; the page must call `ready()` after drawing, or the stored copy is
  deleted and the bundled one is loaded; a newly installed APK deletes any page stored before it;
  and the file is replaced by a rename, so a half-written download is never served.

### Fixed
- **The status bar and keyboard no longer sit on top of the screen on Android 15 and 16.** Targeting
  API 36 put the app edge to edge with no opt-out, so the page is padded by the system bars and the
  IME instead of being drawn under them.
- **The theme's background is painted behind the WebView**, so the first frame is navy rather than a
  white flash, and the strips behind the system bars match the page.

## 1.8.5 — target Android 16, and a Play copy updates through Play

### Changed
- **The hub now targets Android 16 (API 36).** Google Play has refused an upload that targets less
  since 31 August 2026, so `compileSdk` and `targetSdk` are both 36. That needs a newer Android
  Gradle Plugin — 8.10.1, with Gradle 8.11.1, both pinned in CI — and Kotlin 2.0.21. None of the
  hub's own code changed to make room for it.
- **A copy installed from Google Play takes its updates from Play.** `Updater.fromPlay()` asks the
  phone which app installed this copy; when the answer is the Play Store, the hub skips the GitHub
  check altogether and the gear's **Check for updates** opens the Play listing. A Play install must
  not be pointed at an APK: Play's policy forbids it, and a sideloaded APK carries a different
  signing key anyway, so the "update" could not install over the store build.
- **A sideloaded copy keeps the GitHub check unchanged** — the same `updates.json`, the same notes,
  the same release page in the browser.

### Added
- **A Play-ready step in `release.yml`.** A release now stops before publishing unless `targetSdk`
  is 36, `compileSdk` is 36, and the manifest asks for no `REQUEST_INSTALL_PACKAGES`.
- **`PLAY_STORE.md`** — what to upload, the listing copy, the graphics still to supply by hand, and
  the store declarations the hub needs.

### Fixed
- **The header keeps its distance from the status bar on Android 15 and 16.** Those versions force
  edge-to-edge on an app that targets 35 or higher; the root view already takes the system insets,
  so the screen sits where it did before.

## 1.8.4 — the update no longer installs itself, so Play Protect stops calling it harmful

### Fixed
- **Play Protect read the hub as harmful, because the hub was behaving the way a dropper does.**
  `Updater` downloaded the release APK into its own cache and handed it to Android's installer, and
  that hand-off is what `REQUEST_INSTALL_PACKAGES` exists for: a sideloaded app that fetches code
  over the network and then asks to install it is exactly the shape Play Protect is built to block,
  whatever it means to do. The permission, the download and the hand-off are all gone. Tapping an
  offered update now opens the release page in the browser, the APK downloads there, and the
  phone's own installer installs it from outside the hub.
- `ApkProvider` is deleted. It existed only to serve that downloaded APK to the installer, and with
  nothing to serve it was a `ContentProvider` and a permission that only looked suspicious.

### Changed
- The check itself is unchanged: it still reads the manifest published with the release, compares
  the version against the build on the phone, and shows the notes before anything is downloaded.
- The gear's update dialog now says what actually happens — the release opens in the browser —
  instead of promising an install the app no longer performs.

## 1.8.3 — an update that says what changed

### Fixed
- 1.8.2 shipped a manifest with no notes in it at all. The extractor treated the `### Fixed`
  subheading as the end of the entry, so it never reached a bullet. Subheadings are now skipped,
  and a wrapped line is joined back onto the bullet it belongs to, so the notes in the manifest are
  the whole sentences from this file — five of them for this release.
- A release now **fails** if it finds no note for its version, instead of publishing an update that
  announces nothing. Patch notes are part of a release here, so a missing one stops the run.

## 1.8.2 — the notes that come with the update are whole sentences

### Fixed
- A note in this file wraps over several lines, and the manifest took only the first of them, so an
  update would have announced itself with half a sentence. The continuation lines are joined back
  onto the bullet they belong to, and the markdown emphasis is dropped.

## 1.8.1 — the release now says what it is

### Changed
- **The update comes from a manifest published with the release**, the way every other app here
  does it — SideCut's `ota/updates.json`, MeasureT's `updates.json`, FamTM's published page. The
  release job writes `updates.json` beside the APK and attaches it, carrying the version, the file
  name, its size and the notes for it taken from this file, and the hub reads that one fixed URL
  (`releases/latest/download/updates.json`) instead of parsing an API response and a release tag.
  The changelog therefore travels with the build rather than being reconstructed on the phone, and
  an update check no longer depends on an API that rate-limits by address.
- The API and the releases feed stay behind that as fallbacks, so a release that predates the
  manifest is still found.

### Fixed
- `versionName` was read by the release job to check the tag; the check now also means the manifest
  cannot advertise one version while the APK carries another.

## 1.8.0 — the update that never arrived

### Fixed
- **A check could come back empty and read as "you are current".** The hub asked `api.github.com`
  for the newest release without a `User-Agent`, which GitHub refuses with 403, and the anonymous
  allowance is 60 requests an hour **per address** — a phone on a carrier shares its address with
  everything behind that gateway, so the limit is reached in normal use. API and feed requests now
  name the app and its version.
- **A download is now checked before the installer sees it.** GitHub can answer with an error page
  that saves perfectly happily as `myhub-update.apk` and reaches Android as "there was a problem
  parsing the package". The file has to start with the two bytes every APK starts with, and be big
  enough to be one; otherwise it is deleted and the hub says the download failed.
- **The debug APK was as eligible as the release one.** The check took whichever `.apk` came last in
  the release, so a debug asset would have installed over a release build. The release APK is
  chosen explicitly and a debug asset is only a fallback.
- The version comparison, the timeouts and the reason for a failure are all on the log
  (`MyHubUpdater`) instead of vanishing into a null.

### Changed
- `versionCode` is computed from `versionName` — 1.8.4 is 10804 — instead of being typed beside it.
  A release therefore always installs over the build it replaces, and the two numbers cannot drift
  apart.

## 1.7.0 — the hub can update itself

### Added
- **Over-the-air updates.** On launch the hub asks GitHub for the newest release of this repository,
  and — only when that release is newer than the build on the phone — offers what changed, downloads
  the APK and hands it to Android's installer. The gear gains **Check for updates** for the manual
  case.
- The check is silent: current, offline, or no answer all look exactly like before. Nothing is
  downloaded unless there is a newer release.

### Changed
- The manifest now asked for **INTERNET** and **REQUEST_INSTALL_PACKAGES**. The install permission
  is gone as of 1.8.4, for the reason given there.
- The updater is written on the framework, not on a library: a thread and `HttpURLConnection` for
  the check, `org.json` for the release, and — until 1.8.4 — `ApkProvider`, a one-file
  `ContentProvider`, for the hand-off to the installer. The hub still has no dependencies.

## 1.6.0 — SideCut is a card again, and every build has an AAB

### Added
- **The SideCut card is back.** The hub lost it when the list was rebuilt around the apps the hub
  was made for, which left SideCut reachable only by pointing a slot at it by hand. It sits after
  FamTM again, looking for `com.SideCut.myapp` (the release build) and then `com.SideCut.play`, and
  falls back to an app named *SideCut Play* or *SideCut*. A phone carrying the sideloaded full build
  instead can still be reached through a slot, or by holding the card and choosing that app.

### Changed
- Every run now publishes **four** files: `app-debug.apk`, `app-release.apk`, `app-debug.aab` and
  `app-release.aab`. Before this, the hub only ever handed out APKs.
- A release attaches the signed APK **and** its AAB, so Play Console gets the same build a phone
  installs by hand.
