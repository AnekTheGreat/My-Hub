package com.myhub.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * What the page is allowed to ask the phone for.
 *
 * The screen is HTML now, and HTML cannot list the apps in the launcher, start one, or read how
 * long it was in front. Those three things - and the tip links, the settings screens and the update
 * check - are what this object answers, one method per question, each returning JSON so the page
 * can draw whatever it likes with it.
 *
 * Nothing else is exposed. The page cannot read a file, ask for a package it was not given, or
 * open anything but an https link, so an over-the-air page has exactly the reach the Kotlin screen
 * it replaced had: the cards, the buttons behind the gear, and nothing more.
 *
 * Threading: WebView calls these on its own thread, never the UI thread, so anything that starts an
 * activity or writes to the screen is posted to the activity.
 */
class Bridge(private val activity: MainActivity) {

    private val icons = HashMap<String, String>()

    /** Everything the page needs to draw the screen, in one call. */
    @JavascriptInterface
    fun state(): String {
        val pm = activity.packageManager
        val cards = JSONArray()
        var missing = 0
        for (entry in Apps.ALL) {
            val pkg = Apps.resolve(activity, entry)
            if (pkg == null && !entry.slot) missing++
            cards.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("title", entry.title)
                    .put("slot", entry.slot)
                    .put("pkg", pkg ?: JSONObject.NULL)
                    .put("label", if (entry.slot && pkg != null) Apps.appName(pm, pkg) else entry.title)
                    .put("icon", if (pkg != null) icon(pkg) else "")
                    .put("total", Usage.total(activity, entry.id))
                    .put("visits", Usage.count(activity, entry.id))
                    .put("last", Usage.last(activity, entry.id))
            )
        }
        val tips = JSONArray()
        for (link in Support.LINKS) {
            tips.put(JSONObject().put("dollars", link.first).put("url", link.second))
        }
        return JSONObject()
            .put("app", BuildConfig.VERSION_NAME)
            .put("page", PageStore.running(activity))
            .put("play", Updater.fromPlay(activity))
            .put("usage", Usage.granted(activity))
            .put("tracked", Usage.totalAll(activity))
            .put("missing", missing)
            .put("presets", Apps.ALL.count { !it.slot })
            .put("cards", cards)
            .put("tips", tips)
            .toString()
    }

    /**
     * Open a card's app. The moment the hub leaves is the moment the visit starts, and the card and
     * the package are written with it so the usage log can later be asked about that app and not
     * another. A launch that fails is never timed.
     */
    @JavascriptInterface
    fun open(id: String): String {
        val entry = Apps.ALL.firstOrNull { it.id == id } ?: return fail("No such card")
        val pkg = Apps.resolve(activity, entry) ?: return fail("${entry.title} is not on this phone")
        val pm = activity.packageManager
        val intent = pm.getLaunchIntentForPackage(pkg)
            ?: return fail("${Apps.appName(pm, pkg)} has no launch screen")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            activity.runOnUiThread { activity.startActivity(intent) }
            Usage.started(activity, entry, pkg)
            ok("")
        } catch (e: Exception) {
            fail("Could not start ${Apps.appName(pm, pkg)}")
        }
    }

    /** Every app on the phone that has an icon in the launcher, for the picker sheet. */
    @JavascriptInterface
    fun picker(id: String): String {
        val pm = activity.packageManager
        val apps = Apps.launchableApps(pm)
            .map { it.activityInfo.packageName to Apps.label(pm, it) }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase(Locale.ROOT) }
        val out = JSONArray()
        for (app in apps) out.put(JSONObject().put("pkg", app.first).put("label", app.second))
        return out.toString()
    }

    /** Point a card at [pkg]; an empty package gives the card back to its own search. */
    @JavascriptInterface
    fun bind(id: String, pkg: String): String {
        val entry = Apps.ALL.firstOrNull { it.id == id } ?: return fail("No such card")
        Apps.bind(activity, entry, pkg.takeIf { it.isNotEmpty() })
        return ok("")
    }

    @JavascriptInterface
    fun appInfo(pkg: String) {
        if (pkg.isEmpty()) return
        try {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            )
        } catch (e: Exception) {
            // Nothing to show; the page stays where it is.
        }
    }

    /** The one-off switch that lets the hub read how long another app was in front. */
    @JavascriptInterface
    fun usageAccess() {
        try {
            activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (e: Exception) {
            activity.toast("Usage access lives in Settings, Apps, Special access on this phone")
        }
    }

    @JavascriptInterface
    fun resetCard(id: String) {
        Usage.forget(activity, id)
    }

    @JavascriptInterface
    fun resetAll() {
        Usage.forgetAll(activity)
    }

    /** The tip jar: a Stripe page in the browser. The hub never sees a card number. */
    @JavascriptInterface
    fun openUrl(url: String) {
        if (!url.startsWith("https://")) return
        activity.runOnUiThread {
            try {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                activity.toast("No browser on this phone")
            }
        }
    }

    /** Ask GitHub what is newest; the answer arrives back in the page as hubUpdate(...). */
    @JavascriptInterface
    fun checkUpdates(manual: Boolean) {
        activity.checkUpdates(silent = !manual)
    }

    /** Fetch and put in place the page a release published. */
    @JavascriptInterface
    fun updateNow(url: String, version: String) {
        if (!url.startsWith("https://")) return
        activity.applyPage(url, version)
    }

    /** The app's own update: the release's APK into the phone's downloads, never a browser. */
    @JavascriptInterface
    fun downloadBuild(url: String, version: String) {
        if (!url.startsWith("https://")) return
        activity.downloadBuild(url, version)
    }

    /** A copy installed by Play takes the app itself from Play; the page still updates here. */
    @JavascriptInterface
    fun openStore() {
        activity.runOnUiThread { Updater.openStore(activity) }
    }

    @JavascriptInterface
    fun appVersion(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface
    fun pageVersion(): String = PageStore.running(activity)

    /** The page has drawn. It is the shell's proof that the copy it loaded is a working screen. */
    @JavascriptInterface
    fun ready() {
        activity.pageReady()
    }

    // ---- the pieces ----

    private fun ok(message: String) = JSONObject().put("ok", true).put("message", message).toString()

    private fun fail(message: String) =
        JSONObject().put("ok", false).put("message", message).toString()

    /**
     * An app's icon as a PNG data URL, drawn at 96px and remembered. This is the one thing the page
     * cannot ask Android for itself, and a hub with lettered badges instead of icons would not be
     * the same screen.
     */
    private fun icon(pkg: String): String {
        icons[pkg]?.let { return it }
        val url = try {
            val drawable = activity.packageManager.getApplicationIcon(pkg)
            val size = 96
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
        icons[pkg] = url
        return url
    }
}
