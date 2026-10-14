package com.myhub.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
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
                "com.measure.4",
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
