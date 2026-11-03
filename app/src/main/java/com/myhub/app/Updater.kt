package com.myhub.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** One published release: the version, the APK to fetch, and what changed. */
data class Update(val version: String, val url: String, val notes: String)

/**
 * OTA updates, the way the other apps here do them: ask GitHub for the newest release of this
 * repository, compare its tag with the version this build carries, and - only when that release is
 * newer - download the APK and hand it to Android's own installer.
 *
 * This is the hub's ONLY use of the network, and it is why the app asks for INTERNET. The request
 * is a GET to api.github.com for one public release, then the download of the APK itself: no
 * account, no identifier, nothing uploaded, and no traffic at all unless the phone has a release
 * to look at.
 *
 * The file is downloaded into the hub's own cache and served to the installer by [ApkProvider],
 * because a file:// URI cannot be handed to another app since Android 7. That provider is written
 * here rather than pulled from a library: the hub carries no dependencies, and this is the one
 * method it needs.
 */
object Updater {

    private const val LATEST = "https://api.github.com/repos/AnekTheGreat/My-Hub/releases/latest"

    /** Runs [done] on a background thread. Null means "nothing newer", "offline", or "no answer". */
    fun check(done: (Update?) -> Unit) {
        Thread { done(fetch()) }.start()
    }

    private fun fetch(): Update? = try {
        val c = URL(LATEST).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.connectTimeout = 8000
        c.readTimeout = 8000
        if (c.responseCode != 200) null else {
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = j.optString("tag_name").removePrefix("v")
            val assets = j.optJSONArray("assets")
            var url: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    // The bundle is not installable by hand; only the APK is offered here.
                    if (a.optString("name").endsWith(".apk")) {
                        url = a.optString("browser_download_url")
                    }
                }
            }
            if (tag.isEmpty() || url.isNullOrEmpty()) null
            else if (!newer(tag, BuildConfig.VERSION_NAME)) null
            else Update(tag, url, j.optString("body", ""))
        }
    } catch (e: Exception) {
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
            var ok = false
            try {
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val f = ApkProvider.fileFor(ctx)
                val c = URL(update.url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                c.inputStream.use { input -> f.outputStream().use { output -> input.copyTo(output) } }
                ok = f.length() > 0L
            } catch (e: Exception) {
                ok = false
            }
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
