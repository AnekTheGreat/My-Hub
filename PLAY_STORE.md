# Putting My Hub on Google Play

Every release already attaches the bundle Play takes: `MyHub-<version>.aab`, signed with the upload
key. This file is the part that cannot be automated — the Console fields, the graphics, and the
declarations Play asks for before it reviews anything.

## 1. The upload

| | |
| --- | --- |
| File | `MyHub-1.9.0.aab` from the [latest release](../../releases/latest) |
| Application id | `com.myhub.app` |
| Version name / code | `1.9.0` / `10900` — the code is computed from the name, so it only ever climbs |
| Min SDK | 21 (Android 5.0) |
| Target SDK | 36 — Play has refused an upload below API 36 since 31 August 2026 |
| Signing | upload key from the `KEYSTORE_BASE64` secret; Play App Signing re-signs what is delivered |

`release.yml` stops a release before publishing unless the bundle is API 36, the manifest asks for
no `REQUEST_INSTALL_PACKAGES`, and the page in `assets/index.html` names the same version as the
build, so a broken bundle or a page that disagrees with it cannot reach the release page.

**The screen updates outside Play, on purpose.** The UI is `assets/index.html`, and a release that
only changes it is fetched and swapped in place by the installed app — the same thing SideCut,
MeasureT and FamTM do. Play ships the shell (the bridge and the update machinery); the page is web
content, not executable code, and it arrives with the release's own notes.

Turn on **Play App Signing**. It is the default for a new app, and it is what makes a lost keystore
recoverable: Google holds the app signing key, this repository holds only the upload key.

**A sideloaded copy cannot be upgraded through Play.** Play signs the delivered APK with its own
key, so a phone carrying `MyHub-1.9.0.apk` from the releases page has to uninstall it once before
installing from the store. Card bindings and tracked time go with that uninstall.

## 2. Store listing

- **App name** (30 characters): `My Hub`
- **Short description** (80): `One dark screen for the apps you open every day. Tap a card and it opens.`
- **Category**: Tools
- **Contact email**: the address on the developer account
- **Privacy policy URL**: required — the hub declares Usage access, so Play will not list it without one

Full description:

```
One screen, your apps, in the order you put them in.

My Hub is a launcher for the handful of apps you actually use: one navy screen of
rounded cards, each with an icon and a name and nothing else. Tap a card and the app
opens. Come back and the list is waiting where you left it.

FOUR CARDS AND TWO SLOTS
The hub ships with cards for the apps it was built alongside — FamTM, SideCut,
MeasureT and UJournal — and two empty slots for anything else. A card looks for its
app by package name and, if that is not there, by the name it is shown under. Press
and hold any card to point it at a different app, or to empty a slot again.

TIME, IF YOU WANT IT
When a card has opened an app, it can also say how long that app was really in front,
read from the phone's own usage log — so it stops counting when the screen does. That
is optional. It needs Usage access, a one-off switch in Settings, and without that
switch the hub records nothing and invents nothing.

NOTHING LEAVES THE PHONE
No account, no sign-in, no analytics, no ads. What is installed, which app a card
points at and how long it was open all stay on the device. The only connection the
hub makes is to its own public GitHub releases, to see whether a newer version exists,
and that request carries nothing about the person using it.

The screen updates itself over the air: a new version of the hub's own page is
fetched and swapped in place, with nothing installed and no installer prompt.
The app itself comes from Google Play when the hub is installed here. Installed
by hand instead, a new build opens its release page in your browser and your
phone's own installer takes the file from there — the hub never installs a
package itself.
```

## 3. Graphics

| Asset | Size | Where |
| --- | --- | --- |
| App icon | 512 × 512 PNG, 32-bit, no alpha | [play/icon-512.png](play/icon-512.png) |
| Feature graphic | 1024 × 500 PNG/JPG | [play/feature-graphic.png](play/feature-graphic.png) |
| Phone screenshots | 2–8, at least 1080 px on the long edge | **still to take**: the hub screen, a filled slot, the update dialog, the gear menu |

The icon is the launcher icon redrawn at store size from the same 108-unit vector the app ships;
the feature graphic is that icon on the app's own navy. Both were rendered from the app's geometry,
not created for the store.

## 4. App content

| Section | Answer |
| --- | --- |
| Ads | No ads |
| Data safety | **No data collected, no data shared.** Nothing is transmitted off the device; the update check asks for a public release and sends nothing |
| Content rating | Everyone — no user content, no ads, no purchases in the app (the tip jar opens Stripe in a browser and unlocks nothing) |
| Target audience | 13+ |
| News app | No |
| In-app purchases | No — the tip jar is a browser link, not a Play billing product, and nothing is unlocked by it |
| Usage access | **Declare it.** The hub reads `PACKAGE_USAGE_STATS` to time the apps on its cards. It is optional inside the app (the gear offers the switch), no data leaves the phone, and declining it leaves every other feature working |

Play will not publish a listing that claims a permission it cannot justify. The honest version is
the one above: optional, local, and only for the app's own cards.
