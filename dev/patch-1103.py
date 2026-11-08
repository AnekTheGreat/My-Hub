#!/usr/bin/env python3
"""1.10.3: UNote joins the apps the hub opens, and the hub says so.

UNote is a page rather than a Play listing, so its card is a plain link the hub
opens in the phone's browser - no install, nothing to update. The screen's
version and the build's move together, which is what release.yml checks, so an
installed hub is offered this page as a page update and not as a new install.
"""
import io
import sys

V = '1.10.3'
PREV = '1.10.2'


def read(p):
    return io.open(p, encoding='utf-8').read()


def write(p, s):
    io.open(p, 'w', encoding='utf-8').write(s)


def applied():
    try:
        return 'versionName = "%s"' % V in read('app/build.gradle.kts')
    except IOError:
        return False


if applied():
    print('%s is already in the tree - nothing to do' % V)
    sys.exit(0)
if '--check' in sys.argv:
    print('%s is not in the tree yet' % V)
    sys.exit(1)

gradle = read('app/build.gradle.kts')
old = 'versionName = "%s"' % PREV
if gradle.count(old) != 1:
    sys.exit('build.gradle.kts is not on %s' % PREV)
write('app/build.gradle.kts', gradle.replace(old, 'versionName = "%s"' % V, 1))

page = read('app/src/main/assets/index.html')
old = 'var HUB_PAGE = "%s";' % PREV
if page.count(old) != 1:
    sys.exit('the screen is not on page %s' % PREV)
write('app/src/main/assets/index.html', page.replace(old, 'var HUB_PAGE = "%s";' % V, 1))

notes = read('PATCH_NOTES.md')
anchor = '## %s - ' % PREV
if notes.count(anchor) != 1:
    sys.exit('PATCH_NOTES.md has no %s section' % PREV)
section = (
    '## %s - UNote joins the list, and opens from it\n'
    '\n'
    '### New\n'
    '- **UNote is in the hub.** It sits at the bottom of the apps the hub lists, reading "Notes that\n'
    '  link themselves, behind a PIN", and tapping it opens it. UNote is a page rather than a Play\n'
    '  listing, so it opens in the phone\'s browser instead of behind an install button - there is\n'
    '  nothing to download and nothing to update.\n'
    '\n' % V)
write('PATCH_NOTES.md', notes.replace(anchor, section + anchor, 1))
print('%s written: the screen, the build and the notes' % V)
