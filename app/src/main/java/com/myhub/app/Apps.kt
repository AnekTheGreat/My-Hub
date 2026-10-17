package com.myhub.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Process
import java.util.Locale

/**
 * One tile on the hub: a thing to launch, and every way we know of finding it on the phone.
 *
 * A tile is matched to an installed app in a fixed order, and the first match wins:
 *   1. the app that was chosen by hand (long-press a tile -> Choose app), which is remembered,
 *   2. one of the package names below, in the order written,
 *   3. any launchable app whose own name matches one of [aliases].
 *
 * Step 3 is what lets a tile work when the package id is not the one it was built with -- a build
 * that was renamed, or an id that changed between releases. Naming it by hand is always available
 * as a fallback, and a tile that still finds nothing says so rather than pretending to launch
 * something.
 */
data class HubApp(
    val id: String,
    val title: String,
    val tagline: String,
    val packages: List<String>,
    val aliases: List<String>
)

object Apps {

    /** Every app this hub carries a card for. */
    val ALL: List<HubApp> = listOf(
        HubApp(
            id = "famTM",
            title = "FamTM",
            tagline = "The family tree",
            packages = listOf("com.familytree.app"),
            aliases = listOf("FamTM", "Family Tree")
        ),
        HubApp(
            id = "sidecut",
            title = "SideCut",
            tagline = "Your music player, from the Play Store",
            // The Play build. Its applicationId is com.SideCut.myapp, and on the phone it lists
            // itself as "SideCut Play". Looking for the name "SideCut" alone could never match
            // that label, which is how this card ended up saying "Not installed" on a phone that
            // had the app on it all along; the store name is looked for first now.
            packages = listOf("com.SideCut.myapp", "com.SideCut.play"),
            aliases = listOf("SideCut Play", "SideCut")
        ),
        HubApp(
            id = "sidecutfull",
            title = "SideCut Full",
            tagline = "Your music player, the sideloaded build",
            // SideCut is published twice from one pipeline: the Play build above, and this one — the
            // complete build, sideloaded only. It is given its own applicationId and its own name
            // exactly so the two can be installed side by side, so each gets a card of its own here:
            // one card that opened whichever happened to be installed would always have opened the
            // Play build and left this one unreachable.
            packages = listOf("com.SideCut.myapp.full"),
            aliases = listOf("SideCut Full")
        ),
        HubApp(
            id = "measuret",
            title = "MeasureT",
            tagline = "Measure anything",
            // MeasureT is built out of the Compass-EDC repository, and the applicationId that
            // repository's Capacitor config hands the APK is com.CompassEDC.myapp -- the one
            // package name that really exists on a phone. It is looked for FIRST, because the
            // com.measuret.* guesses below were never what the build installed under, which is
            // why this card used to come up empty on a phone that already had MeasureT.
            packages = listOf(
                "com.CompassEDC.myapp",
                "com.measuret.app",
                "com.measuret",
                "com.measuret.android"
            ),
            // "Compass EDC" is this same app under the name it shipped with before it was
            // renamed: a phone still carrying that install is still carrying MeasureT, so the
            // card opens it rather than claiming there is nothing to open. It sits last, so a
            // build that has moved to the new name always wins.
            aliases = listOf("MeasureT", "Measure T", "Measure", "Compass EDC", "CompassEDC")
        )
    )

    const val PREFS = "myhub"
    fun bindKey(e: HubApp) = "bind.${e.id}"

    /** The package a tile should launch, or null when this phone has nothing that fits it. */
    fun resolve(ctx: Context, e: HubApp): String? {
        val pm = ctx.packageManager
        val chosen = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(bindKey(e), null)
        if (chosen != null && launchable(ctx, chosen)) return chosen
        e.packages.firstOrNull { launchable(ctx, it) }?.let { return it }

        // Names are tried in the order written, so a tile that knows the exact name wins over the
        // looser one: "MeasureT" is looked for before "Measure", which matters because Google's
        // own app is called Measure and would otherwise answer for the MeasureT card.
        val installed = launchableApps(pm)
        for (alias in e.aliases) {
            val hit = installed.firstOrNull { norm(label(pm, it)) == norm(alias) } ?: continue
            val pkg = hit.activityInfo?.packageName
            if (pkg != null) return pkg
        }
        return null
    }

    fun launchable(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getLaunchIntentForPackage(pkg) != null
    } catch (e: Exception) {
        false
    }

    /** Every app on the phone that has an icon in the launcher, in the order the phone returns them. */
    fun launchableApps(pm: PackageManager): List<ResolveInfo> = try {
        pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).filter { it.activityInfo != null }
    } catch (e: Exception) {
        emptyList()
    }

    fun label(pm: PackageManager, info: ResolveInfo): String = try {
        info.loadLabel(pm).toString()
    } catch (e: Exception) {
        info.activityInfo?.packageName ?: "?"
    }

    fun appName(pm: PackageManager, pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    /** "Measure T" and "measuret" are the same name; so are "side cut" and "SideCut". */
    private fun norm(s: String) =
        s.lowercase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "")
}

/**
 * How long each card's app was actually in front of you.
 *
 * Tapping a card is the start of a session; coming back to the hub ends it. What the session is
 * worth is asked of the phone's own usage log: the app's foreground time between those two
 * moments. That is the number that was missing.
 *
 * The first attempt measured the same session with a clock - the time from the tap to the moment
 * the hub came back - and it was wrong by hours. The clock does not stop when the screen does: tap
 * SideCut at eleven at night, come back to the hub the next morning, and the card claimed nine
 * hours in a music player. It also counted a bus ride spent in a different app. The phone's log
 * knows the app was in front and knows when it stopped, so a session is now worth exactly that.
 *
 * Reading the log needs Usage access, a one-off switch in Settings, not a permission prompt. With
 * it off nothing is guessed: no time is banked and the card offers the switch instead. Totals that
 * the old clock wrote are cleared once, on the first run of this version, because they cannot be
 * separated from the hours that were never spent.
 *
 * Nothing runs in the background: the log is read once, when the hub is resumed. A total is per
 * card, not per package: SideCut and SideCut Full are two cards and are timed separately.
 */
object Usage {

    private const val KEY_TOTAL = "usage.total."
    private const val KEY_LAST = "usage.last."
    private const val KEY_COUNT = "usage.count."
    private const val KEY_PENDING_ID = "usage.pending.id"
    private const val KEY_PENDING_PKG = "usage.pending.pkg"
    private const val KEY_PENDING_AT = "usage.pending.at"
    private const val KEY_METHOD = "usage.method"

    /**
     * 1 was the clock. 2 is the usage log. A phone carrying 1 is carrying hours it never spent, so
     * 2 clears the time once rather than leaving the two kinds of number side by side.
     */
    private const val METHOD = 2

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(Apps.PREFS, Context.MODE_PRIVATE)

    /** Move this phone off the clock's totals, once. */
    fun migrate(ctx: Context) {
        val p = prefs(ctx)
        if (p.getInt(KEY_METHOD, 0) >= METHOD) return
        val edit = p.edit()
        for (app in Apps.ALL) {
            edit.remove(KEY_TOTAL + app.id).remove(KEY_COUNT + app.id)
        }
        edit.putInt(KEY_METHOD, METHOD).apply()
    }

    /** Whether this phone will tell the hub how long another app was in front. */
    fun granted(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        return try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /** The hub is leaving for [entry]'s app now; remember the card, the package and the moment. */
    fun started(ctx: Context, entry: HubApp, pkg: String) {
        prefs(ctx).edit()
            .putString(KEY_PENDING_ID, entry.id)
            .putString(KEY_PENDING_PKG, pkg)
            .putLong(KEY_PENDING_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * The hub came back, so the visit that just ended can be priced. Only the time the app was in
     * front is banked, and that now is the card's "last opened". A session with no start, a clock
     * that went backwards, or a phone that will not share its usage log banks nothing rather than
     * a number nobody can stand behind.
     */
    fun returned(ctx: Context) {
        val p = prefs(ctx)
        val id = p.getString(KEY_PENDING_ID, null) ?: return
        val pkg = p.getString(KEY_PENDING_PKG, null)
        val at = p.getLong(KEY_PENDING_AT, 0L)
        p.edit().remove(KEY_PENDING_ID).remove(KEY_PENDING_PKG).remove(KEY_PENDING_AT).apply()
        val now = System.currentTimeMillis()
        if (pkg == null || at <= 0L || now <= at) return
        val spent = foreground(ctx, pkg, at, now)
        if (spent <= 0L) return
        p.edit()
            .putLong(KEY_TOTAL + id, total(ctx, id) + spent)
            .putLong(KEY_LAST + id, now)
            .putInt(KEY_COUNT + id, count(ctx, id) + 1)
            .apply()
    }

    /**
     * Foreground time for [pkg] between two moments, from the phone's usage log.
     *
     * The log's own totals are not used. They are kept in buckets - an hour or a day at a time -
     * and a query that lands inside one is answered with the whole bucket, which is the same kind
     * of over-count this version exists to remove. The events are exact: one line when an app came
     * to the front and one when it went away, so the stretches are summed by [UsageSum].
     *
     * The read starts [UsageSum.PADDING] before the visit because the launch line is written
     * before the hub manages to record the tap.
     */
    private fun foreground(ctx: Context, pkg: String, from: Long, to: Long): Long {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return 0L
        return try {
            val stream = usm.queryEvents(from - UsageSum.PADDING, to) ?: return 0L
            val event = UsageEvents.Event()
            val events = ArrayList<UsageEvent>()
            while (stream.hasNextEvent()) {
                stream.getNextEvent(event)
                if (event.packageName == pkg) {
                    events += UsageEvent(pkg, event.eventType, event.timeStamp)
                }
            }
            UsageSum.foreground(pkg, from, to, events)
        } catch (e: Exception) {
            0L
        }
    }

    fun total(ctx: Context, id: String): Long = prefs(ctx).getLong(KEY_TOTAL + id, 0L)
    fun last(ctx: Context, id: String): Long = prefs(ctx).getLong(KEY_LAST + id, 0L)
    fun count(ctx: Context, id: String): Int = prefs(ctx).getInt(KEY_COUNT + id, 0)

    fun totalAll(ctx: Context): Long = Apps.ALL.sumOf { total(ctx, it.id) }

    fun forget(ctx: Context, id: String) {
        prefs(ctx).edit()
            .remove(KEY_TOTAL + id).remove(KEY_LAST + id).remove(KEY_COUNT + id)
            .apply()
    }

    fun forgetAll(ctx: Context) {
        val edit = prefs(ctx).edit()
        for (app in Apps.ALL) {
            edit.remove(KEY_TOTAL + app.id).remove(KEY_LAST + app.id).remove(KEY_COUNT + app.id)
        }
        edit.apply()
    }

    /** "1h 24m", "7m", "<1m" - the shape a total is read in. Minutes are the smallest unit kept. */
    fun human(ms: Long): String {
        val mins = ms / 60_000L
        val h = mins / 60L
        val m = mins % 60L
        return when {
            h > 0L -> "${h}h ${m}m"
            mins > 0L -> "${mins}m"
            else -> "<1m"
        }
    }

    /** "just now", "3h ago", "2d ago" - when a card's app was last opened from the hub, or null. */
    fun ago(ctx: Context, id: String): String? {
        val last = last(ctx, id)
        if (last <= 0L) return null
        val mins = (System.currentTimeMillis() - last) / 60_000L
        return when {
            mins < 1L -> "just now"
            mins < 60L -> "${mins}m ago"
            mins < 60L * 24L -> "${mins / 60L}h ago"
            else -> "${mins / (60L * 24L)}d ago"
        }
    }
}
