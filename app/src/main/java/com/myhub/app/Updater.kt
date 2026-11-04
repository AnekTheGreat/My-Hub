package com.myhub.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One published release: the version, where it can be read about and fetched, and what changed. */
data class Update(val version: String, val url: String, val notes: String)

/**
 * OTA updates: read what the newest release is, compare it with the version this build carries,
 * and - only when that release is newer - take the owner to it.
 *
 * This is the hub's ONLY use of the network, and it is why the app asks for INTERNET. Nothing is
 * uploaded; there is no account and no identifier, and no traffic at all unless there is a
 * release to look at.
 *
 * The answer comes from a small manifest published WITH the release, which is the same shape
 * SideCut, MeasureT and FamTM use: updates.json holding the version, the file, its size and the
 * notes for it. One fixed URL, no API, no tag parsing, and the changelog travels with the build
 * instead of being reconstructed on the phone. The GitHub API and the releases feed are kept as
 * fallbacks for a release that predates the manifest.
 *
 * WHAT THE HUB DOES NOT DO ANY MORE: download the APK and install it. That needed
 * REQUEST_INSTALL_PACKAGES, and a sideloaded app that fetches code over the network and then asks
 * Android to install it is the shape Play Protect blocks - it read the hub as harmful because of
 * it. [browse] hands the release page to the browser instead, the APK downloads there, and the
 * phone's own installer takes it from outside the app. Same check, same notes, no installer.
 */
object Updater {

    private const val TAG = "MyHubUpdater"
    private const val REPO = "AnekTheGreat/My-Hub"

    /**
     * The manifest attached to the newest release. `releases/latest/download/<asset>` always
     * resolves to that release's copy of the asset, so the address never changes - the same trick
     * SideCut's ota/updates.json plays from its published site.
     */
    private const val MANIFEST = "https://github.com/$REPO/releases/latest/download/updates.json"
    private const val DOWNLOADS = "https://github.com/$REPO/releases/download"

    private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    /**
     * The releases feed, read when neither the manifest nor the API answers.
     *
     * api.github.com allows 60 anonymous requests an hour per address, and a phone on a carrier
     * shares its address with everything else behind that gateway, so "rate limit exceeded" is an
     * ordinary reply. GitHub's feeds carry no such limit and need no credentials, and the file name
     * is fixed by release.yml (MyHub-<version>.apk), which makes the download URL predictable.
     */
    private const val FEED = "https://github.com/$REPO/releases.atom"

    /**
     * GitHub refuses any request that arrives without a User-Agent, and what a particular Android
     * version supplies by itself is not something to rely on, so it is stated here: the app and the
     * version asking, and nothing about the phone or the person holding it.
     */
    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "MyHub/" + BuildConfig.VERSION_NAME)
            connectTimeout = 12000
            readTimeout = 25000
            instanceFollowRedirects = true
        }

    /** Runs [done] on a background thread. Null means "nothing newer", "offline", or "no answer". */
    fun check(done: (Update?) -> Unit) {
        Thread { done(fetch()) }.start()
    }

    private fun fetch(): Update? {
        val manifest = manifestJson()
        if (manifest != null) return fromManifest(manifest)
        val json = apiJson()
        return if (json != null) fromJson(json) else fromFeed()
    }

    /** The manifest's text, or null when the newest release does not carry one. */
    private fun manifestJson(): String? = try {
        val c = open(MANIFEST)
        val code = c.responseCode
        if (code == 200) c.inputStream.bufferedReader().use { it.readText() } else {
            Log.w(TAG, "no manifest at the newest release ($code)")
            null
        }
    } catch (e: Exception) {
        Log.w(TAG, "manifest unreachable: ${e.javaClass.simpleName}")
        null
    }

    /**
     * {"version":"1.8.4","apk":"MyHub-1.8.4.apk","size":647231,"notes":["..."],"date":""}
     * - the same keys SideCut's ota/updates.json and MeasureT's updates.json carry.
     */
    private fun fromManifest(text: String): Update? = try {
        val j = JSONObject(text)
        val v = j.optString("version").removePrefix("v")
        val apk = j.optString("apk")
        when {
            v.isEmpty() -> {
                Log.w(TAG, "the manifest names no version")
                null
            }
            apk.isEmpty() -> {
                Log.w(TAG, "the manifest names no APK")
                null
            }
            !newer(v, BuildConfig.VERSION_NAME) -> null
            else -> {
                val notes = j.optJSONArray("notes")?.let { a ->
                    (0 until a.length()).joinToString("\n") { a.optString(it) }
                } ?: ""
                Update(v, "$DOWNLOADS/v$v/$apk", notes)
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "unreadable manifest: ${e.javaClass.simpleName}")
        null
    }

    /** The newest release as JSON, or null when the API answered with anything but 200. */
    private fun apiJson(): String? = try {
        val c = open(LATEST)
        c.setRequestProperty("Accept", "application/vnd.github+json")
        val code = c.responseCode
        if (code == 200) c.inputStream.bufferedReader().use { it.readText() } else {
            Log.w(TAG, "api answered $code, reading the feed instead")
            null
        }
    } catch (e: Exception) {
        Log.w(TAG, "api unreachable: ${e.javaClass.simpleName}")
        null
    }

    private fun fromJson(text: String): Update? = try {
        val j = JSONObject(text)
        val tag = j.optString("tag_name").removePrefix("v")
        val apk = apkOf(j.optJSONArray("assets"))
        when {
            tag.isEmpty() -> {
                Log.w(TAG, "the newest release carries no tag")
                null
            }
            apk == null -> {
                Log.w(TAG, "release $tag has no APK attached")
                null
            }
            !newer(tag, BuildConfig.VERSION_NAME) -> null
            else -> Update(tag, apk, j.optString("body", ""))
        }
    } catch (e: Exception) {
        Log.w(TAG, "unreadable release: ${e.javaClass.simpleName}")
        null
    }

    /**
     * The release APK. A debug asset would install over a release build and was previously just as
     * eligible as the real one, since the loop kept whichever .apk came last.
     */
    private fun apkOf(assets: JSONArray?): String? {
        if (assets == null) return null
        var debug: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name")
            if (!name.endsWith(".apk")) continue
            val url = a.optString("browser_download_url")
            if (url.isEmpty()) continue
            if (name.contains("debug", ignoreCase = true)) debug = url else return url
        }
        return debug
    }

    /** The newest tag from the releases feed, with the APK URL release.yml names after it. */
    private fun fromFeed(): Update? = try {
        val c = open(FEED)
        if (c.responseCode != 200) {
            Log.w(TAG, "feed answered ${c.responseCode}")
            null
        } else {
            val xml = c.inputStream.bufferedReader().use { it.readText() }
            // The first tag link in the feed is the newest release.
            val tag = Regex("/releases/tag/([^\"]+)").find(xml)?.groupValues?.get(1)
            if (tag == null) {
                Log.w(TAG, "the feed lists no release")
                null
            } else {
                val v = tag.removePrefix("v")
                if (!newer(v, BuildConfig.VERSION_NAME)) null
                else Update(
                    v,
                    "$DOWNLOADS/$tag/MyHub-$v.apk",
                    "Published on GitHub."
                )
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "feed unreadable: ${e.javaClass.simpleName}")
        null
    }

    /** "1.10.0" is newer than "1.9.9": each part is compared as a number, never as text. */
    fun newer(a: String, b: String): Boolean {
        val x = a.split(".")
        val y = b.split(".")
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrNull(i)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            val q = y.getOrNull(i)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            if (p != q) return p > q
        }
        return false
    }

    /**
     * True when this copy was installed by the Play Store.
     *
     * A Play copy is updated by Play, so the hub must not offer it a download: store policy is that
     * a Play-distributed app does not send its users to an APK, and the APK on the release page is
     * signed with the upload key rather than the key Play delivers under - so it could not install
     * over the store build anyway.
     */
    fun fromPlay(ctx: Context): Boolean = try {
        val pm = ctx.packageManager
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(ctx.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(ctx.packageName)
        }
        installer == "com.android.vending"
    } catch (e: Exception) {
        Log.w(TAG, "installer unknown: ${e.javaClass.simpleName}")
        false
    }

    /** The Play listing for this build. Play performs the update; the hub only opens the page. */
    fun openStore(ctx: Context) {
        val id = ctx.packageName
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")).addFlags(flags)
            )
        } catch (e: Exception) {
            try {
                ctx.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$id")
                    ).addFlags(flags)
                )
            } catch (e2: Exception) {
                Log.w(TAG, "no Play Store for $id: ${e2.javaClass.simpleName}")
                Toast.makeText(ctx, "No Play Store on this phone", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Where the update is actually taken from: the release page, opened in the browser.
     *
     * The hub used to fetch the APK into its own cache and hand it to Android's installer through a
     * one-file ContentProvider. That is what made Play Protect read the app as harmful - downloading
     * executable code and asking to install it is the dropper signature - and it is also the only
     * reason the app ever asked for REQUEST_INSTALL_PACKAGES. Now the browser downloads the file and
     * the phone's own installer, a different app with its own consent screen, installs it.
     */
    fun browse(ctx: Context, update: Update) {
        // A Play copy is updated by Play itself, so there is no APK to open for it.
        if (fromPlay(ctx)) {
            openStore(ctx)
            return
        }
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(page(update)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.w(TAG, "no browser for ${update.url}: ${e.javaClass.simpleName}")
            Toast.makeText(ctx, "No browser on this phone to open the release", Toast.LENGTH_LONG)
                .show()
        }
    }

    /** The release page itself, which carries the APK and the notes beside it. */
    private fun page(update: Update) = "https://github.com/$REPO/releases/tag/v${update.version}"
}
