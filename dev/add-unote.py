#!/usr/bin/env python3
"""UNote joins My Hub's Other apps list: one row, and the note tells the truth about it.

Run from the repository root: python3 dev/add-unote.py
--check reports without writing, and a second run is a no-op.
"""
import io
import sys

PAGE = 'app/src/main/assets/index.html'
URL = 'https://anekthegreat.github.io/UNote/'
lines = io.open(PAGE, encoding='utf-8').read().split('\n')

if any("label: 'UNote'" in l for l in lines):
    print('UNote is already in the list - nothing to do')
    sys.exit(0)

if '--check' in sys.argv:
    print('UNote is NOT in the list - it needs applying')
    sys.exit(1)

# 1. the row, at the end of OTHERS
start = next(i for i, l in enumerate(lines) if l.startswith('var OTHERS = ['))
end = next(i for i in range(start, len(lines)) if lines[i].strip() == '];')
last = end - 1
if not lines[last].rstrip().endswith('}'):
    sys.exit('the last entry is not where it was expected')
lines[last] = lines[last].rstrip() + ','
lines.insert(end, "  { label: 'UNote', sub: 'Notes that link themselves, behind a PIN',\n    url: '%s' }" % URL)

# 2. the note under the list: five, and UNote is a page rather than a listing
n = next(i for i, l in enumerate(lines) if 'Four more, made the same way' in l)
block = lines[n:n + 4]
if 'Play listing' not in ''.join(block):
    sys.exit('the note is not the shape it was')
lines[n:n + 4] = [
    "    note: 'Five more, made the same way. Four open their Play listing, so what you install is the\\n' +",
    "      'same build Play keeps up to date - nothing here downloads or installs a package.\\n\\n' +",
    "      'UNote is the exception: it is a page, not a listing. Its row opens it in the phone\\'s own\\n' +",
    "      'browser, where it can be added to the home screen. The APK is in its releases.\\n\\n' +",
    "      'A listing that is not up yet answers with Play\\'s own not-found page, so a row opens\\n' +",
    "      'nothing at all until the app it names is published.',",
]

io.open(PAGE, 'w', encoding='utf-8').write('\n'.join(lines))
print('added UNote and rewrote the note under the list')
