#!/usr/bin/env python3
"""My Hub 1.10.5 - the same five apps, the same names, on every shelf.

Every app in the family carries an Other apps list, and until now no two of them agreed. This one
said "Compass EDC" for an app that is called MeasureT everywhere else, and it sent four of its five
rows to Play listings that do not exist yet - four of the five packages answer with Play's own
not-found page, and only SideCut's is live.

1.10.5 makes this list the list the whole family carries: FamTM, SideCut, MeasureT, UJournal and
UNote, each with one line, each at an address that answers today.

--check says whether the tree ALREADY carries the change, which is what the apply workflow asks it
before deciding there is nothing to do.

    python3 dev/patch-1105.py            # apply
    python3 dev/patch-1105.py --check    # verify only, writes nothing
"""

import io
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

PAGE = "app/src/main/assets/index.html"
GRADLE = "app/build.gradle.kts"
NOTES = "PATCH_NOTES.md"

V = "1.10.5"
OLD = "1.10.4"

OTHERS_NEW = r"""var OTHERS = [
  { label: 'FamTM', sub: 'The family tree in your pocket',
    url: 'https://github.com/AnekTheGreat/FamTM/releases/latest' },
  { label: 'SideCut', sub: 'A music player with its own equaliser',
    url: 'https://play.google.com/store/apps/details?id=com.SideCut.myapp' },
  { label: 'MeasureT', sub: 'Elevation, location, a level and a converter',
    url: 'https://anekthegreat.github.io/Compass-EDC/' },
  { label: 'UJournal', sub: 'A journal for the days you write down',
    url: 'https://github.com/AnekTheGreat/Journal/releases/latest' },
  { label: 'UNote', sub: 'Notes that link themselves, behind a PIN',
    url: 'https://anekthegreat.github.io/UNote/' }
];"""

NOTE_NEW = r"""note: 'The five apps beside this one, in the same order every app in the family lists them.\n\n' +
      'SideCut opens its Play listing. FamTM, MeasureT, UJournal and UNote open the page or the\n' +
      'release they install from, in the phone\'s own browser: one tap, and never a package that\n' +
      'installs itself.',
    """

NOTES_ENTRY = """## 1.10.5 - 8 November 2026

The other apps list every other app, with the names everyone else uses.

- **MeasureT, not Compass EDC.** This list called it Compass EDC while the app itself, its Play
  listing and every other shelf in the family call it MeasureT. It says MeasureT now.
- **Five rows, all five apps.** FamTM, SideCut, MeasureT, UJournal and UNote - the same five, in the
  same order, that the other apps list.
- **Rows that answer.** Four of the five store pages this list pointed at do not exist yet, so those
  rows opened Play's not-found page. FamTM and UJournal now open the release they install from,
  MeasureT and UNote open their own page, and SideCut keeps its live listing.

"""


def read(rel):
    with io.open(os.path.join(ROOT, rel), encoding="utf-8") as f:
        return f.read()


def write(rel, text):
    with io.open(os.path.join(ROOT, rel), "w", encoding="utf-8", newline="") as f:
        f.write(text)


def main():
    check = "--check" in sys.argv
    page = read(PAGE)
    gradle = read(GRADLE)
    notes = read(NOTES)
    touched = []

    if OTHERS_NEW not in page:
        if "var OTHERS = [" not in page:
            sys.exit("the page has no OTHERS list")
        i = page.index("var OTHERS = [")
        j = page.index("];", i) + 2
        page = page[:i] + OTHERS_NEW + page[j:]
        touched.append("the other apps list")

    if NOTE_NEW not in page:
        try:
            i = page.index("note: 'Five more")
        except ValueError:
            sys.exit("the sheet note is neither the old one nor the new one")
        j = page.index("buttons: [{ label: 'Close', run: closeSheet }]", i)
        page = page[:i] + NOTE_NEW + page[j:]
        touched.append("the note under the list")

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
        (OTHERS_NEW in page, "the five apps are not in the page"),
        (page.count("https://play.google.com/store/apps/details?id=") == 1,
         "a row still points at a Play listing that is not there"),
        ("Compass EDC" not in page, "the page still calls MeasureT Compass EDC"),
        ("MeasureT" in page, "MeasureT is not named"),
        ("github.com/AnekTheGreat/FamTM/releases/latest" in page, "FamTM has no address"),
        ("github.com/AnekTheGreat/Journal/releases/latest" in page, "UJournal has no address"),
        ("anekthegreat.github.io/Compass-EDC/" in page, "MeasureT has no address"),
        ("anekthegreat.github.io/UNote/" in page, "UNote has no address"),
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
        print("all %d checks pass - the tree carries %s" % (len(probes), V))
        return

    write(PAGE, page)
    write(GRADLE, gradle)
    write(NOTES, notes)
    print("My Hub %s written to the page, build.gradle.kts and PATCH_NOTES.md" % V)
    print("next: the apply workflow commits it, then release.yml for %s" % V)


if __name__ == "__main__":
    main()
