package com.myhub.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

// The palette: the navy of OnePlus Game Space with OnePlus red for the launch action.
private val BG = Color.parseColor("#0C1A2B")
private val CARD = Color.parseColor("#16283F")
private val CARD_MISSING = Color.parseColor("#111E2E")
private val INK = Color.parseColor("#F2F6FB")
private val MUTE = Color.parseColor("#8FA3B8")
private val ACCENT = Color.parseColor("#EB1B2E")

class MainActivity : Activity() {

    private lateinit var list: LinearLayout
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(build())
    }

    // Coming back to the hub is also when an app may have been installed or removed, so the whole
    // screen is worked out again rather than trusting what it looked like last time.
    override fun onResume() {
        super.onResume()
        fill()
    }

    // ---- the screen ----

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            fitsSystemWindows = true
        }
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(22), dp(12), 0)
        }
        bar.addView(TextView(this).apply {
            text = "My Hub"
            setTextColor(INK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_settings)
            imageTintList = ColorStateList.valueOf(MUTE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            contentDescription = "Settings"
            isClickable = true
            setOnClickListener { settings() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(bar)

        summary = TextView(this).apply {
            setTextColor(MUTE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(20), dp(2), dp(20), dp(14))
        }
        root.addView(summary)

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(2), dp(14), dp(24))
        }
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(list)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(TextView(this).apply {
            text = "Tap a card to open its app · press and hold to change what it opens"
            setTextColor(MUTE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(18))
        })
        return root
    }

    private fun fill() {
        list.removeAllViews()
        var found = 0
        for (entry in Apps.ALL) {
            val pkg = Apps.resolve(this, entry)
            if (pkg != null) found++
            list.addView(card(entry, pkg))
        }
        summary.text = "${Apps.ALL.size} apps · $found installed"
        if (found == 0) list.addView(note())
    }

    private fun card(entry: HubApp, pkg: String?): View {
        val here = pkg != null
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(12), dp(16))
            background = rounded(if (here) CARD else CARD_MISSING, 20f)
            isClickable = true
            setOnClickListener { if (here) open(pkg!!) else pick(entry) }
            setOnLongClickListener { menu(entry, pkg); true }
        }
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }

        row.addView(icon(pkg, entry.title))

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(8), 0)
        }
        details.addView(TextView(this).apply {
            text = entry.title
            setTextColor(if (here) INK else MUTE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        details.addView(TextView(this).apply {
            text = if (here) entry.tagline else "Not installed · tap to point this card at an app"
            setTextColor(MUTE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, dp(3), 0, 0)
        })
        if (here) {
            details.addView(TextView(this).apply {
                text = pkg
                setTextColor(Color.parseColor("#5E7A93"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, dp(4), 0, 0)
            })
        }
        row.addView(details, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(action(entry, pkg))
        return row
    }

    /** The app's own icon, or a lettered badge when the card has nothing to point at yet. */
    private fun icon(pkg: String?, title: String): View {
        val size = dp(52)
        val view: View = if (pkg != null) {
            ImageView(this).apply {
                try {
                    setImageDrawable(packageManager.getApplicationIcon(pkg))
                } catch (e: Exception) {
                    setImageDrawable(null)
                }
            }
        } else {
            TextView(this).apply {
                text = title.trim().take(1).uppercase(Locale.ROOT)
                setTextColor(MUTE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                background = rounded(Color.parseColor("#1D3459"), 14f)
            }
        }
        view.layoutParams = LinearLayout.LayoutParams(size, size)
        return view
    }

    private fun action(entry: HubApp, pkg: String?): View {
        val button = TextView(this).apply {
            text = if (pkg != null) "\u25B6" else "+"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (pkg != null) 15f else 20f)
            setTextColor(if (pkg != null) Color.WHITE else MUTE)
            background = StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_pressed),
                    rounded(if (pkg != null) Color.parseColor("#B51424") else Color.parseColor("#22344C"), 24f)
                )
                addState(
                    intArrayOf(),
                    rounded(if (pkg != null) ACCENT else Color.parseColor("#1B2A3F"), 24f)
                )
            }
            isClickable = true
            setOnClickListener { if (pkg != null) open(pkg) else pick(entry) }
        }
        button.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        return button
    }

    private fun note() = TextView(this).apply {
        text = "Nothing on this phone matches these apps yet. Press and hold a card, or tap the + on one, to point it at an app that is installed."
        setTextColor(MUTE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(dp(6), dp(4), dp(6), dp(8))
    }

    // ---- what a card does ----

    private fun open(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            toast("${Apps.appName(packageManager, pkg)} has no launch screen")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("Could not start ${Apps.appName(packageManager, pkg)}")
        }
    }

    private fun menu(entry: HubApp, pkg: String?) {
        val items = mutableListOf<String>()
        if (pkg != null) items += "Open"
        items += "Choose the app\u2026"
        if (pkg != null) items += "App info"
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(entry.title)
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "Open" -> open(pkg!!)
                    "Choose the app\u2026" -> pick(entry)
                    "App info" -> appInfo(pkg!!)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Point a card at any app on the phone, by hand, and remember it. */
    private fun pick(entry: HubApp) {
        val pm = packageManager
        val apps = Apps.launchableApps(pm)
            .map { it.activityInfo.packageName to Apps.label(pm, it) }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase(Locale.ROOT) }
        if (apps.isEmpty()) {
            toast("This phone is not showing any apps to choose from")
            return
        }
        val shown = apps.map { "${it.second}\n${it.first}" }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Open ${entry.title} with\u2026")
            .setItems(shown) { _, which ->
                getSharedPreferences(Apps.PREFS, MODE_PRIVATE).edit()
                    .putString(Apps.bindKey(entry), apps[which].first).apply()
                fill()
                toast("${entry.title} \u2192 ${apps[which].second}")
            }
            .setNeutralButton("Use the default") { _, _ ->
                getSharedPreferences(Apps.PREFS, MODE_PRIVATE).edit()
                    .remove(Apps.bindKey(entry)).apply()
                fill()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun appInfo(pkg: String) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            )
        } catch (e: Exception) {
            toast("No app info on this phone")
        }
    }

    private fun settings() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("My Hub")
            .setItems(arrayOf("Refresh", "How the hub finds your apps")) { _, which ->
                if (which == 0) {
                    fill()
                    toast("Checked again")
                } else {
                    about()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun about() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("How the hub finds your apps")
            .setMessage(
                "A card opens the app it found on this phone.\n\n" +
                    "It looks for the apps it was built for by package name \u2014 FamTM is com.familytree.app, " +
                    "SideCut is com.SideCut.myapp, and the sideloaded SideCut Full is " +
                    "com.SideCut.myapp.full \u2014 and if that finds nothing it looks for an app whose own " +
                    "name matches the card.\n\n" +
                    "SideCut is published twice, so it has two cards: the Play build and the sideloaded " +
                    "full build are separate apps on this phone and each card opens its own.\n\n" +
                    "MeasureT is built from the Compass-EDC repository and installs as " +
                    "com.CompassEDC.myapp, so that is the package its card looks for first \u2014 and " +
                    "if this phone carries it under the name it shipped with before, Compass EDC, " +
                    "the card opens it just the same. A card that finds nothing can be pointed at " +
                    "any app: press and hold it and choose the one you want. The choice is " +
                    "remembered, and 'Use the default' on that screen puts it back to searching.\n\n" +
                    "Nothing is sent anywhere: the hub only ever asks this phone what is installed."
            )
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    // ---- small helpers ----

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }
}
