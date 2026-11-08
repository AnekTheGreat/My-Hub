#!/usr/bin/env python3
"""My Hub 1.10.6 - UNote gets a card of its own.

The hub was built with four cards: FamTM, SideCut, MeasureT and UJournal. UNote was on the Other
apps shelf, and in the release feed, but it was never one of the apps the hub itself carries - so a
phone with UNote installed had no card that opened it, and the screen said four of four.

1.10.6 adds the fifth card. It looks for app.unote, which is the id UNote's APK is signed under,
and falls back to the name the phone shows it by.

--check says whether the tree ALREADY carries the change.

    python3 dev/patch-1106.py
    python3 dev/patch-1106.py --check
"""

import io
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

APPS = "app/src/main/java/com/myhub/app/Apps.kt"
PAGE = "app/src/main/assets/index.html"
GRADLE = "app/build.gradle.kts"
NOTES = "PATCH_NOTES.md"

V = "1.10.6"
OLD = "1.10.5"

ANCHOR = "        // The two slots. A slot is not built for anything: it carries no package names and no"

CARD = """        HubApp(
            id = "unote",
            title = "UNote",
            // UNote is built out of its own repository, and its applicationId has been app.unote
            // from the first commit - the same id its APK is signed under. The name is the fallback,
            // for a phone that carries the APK under an id this card does not know.
            packages = listOf("app.unote"),
            aliases = listOf("UNote")
        ),
"""

PROSE_OLD = "'The four cards the hub was built with look for their apps by package name"
PROSE_NEW = "'The five cards the hub was built with look for their apps by package name"

PROSE2_OLD = "app.ujournal.\\n\\n' +"
PROSE2_NEW = ("app.ujournal; UNote installs from its own repository as "
              "app.unote.\\n\\n' +")

NOTES_ENTRY = """## 1.10.6 - 8 November 2026

UNote is one of the apps the hub carries, not just a row on a list.

- **A fifth card.** The screen was built with four cards - FamTM, SideCut, MeasureT and UJournal -
  and UNote was only ever on the Other apps shelf. A phone with UNote installed had nothing to tap
  that opened it. There is a UNote card now, and the screen counts it.
- **It finds the app the APK is signed under.** The card looks for `app.unote`, and falls back to
  the name the phone shows it by, so a build whose id differs is still found.
- **The explanation matches.** How the hub finds your apps says five cards, and names UNote's
  package beside the others.

"""


def read(rel):
    with io.open(os.path.join(ROOT, rel), encoding="utf-8") as f:
        return f.read()


def write(rel, text):
    with io.open(os.path.join(ROOT, rel), "w", encoding="utf-8", newline="") as f:
        f.write(text)


def main():
    check = "--check" in sys.argv
    apps = read(APPS)
    page = read(PAGE)
    gradle = read(GRADLE)
    notes = read(NOTES)
    touched = []

    if 'id = "unote"' not in apps:
        if ANCHOR not in apps:
            sys.exit("Apps.kt has neither the UNote card nor the place it goes")
        apps = apps.replace(ANCHOR, CARD + ANCHOR, 1)
        touched.append("the UNote card")

    if PROSE_NEW not in page:
        if PROSE_OLD not in page:
            sys.exit("the page says neither four cards nor five")
        page = page.replace(PROSE_OLD, PROSE_NEW, 1)
        touched.append("the line that counts the cards")

    if PROSE2_NEW not in page:
        if PROSE2_OLD not in page:
            sys.exit("the page does not name UJournal's package where it should")
        page = page.replace(PROSE2_OLD, PROSE2_NEW, 1)
        touched.append("UNote's package in the explanation")

    hub_old = 'var HUB_PAGE = "%s";' % OLD
    hub_new = 'var HUB_PAGE = "%s";' % V
    if hub_new not in page:
        if hub_old not in page:
            sys.exit("the page does not name %s" % OLD)
        page = page.replace(hub_old, hub_new, 1)
        touched.append("HUB_PAGE")

    if 'versionName = "%s"' % V not in gradle:
        if 'versionName = "%s"' % OLD not in gradle:
            sys.exit("build.gradle.kts does not name %s" % OLD)
        gradle = gradle.replace('versionName = "%s"' % OLD, 'versionName = "%s"' % V, 1)
        touched.append("versionName")

    if "## %s " % V not in notes:
        notes = NOTES_ENTRY + notes
        touched.append("PATCH_NOTES")

    probes = [
        ('id = "unote"' in apps, "Apps.kt has no UNote card"),
        ('packages = listOf("app.unote")' in apps, "the card does not look for app.unote"),
        ('aliases = listOf("UNote")' in apps, "the card has no name to fall back on"),
        (apps.count("\n        HubApp(") == 7, "the card list is not five apps and two slots"),
        (PROSE_NEW in page, "the page still says four cards"),
        ("app.unote" in page, "the explanation does not name UNote's package"),
        (hub_new in page, "the page does not say %s" % V),
        (hub_old not in page, "the page still says %s" % OLD),
        ('versionName = "%s"' % V in gradle, "build.gradle.kts does not say %s" % V),
        ("## %s " % V in notes, "PATCH_NOTES has no %s entry" % V),
        ('myhub-page' in page, "the page lost its myhub-page marker"),
    ]
    bad = [why for ok, why in probes if not ok]
    if bad:
        sys.exit("; ".join(bad))

    if check:
        if touched:
            sys.exit("the tree does not carry %s yet (%s)" % (V, ", ".join(touched)))
        print("all %d checks pass - the tree carries My Hub %s" % (len(probes), V))
        return

    write(APPS, apps)
    write(PAGE, page)
    write(GRADLE, gradle)
    write(NOTES, notes)
    print("My Hub %s written: Apps.kt, the page, build.gradle.kts and PATCH_NOTES.md" % V)


if __name__ == "__main__":
    main()
