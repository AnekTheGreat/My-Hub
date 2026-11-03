package com.myhub.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** One published release: the version, the APK to fetch, and what changed. */
data class Update(val version: String, val url: String, val notes: String)

/**
 * OTA updates: read what the newest release is, compare it with the version this build carries,
 * and - only when that release is newer - download the APK and hand it to Android's installer.
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
 * The file is downloaded into the hub's own cache and served to the installer by [ApkProvider],
 * because a file:// URI cannot be handed to another app since Android 7. That provider is written
 * here rather than pulled from a library: the hub carries no dependencies, and this is the one
 * method it needs.
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
     * {"version":"1.8.1","apk":"MyHub-1.8.1.apk","size":646318,"notes":["..."],"date":"..."}
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
     * Android only installs a package from an app the owner has allowed to install, so the switch is
     * opened for them instead of the install failing with no explanation. True when it is already on.
     */
    fun ensurePermission(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        if (ctx.packageManager.canRequestPackageInstalls()) return true
        try {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Toast.makeText(
                ctx, "Allow installs for My Hub, then tap Check for updates again", Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "Installs from this app are switched off in Settings", Toast.LENGTH_LONG)
                .show()
        }
        return false
    }

    /** Downloads [update] into the cache, replacing any earlier file. [done] runs off the UI thread. */
    fun download(ctx: Context, update: Update, done: (Boolean) -> Unit) {
        Thread {
            val f = ApkProvider.fileFor(ctx)
            var ok = false
            try {
                File(ctx.cacheDir, "updates").mkdirs()
                File(ctx.cacheDir, "updates").listFiles()?.forEach { it.delete() }
                val c = open(update.url)
                val code = c.responseCode
                if (code != 200) {
                    Log.w(TAG, "download answered $code")
                } else {
                    c.inputStream.use { input -> f.outputStream().use { output -> input.copyTo(output) } }
                    // Anything GitHub sends can arrive with a successful-looking body, and an error
                    // page saved under this name reaches Android as "there was a problem parsing
                    // the package". An APK is a zip: it starts 50 4B and it is not tiny.
                    ok = f.length() > 20_000L &&
                        f.inputStream().use { it.read() == 0x50 && it.read() == 0x4B }
                    if (!ok) Log.w(TAG, "${f.length()} bytes arrived that are not an APK")
                }
            } catch (e: Exception) {
                Log.w(TAG, "download failed: ${e.javaClass.simpleName}")
            }
            // A half file left in the cache is handed to the installer next time and fails there.
            if (!ok) f.delete()
            done(ok)
        }
    }

    /** Hand the downloaded file to the system installer, which shows its own confirmation. */
    fun install(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(ApkProvider.uriFor(ctx), "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(ctx, "No installer on this phone", Toast.LENGTH_LONG).show()
        }
    }
}
