# Patch notes

What changed in each release, newest first. Everything here ships as a signed APK for a phone and
the same build as an AAB for Play Console, made by
[release.yml](.github/workflows/release.yml) on a `v*` tag.

## 1.8.0 — the update that never arrived

### Fixed
- **A check could come back empty and read as "you are current".** The hub asked `api.github.com`
  for the newest release without a `User-Agent`, which GitHub refuses with 403, and the anonymous
  allowance is 60 requests an hour **per address** — a phone on a carrier shares its address with
  everything behind that gateway, so the limit is reached in normal use. Every request now names
  the app and its version, and when the API still says no, the newest tag is read from the
  repository's releases feed, which has no limit and needs no credentials. The APK URL is built
  from that tag, because `release.yml` names the file after the version.
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
- `versionCode` is computed from `versionName` — 1.8.0 is 10800 — instead of being typed beside it.
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
- The manifest now asks for **INTERNET** and **REQUEST_INSTALL_PACKAGES**. That is the hub's only
  connection and it exists for this: one GET for a public release, then the APK itself. There is no
  account, no identifier, and nothing uploaded — the README previously said the hub never touches
  the network, and now says what the one request is for.
- The updater is written on the framework, not on a library: a thread and `HttpURLConnection` for
  the check and the download, `org.json` for the release, and `ApkProvider` — a one-file
  `ContentProvider` — for the hand-off to the installer, because `file://` URIs have been refused
  since Android 7. The hub still has no dependencies.

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
