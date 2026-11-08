package com.myhub.app

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowInsets
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.util.Log
import android.net.Uri

// The palette lives with the page now, and these two are only what shows while it loads and the
// strips behind the system bars: the same navy of OnePlus Game Space.
private val BG = Color.parseColor("#0C1A2B")
private val BG_TOP = Color.parseColor("#10243B")

/**
 * The shell: one WebView, one bridge, and the update that keeps the screen current.
 *
 * The screen itself is `assets/index.html` - the same page every other app here draws - so a
 * wording change, a new card, a different sheet all ship by replacing that file over the air. The
 * APK only changes when this file does: it holds the bridge the page asks about apps, launching,
 * usage time, the tip links and the update check.
 *
 * Three things guard the phone against a bad page, because the one failure that matters is a hub
 * that opens to a blank screen and has no way to ask for a better one:
 *
 *  - [PageStore] refuses a download without the marker or without the promised version.
 *  - A page has to call back `ready()` after drawing. If it does not within [WATCHDOG] ms the
 *    stored copy is deleted and the bundled one is loaded, so the worst an update can do is put the
 *    shipped screen back on the phone.
 *  - A newly installed APK deletes an older stored page before anything is drawn.
 */
class MainActivity : Activity() {

    private lateinit var frame: FrameLayout
    private lateinit var web: WebView
    private lateinit var bridge: Bridge
    private val main = Handler(Looper.getMainLooper())

    /** Set by the page's `ready()`, cleared on every load. */
    private var ready = false

    /** True once the bundled page has been tried, so a broken build cannot reload for ever. */
    private var fellBack = false
    private var watchdog: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Totals written by the clock-based version are cleared once, before a card is drawn.
        Usage.migrate(this)
        // A page stored by an older install is dropped: the APK on the phone is the newer thing.
        PageStore.dropStale(this)

        frame = FrameLayout(this).apply { setBackgroundColor(BG) }
        web = WebView(this).apply {
            setBackgroundColor(BG)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // The page is served from the app's own files, so file access is the point; content
            // (content:// from other apps) is not, and stays off.
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) = watch()

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    // A stored page that will not even load is the same case as one that never
                    // draws: the next start uses the bundled copy.
                    if (request?.isForMainFrame == true) watch()
                }
            }
        }
        bridge = Bridge(this)
        web.addJavascriptInterface(bridge, "Hub")
        frame.addView(
            web,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(frame)
        bars()
        loadPage()
        // One quiet check per launch. It only speaks when something newer exists, so a hub that is
        // current - or offline - opens exactly as it did before updates existed.
        checkUpdates(silent = true)
    }

    /**
     * Coming back to the hub is when an app may have been installed or removed, so the page is
     * asked to draw again. It is also the one moment the visit that just ended can be priced: the
     * hub left because a card opened an app, and this is the return.
     */
    override fun onResume() {
        super.onResume()
        Usage.returned(this)
        eval("window.hubResume&&window.hubResume()")
    }

    // ---- the page ----

    /** Load the stored page when there is one, the bundled page otherwise. */
    fun loadPage() {
        ready = false
        val url = if (PageStore.stored(this)) "file://" + PageStore.path(this)
        else "file:///android_asset/index.html"
        web.loadUrl(url)
    }

    /** Start, or restart, the wait for the page to say it has drawn. */
    private fun watch() {
        ready = false
        watchdog?.let { main.removeCallbacks(it) }
        val check = Runnable { if (!ready) fallback() }
        watchdog = check
        main.postDelayed(check, WATCHDOG)
    }

    /** The page called `ready()`: it is a working screen, and the wait is over. */
    fun pageReady() {
        ready = true
        watchdog?.let { main.removeCallbacks(it) }
    }

    /**
     * The page loaded but never drew - a script error in a downloaded copy, most likely. The stored
     * copy is thrown away and the bundled one is loaded, which is the whole reason an over-the-air
     * page is safe to keep: it can always be taken back.
     */
    private fun fallback() {
        if (fellBack) return
        fellBack = true
        if (PageStore.stored(this)) {
            PageStore.clear(this)
            toast("My Hub went back to the screen it ships with")
            loadPage()
        } else {
            toast("My Hub could not draw its screen")
        }
    }

    /** Run a statement in the page. Safe to call from any thread, and before the page exists. */
    fun eval(js: String) = runOnUiThread {
        if (::web.isInitialized) web.evaluateJavascript(js, null)
    }

    fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    // ---- updates ----

    /**
     * Ask GitHub what is newest and offer it. A page update is the ordinary kind now: the screen is
     * a web page, so it is fetched and swapped in place with nothing installed, the way SideCut's,
     * MeasureT's and FamTM's screens update. The APK is offered only when the shell itself changed,
     * and never to a copy Play delivered.
     */
    fun checkUpdates(silent: Boolean) {
        if (!silent) eval("hubToast('Checking for updates…')")
        Updater.check { update -> runOnUiThread { offered(update, silent) } }
    }

    /**
     * THE TWO CLOCKS, KEPT APART.
     *
     * This compared the RELEASE's version against the running PAGE's version, so any release whose
     * number was ahead of the page - every release that shipped a shell change and republished the
     * same screen, and every release older than the page field - asked for a download the phone had
     * already taken, on every launch. A release is "newer" as an app, and a page is "newer" as a
     * page; they are measured against their own kind now, and only when the manifest actually
     * names the page's version does a screen update get offered.
     *
     * And an offer is remembered. A silent check that has already spoken about a version stays
     * quiet on the next launch instead of interrupting again; Check for updates (silent = false)
     * still answers every time it is pressed, because that is someone asking.
     */
    private fun offered(update: Update?, silent: Boolean) {
        val runningPage = PageStore.running(this)
        val pageTarget = update?.pageVersion?.takeIf { update.page != null }
        if (update != null && pageTarget != null && Updater.newer(pageTarget, runningPage)) {
            // The screen is one file in the app's own storage, and the shell already refuses one
            // that does not name the version it asked for, so this needs no sheet and no
            // permission: fetch it, put it in place, say so. The worst a bad fetch does is leave
            // the old screen running.
            applyPage(update.page!!, pageTarget)
            eval("hubToast(" + JSONObject.quote("Screen updated to " + pageTarget) + ")")
            return
        }
        if (update != null && !Updater.fromPlay(this) &&
            Updater.newer(update.version, BuildConfig.VERSION_NAME)
        ) {
            show(update, "app", update.version, silent)
            return
        }
        if (!silent) {
            eval("hubToast(" + JSONObject.quote("My Hub $runningPage is the newest there is") + ")")
        }
    }

    /** One offer per version per install: a launch cannot repeat what a launch already said. */
    private fun show(update: Update, kind: String, version: String, silent: Boolean) {
        val key = kind + ":" + version
        val prefs = getSharedPreferences("updates", MODE_PRIVATE)
        if (silent && prefs.getString("last_offered", "") == key) return
        prefs.edit().putString("last_offered", key).apply()
        eval("hubUpdate(" + offer(update, kind, version) + ")")
    }

    /** What the update sheet is given: what changed, and where the new page or release comes from. */
    private fun offer(update: Update, kind: String, version: String): String {
        val notes = JSONArray()
        update.notes.lines()
            .map { it.trim().removePrefix("-").trim() }
            .filter { it.isNotEmpty() }
            .take(8)
            .forEach { notes.put(it) }
        return JSONObject()
            .put("kind", kind)
            // For a screen update this is the PAGE's version, which is what the sheet shows and
            // what the fetched file has to name before PageStore will accept it.
            .put("version", version)
            .put("app", update.version)
            .put("notes", notes)
            .put("url", if (kind == "page") update.page ?: "" else apkUrl(update))
            .toString()
    }

    /** The release's own APK, named the way release.yml names it. No page in between. */
    private fun apkUrl(update: Update) =
        "https://github.com/AnekTheGreat/My-Hub/releases/download/v${update.version}/MyHub-${update.version}.apk"

    /**
     * Fetch the page a release published and put it in place. Nothing is installed and no package
     * is touched: this writes one file into the app's own storage and loads it.
     */
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

    fun applyPage(url: String, version: String) {
        Thread {
            val bytes = Updater.download(url)
            val saved = bytes != null && PageStore.save(this, bytes, version)
            runOnUiThread {
                if (saved) {
                    loadPage()
                    main.postDelayed({ eval("hubToast('Updated to $version')") }, 1500)
                } else {
                    eval("hubToast('That update did not arrive in one piece')")
                }
            }
        }.start()
    }

    // ---- the bars ----

    /**
     * From Android 15 the system draws every app edge to edge and there is no opt-out at API 36, so
     * the page is padded by the bars and the keyboard instead of being drawn under them. Below API
     * 30 nothing forces it, and the layout keeps the insets itself.
     */
    private fun bars() {
        window.statusBarColor = BG_TOP
        window.navigationBarColor = BG
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            frame.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.ime()
                )
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
    }

    private companion object {
        /** How long a page gets to draw before the bundled copy is put back. */
        const val WATCHDOG = 9000L
    }
}
