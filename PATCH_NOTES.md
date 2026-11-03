# Patch notes

What changed in each release, newest first. Everything here ships as a signed APK for a phone and
the same build as an AAB for Play Console, made by
[release.yml](.github/workflows/release.yml) on a `v*` tag.

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
- Both workflows now fail if `app-release.aab` is missing, for the same reason they already failed
  on a missing `app-release.apk`: Android Gradle Plugin does not fail a build that produced nothing
  usable, it writes an `-unsigned` file and carries on.

## 1.5.0 — a signed release, and a way to say thank you

### Added
- **Real release builds.** The hub only ever produced a debug APK because it had no key. It now
  signs from `SIGNING_STORE_FILE` + `KEYSTORE_PASSWORD` (reconstructed from the `KEYSTORE_BASE64`
  secret in CI) and publishes on a `v*` tag. No key material is in the repository.
- **♥ Support My Hub**, in the gear menu: five amounts ($2, $5, $10, $25, $50) paid through the
  same Stripe Payment Links SideCut's Donate tab opens. The hub hands the link to the browser and
  still has no internet permission; nothing unlocks, because there is nothing to unlock.

## 1.4.0 — the hub the apps actually use

### Changed
- The card list was rebuilt around the apps it is for: **FamTM → MeasureT → UJournal**, then the two
  slots the phone fills itself. A card that would not open anything is worse than no card.
- Section headings (**YOUR APPS** / **YOUR SLOTS**), a gradient field behind the cards, and elevation
  so a card reads as something to press.
- Card subtitles stay quiet: the app's package name and a launch button are gone, and a card shows
  only its name, its icon, and at most one line of time.

### Fixed
- UJournal is found as `app.ujournal`, the id it actually installs under.
