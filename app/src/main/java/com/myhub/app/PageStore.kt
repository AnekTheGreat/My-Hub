package com.myhub.app

import android.content.Context
import java.io.File

/**
 * The page My Hub draws, and the over-the-air copy of it.
 *
 * The screen used to be built in Kotlin, so changing a word meant a new APK. It is a page now: the
 * APK carries a copy in assets, and a newer copy replaces it without Play and without an installer
 * - which is exactly how SideCut, MeasureT and FamTM have always updated.
 *
 * Three rules make that safe, and each one exists because its opposite would leave a phone with a
 * screen it cannot use:
 *
 *  - Only a page that carries [MARK] and names a version is accepted. GitHub answers an error with
 *    a perfectly valid download, and a page that is not the page would otherwise become the hub's
 *    only screen.
 *  - A new APK always wins. A copy stored before this APK was installed is deleted, so the page on
 *    a phone is never older than the code around it.
 *  - [clear] exists because the shell watches for the first draw: a page that loads but never says
 *    it is up is thrown away and the bundled copy is drawn instead.
 *
 * The stored copy lives in the app's own files, is replaced by a rename so a half-written download
 * is never served, and is read once per process.
 */
object PageStore {

    /** Every published page carries this word; a download without it is not the hub's screen. */
    const val MARK = "myhub-page"

    private const val DIR = "page"
    private const val NAME = "index.html"

    /** The page is about 40 KB. Anything this size is a wrong URL, not an update. */
    private const val MAX = 4 * 1024 * 1024

    private var cached: String? = null

    private fun dir(ctx: Context) = File(ctx.filesDir, DIR)
    private fun file(ctx: Context) = File(dir(ctx), NAME)

    /** Where the stored page is, for the WebView to load. */
    fun path(ctx: Context): String = file(ctx).absolutePath

    /** Whether an over-the-air copy is being served instead of the bundled one. */
    fun stored(ctx: Context): Boolean = file(ctx).let { it.isFile && it.length() > 0L }

    /** The version a page names - `HUB_PAGE = "1.9.0"` - or null when it names none. */
    fun versionOf(text: String): String? =
        Regex("HUB_PAGE\\s*=\\s*[\"']([0-9][^\"']*)[\"']").find(text)?.groupValues?.get(1)

    /** The page being served right now: the stored copy when there is one, else the bundled one. */
    fun text(ctx: Context): String {
        cached?.let { return it }
        val t = try {
            if (stored(ctx)) file(ctx).readText() else bundled(ctx)
        } catch (e: Exception) {
            try { bundled(ctx) } catch (e2: Exception) { "" }
        }
        cached = t
        return t
    }

    private fun bundled(ctx: Context): String =
        ctx.assets.open(NAME).bufferedReader().use { it.readText() }

    /** The version running on this phone, for the update comparison and the page's own footer. */
    fun running(ctx: Context): String = versionOf(text(ctx)) ?: "0"

    /**
     * Take a downloaded page as the one to draw from now on. [version] is what the release
     * promised; a file that names another version, or carries no marker, is refused rather than
     * installed - the release and the page must agree about what just arrived.
     */
    fun save(ctx: Context, bytes: ByteArray, version: String): Boolean {
        if (bytes.isEmpty() || bytes.size > MAX) return false
        val text = try {
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return false
        }
        if (!text.contains(MARK)) return false
        if (versionOf(text) != version) return false
        return try {
            val d = dir(ctx).apply { mkdirs() }
            val tmp = File(d, "$NAME.tmp")
            tmp.writeBytes(bytes)
            val target = file(ctx)
            if (target.exists()) target.delete()
            val placed = tmp.renameTo(target)
            if (placed) cached = text else tmp.delete()
            placed
        } catch (e: Exception) {
            false
        }
    }

    /** Throw the stored page away; the bundled one is drawn from the next start. */
    fun clear(ctx: Context) {
        try { file(ctx).delete() } catch (e: Exception) { }
        cached = null
    }

    /**
     * A page stored before this APK was installed is dropped: an install is newer than anything it
     * replaced, and a screen must not outlive the code that was meant to run it.
     */
    fun dropStale(ctx: Context) {
        if (!stored(ctx)) return
        val installed = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime
        } catch (e: Exception) {
            0L
        }
        if (installed > 0L && file(ctx).lastModified() < installed) clear(ctx)
    }
}
