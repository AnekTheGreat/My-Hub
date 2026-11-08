#!/usr/bin/env python3
"""My Hub 1.10.4 - it updates itself, and stops sending anyone to a web page.

    python3 dev/patch-1104.py           apply
    python3 dev/patch-1104.py --check   exit 1 if it is not in place

Two halves. The screen already arrives as one file the shell can swap, so it now
does that on launch with nothing to press. The app is a package, and this app is
not allowed to hand one to Android's installer - Play reads a sideloaded app that
downloads code and then asks to install it as a dropper, which is why 1.8.4 moved
that tap into a browser. So the tap now downloads the build into the phone's own
downloads folder, where the finished file asks to install: one tap, no page.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OLD = '1.10.3'
NEW = '1.10.4'
PAGE = 'app/src/main/assets/index.html'
MAIN = 'app/src/main/java/com/myhub/app/MainActivity.kt'
BRIDGE = 'app/src/main/java/com/myhub/app/Bridge.kt'
GRADLE = 'app/build.gradle.kts'
NOTES = 'PATCH_NOTES.md'

TAIL_NEW = (": 'The new build downloads here, into your downloads folder, and asks to install "
            "when it has finished. Nothing opens in a browser.';")

NOTES_BLOCK = '''## NEWVER - it updates itself, and never sends you to a web page

### Changed
- **No more web page.** The update used to open GitHub in the browser. Now the new build downloads
  straight to your downloads folder and asks to install when it has finished - one tap, in place,
  and nothing to read in a browser first.
- **The screen updates by itself.** A new screen arrives on launch with nothing to press: no sheet,
  no notes to get through, just a quiet "Screen updated" when it is done.
- **The sheet is only about the app now**, and it still says each version once.

'''.replace('NEWVER', NEW)

OFFER_PAGE_OLD = '''        if (update != null && pageTarget != null && Updater.newer(pageTarget, runningPage)) {
            show(update, "page", pageTarget, silent)
            return
        }'''
OFFER_PAGE_NEW = '''        if (update != null && pageTarget != null && Updater.newer(pageTarget, runningPage)) {
            // The screen is one file in the app's own storage, and the shell already refuses one
            // that does not name the version it asked for, so this needs no sheet and no
            // permission: fetch it, put it in place, say so. The worst a bad fetch does is leave
            // the old screen running.
            applyPage(update.page!!, pageTarget)
            eval("hubToast(" + JSONObject.quote("Screen updated to " + pageTarget) + ")")
            return
        }'''

URL_OLD = '''    /** The release page, which carries the APK and the notes beside it. */
    private fun releasePage(update: Update) =
        "https://github.com/AnekTheGreat/My-Hub/releases/tag/v${update.version}"'''
URL_NEW = '''    /** The release's own APK, named the way release.yml names it. No page in between. */
    private fun apkUrl(update: Update) =
        "https://github.com/AnekTheGreat/My-Hub/releases/download/v${update.version}/MyHub-${update.version}.apk"'''

DOWNLOAD_BUILD = '''
    /**
     * The app itself, fetched into the phone's downloads folder and left there.
     *
     * The hub still does not install a package: Play reads a sideloaded app that downloads code and
     * then asks to install it as a dropper, whatever it means to do, and 1.8.4 removed the permission
     * for exactly that. What is gone with 1.10.4 is the browser: DownloadManager takes the file to
     * the phone's own downloads, the finished file says so in the notification bar, and the phone's
     * installer takes it from there. One tap, the release's own APK, no page in between.
     */
    fun downloadBuild(url: String, version: String) {
        Thread {
            val started = try {
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle("My Hub " + version)
                    .setDescription("Tap when it has finished to install")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS, "MyHub-" + version + ".apk"
                    )
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                true
            } catch (e: Exception) {
                Log.w("MyHub", "the download could not start", e)
                false
            }
            runOnUiThread {
                toast(
                    if (started) "Downloading My Hub " + version + " - the finished file asks to install"
                    else "That download could not start"
                )
            }
        }.start()
    }
'''

BRIDGE_ADD = '''
    /** The app's own update: the release's APK into the phone's downloads, never a browser. */
    @JavascriptInterface
    fun downloadBuild(url: String, version: String) {
        if (!url.startsWith("https://")) return
        activity.downloadBuild(url, version)
    }
'''


def read(rel):
    return io.open(os.path.join(ROOT, rel), encoding='utf-8').read()


def write(rel, text):
    io.open(os.path.join(ROOT, rel), 'w', encoding='utf-8', newline='').write(text)


def add_imports(src):
    need = ['import android.app.DownloadManager', 'import android.content.Context',
            'import android.os.Environment', 'import android.util.Log']
    missing = [n for n in need if n not in src]
    if not missing:
        return src
    lines = src.split('\n')
    last = max(i for i, l in enumerate(lines) if l.startswith('import '))
    return '\n'.join(lines[:last + 1] + missing + lines[last + 1:])


def page_after(page):
    page = re.sub(r'(var HUB_PAGE = ")[^"]+(")', r'\g<1>' + NEW + r'\g<2>', page, count=1)
    page, n = re.subn(r": 'The release opens in your browser.*?itself\.';", TAIL_NEW, page,
                      count=1, flags=re.S)
    if n == 0 and "The new build downloads here" not in page:
        raise SystemExit('the browser sentence is not where it was')
    page = page.replace("label: isPage ? 'Update now' : 'Open the release',",
                        "label: isPage ? 'Update now' : 'Download it',", 1)
    page = page.replace("else native.openUrl(u.url);",
                        "else { toast('Downloading...'); native.downloadBuild(u.url, u.version); }", 1)
    return page


def main_after(src):
    if OFFER_PAGE_OLD in src:
        src = src.replace(OFFER_PAGE_OLD, OFFER_PAGE_NEW, 1)
    elif 'applyPage(update.page!!' not in src:
        raise SystemExit('the page offer is not where it was')
    if URL_OLD in src:
        src = src.replace(URL_OLD, URL_NEW, 1)
    # The one place that called it is the sheet's address; swap that first, then insist nothing is
    # left pointing at a release page.
    src = src.replace('releasePage(update)', 'apkUrl(update)')
    if 'releasePage' in src:
        raise SystemExit('something still points at the release page')
    if 'fun downloadBuild(' not in src:
        anchor = '\n    fun applyPage(url: String, version: String) {'
        if anchor not in src:
            raise SystemExit('applyPage is not where it was')
        src = src.replace(anchor, DOWNLOAD_BUILD + anchor, 1)
    # Whatever the tree looks like, the four imports the download needs are there before the file is
    # written - a missing one is a compile error the release run would only find after an APK build.
    return add_imports(src)


def bridge_after(src):
    if 'fun downloadBuild(' in src:
        return src
    anchor = '''    @JavascriptInterface
    fun updateNow(url: String, version: String) {
        if (!url.startsWith("https://")) return
        activity.applyPage(url, version)
    }
'''
    if anchor not in src:
        raise SystemExit('updateNow is not where it was')
    return src.replace(anchor, anchor + BRIDGE_ADD, 1)


def changes():
    yield PAGE, page_after(read(PAGE))
    yield MAIN, main_after(read(MAIN))
    yield BRIDGE, bridge_after(read(BRIDGE))
    yield GRADLE, re.sub(r'(versionName = ")[^"]+(")', r'\g<1>' + NEW + r'\g<2>', read(GRADLE), count=1)
    notes = read(NOTES)
    if ('## ' + NEW) not in notes:
        if ('## ' + OLD) not in notes:
            raise SystemExit('PATCH_NOTES has no 1.10.3 heading to sit above')
        notes = notes.replace('## ' + OLD, NOTES_BLOCK + '## ' + OLD, 1)
    yield NOTES, notes


def main():
    want = list(changes())
    if '--check' in sys.argv:
        bad = [rel for rel, text in want if read(rel).replace('\r\n', '\n') != text.replace('\r\n', '\n')]
        if not bad:
            print('%s is in place' % NEW)
            return 0
        print('not applied yet: ' + ', '.join(bad))
        return 1
    for rel, text in want:
        if read(rel) != text:
            write(rel, text)
            print('wrote ' + rel)
    print('%s applied' % NEW)
    return 0


if __name__ == '__main__':
    sys.exit(main())
