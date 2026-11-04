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

// The palette: the navy of OnePlus Game Space, and nothing else. The screen carries no red any
// more - a card is a name, an icon and, when there is something to say, one quiet line.
private val BG = Color.parseColor("#0C1A2B")
private val BG_TOP = Color.parseColor("#10243B")
private val CARD = Color.parseColor("#16283F")
private val CARD_DOWN = Color.parseColor("#1C314C")
private val LINE = Color.parseColor("#22344C")
private val LINE_DOWN = Color.parseColor("#2C4260")
private val INK = Color.parseColor("#F2F6FB")
private val MUTE = Color.parseColor("#8FA3B8")
private val CHEVRON = Color.parseColor("#47617A")
private val TIMED = Color.parseColor("#63C6A6")

class MainActivity : Activity() {

    private lateinit var list: LinearLayout
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A phone upgrading from the clock-based totals clears them once here, before a card is
        // drawn, so no card is ever shown a number this version did not earn.
        Usage.migrate(this)
        setContentView(build())
        // One quiet check per launch. It only speaks when a newer release exists, so a hub that is
        // already current - or offline - opens exactly as it did before the updater existed.
        checkUpdates(silent = true)
    }

    // Coming back to the hub is also when an app may have been installed or removed, so the whole
    // screen is worked out again rather than trusting what it looked like last time. It is also
    // the one moment the visit that just ended can be measured: the hub left because a card opened
    // an app, and this is the return, so the clock on that app is banked before anything is drawn.
    override fun onResume() {
        super.onResume()
        Usage.returned(this)
        fill()
    }

    // ---- the screen ----

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // One soft wash from the top instead of a flat field: the cards then sit on something
            // rather than float in it, and the header has an edge to disappear into.
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(BG_TOP, BG)
            )
            fitsSystemWindows = true
        }
        window.statusBarColor = BG_TOP
        window.navigationBarColor = BG

        // The header is the app's name and one control. Nothing counts itself up here.
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(26), dp(10), 0)
        }
        bar.addView(TextView(this).apply {
            text = "My Hub"
            setTextColor(INK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 27f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = -0.01f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_settings)
            imageTintList = ColorStateList.valueOf(MUTE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            contentDescription = "Settings"
            isClickable = true
            setOnClickListener { settings() }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        root.addView(bar)

        // Only ever says something worth reading: a card that has no app, or the time you have
        // spent. When there is neither, it is not there at all.
        summary = TextView(this).apply {
            setTextColor(MUTE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(dp(22), dp(4), dp(22), dp(2))
        }
        root.addView(summary)

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(0), dp(16), dp(20))
        }
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(list)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(TextView(this).apply {
            text = "Hold a card to change its app"
            setTextColor(CHEVRON)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(18))
        })
        return root
    }

    private fun fill() {
        list.removeAllViews()
        var missing = 0
        var appsTitled = false
        var slotsTitled = false
        for (entry in Apps.ALL) {
            // Two headings, so the free slots read as a group of their own instead of trailing the
            // apps that came with the hub.
            if (!entry.slot && !appsTitled) {
                list.addView(section("YOUR APPS"))
                appsTitled = true
            }
            if (entry.slot && !slotsTitled) {
                list.addView(section("YOUR SLOTS"))
                slotsTitled = true
            }
            val pkg = Apps.resolve(this, entry)
            // An empty slot is not a problem to report: it is empty because nobody filled it yet.
            if (pkg == null && !entry.slot) missing++
            list.addView(card(entry, pkg))
        }
        val presets = Apps.ALL.count { !it.slot }
        val tracked = Usage.totalAll(this)
        summary.text = when {
            missing > 0 -> "$missing of $presets apps still need to be added"
            tracked > 0L -> "${Usage.human(tracked)} across your apps"
            else -> ""
        }
        summary.visibility = if (summary.text.isEmpty()) View.GONE else View.VISIBLE
    }

    /** A quiet heading over a group of cards: small, spaced out, and gone again on the next fill. */
    private fun section(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(CHEVRON)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
        letterSpacing = 0.18f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(8), dp(16), dp(8), dp(9))
    }

    /**
     * One card: the app's icon, its name, and at most one line under it. The package name and the
     * red launch button that used to sit on every row are gone - the whole card is the button, and
     * the chevron says so.
     */
    private fun card(entry: HubApp, pkg: String?): View {
        val here = pkg != null
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = rowBackground(here)
            elevation = dp(2).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { if (here) open(entry, pkg!!) else pick(entry) }
            setOnLongClickListener { menu(entry, pkg); true }
        }
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }

        row.addView(icon(entry, pkg))

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), 0, dp(8), 0)
        }
        details.addView(TextView(this).apply {
            text = title(entry, pkg)
            setTextColor(if (here) INK else MUTE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val sub = subtitle(entry, pkg)
        if (sub != null) {
            val timed = here && Usage.total(this, entry.id) > 0L
            details.addView(TextView(this).apply {
                text = sub
                setTextColor(if (timed) TIMED else MUTE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setPadding(0, dp(4), 0, 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }
        row.addView(details, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(TextView(this).apply {
            text = "\u203A"
            setTextColor(CHEVRON)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
        })
        return row
    }

    /** A card's name: its own, or the app it was pointed at when it is a slot. */
    private fun title(entry: HubApp, pkg: String?): String =
        if (entry.slot && pkg != null) Apps.appName(packageManager, pkg) else entry.title

    /** The one line under a name, or null when there is nothing worth saying. */
    private fun subtitle(entry: HubApp, pkg: String?): String? = when {
        pkg == null && entry.slot -> "Empty \u00B7 tap to put an app here"
        pkg == null -> "Not installed \u00B7 tap to choose an app"
        Usage.total(this, entry.id) > 0L -> usageLine(entry)
        else -> null
    }

    /** The app's own icon, a lettered badge for a card whose app is not here, + for an empty slot. */
    private fun icon(entry: HubApp, pkg: String?): View {
        val size = dp(44)
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
                text = if (entry.slot) "+" else entry.title.trim().take(1).uppercase(Locale.ROOT)
                setTextColor(MUTE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (entry.slot) 21f else 18f)
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                background = if (entry.slot) rounded(Color.TRANSPARENT, 13f, LINE)
                else rounded(Color.parseColor("#1D3459"), 13f)
            }
        }
        view.layoutParams = LinearLayout.LayoutParams(size, size)
        return view
    }

    /** A filled card for an app that is here, an outlined one for a card that is not. */
    private fun rowBackground(hasApp: Boolean): StateListDrawable = StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_pressed),
            if (hasApp) rounded(CARD_DOWN, 20f) else rounded(Color.TRANSPARENT, 20f, LINE_DOWN)
        )
        addState(
            intArrayOf(),
            if (hasApp) rounded(CARD, 20f) else rounded(Color.TRANSPARENT, 20f, LINE)
        )
    }

    // ---- what a card does ----

    private fun open(entry: HubApp, pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            toast("${Apps.appName(packageManager, pkg)} has no launch screen")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
            // The hub is on its way out; this is the moment the visit starts, and the package is
            // written with it so the usage log can be asked about that app and not another. It is
            // only written after the app actually started, so a launch that failed is never timed.
            Usage.started(this, entry, pkg)
        } catch (e: Exception) {
            toast("Could not start ${Apps.appName(packageManager, pkg)}")
        }
    }

    /** "2h 14m over 3 visits \u00B7 last opened 4h ago" - only ever drawn once there is time. */
    private fun usageLine(entry: HubApp): String {
        val total = Usage.total(this, entry.id)
        val visits = Usage.count(this, entry.id)
        val visitWord = if (visits == 1) "visit" else "visits"
        val since = Usage.ago(this, entry.id)?.let { " \u00B7 $it" } ?: ""
        return "${Usage.human(total)} over $visits $visitWord$since"
    }

    private fun menu(entry: HubApp, pkg: String?) {
        val items = mutableListOf<String>()
        if (pkg != null) items += "Open"
        items += if (pkg != null) "Change the app\u2026" else "Choose an app\u2026"
        if (pkg != null && entry.slot) items += "Empty this slot"
        if (pkg != null) items += "App info"
        if (pkg != null && Usage.total(this, entry.id) > 0L) items += "Reset this card's time"
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(title(entry, pkg))
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "Open" -> open(entry, pkg!!)
                    "Change the app\u2026", "Choose an app\u2026" -> pick(entry)
                    "Empty this slot" -> {
                        // The one thing a slot owns is a binding, so emptying it is just that
                        // binding going away - the time it earned stays where it is.
                        Apps.bind(this, entry, null)
                        fill()
                        toast("${entry.title} is empty again")
                    }
                    "App info" -> appInfo(pkg!!)
                    "Reset this card's time" -> {
                        Usage.forget(this, entry.id)
                        fill()
                        toast("Cleared ${entry.title}'s time")
                    }
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
            .setTitle(
                if (entry.slot) "Put an app in ${entry.title}"
                else "Open ${entry.title} with\u2026"
            )
            .setItems(shown) { _, which ->
                Apps.bind(this, entry, apps[which].first)
                fill()
                toast("${entry.title} \u2192 ${apps[which].second}")
            }
            .setNeutralButton(if (entry.slot) "Empty the slot" else "Use the default") { _, _ ->
                Apps.bind(this, entry, null)
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
        val items = mutableListOf(
            "Refresh", "Check for updates", "How the hub finds your apps", "\u2665 Support My Hub"
        )
        if (!Usage.granted(this)) items += "Turn on Usage access, to time each app"
        items += "Reset all tracked time"
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("My Hub")
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "Refresh" -> {
                        fill()
                        toast("Checked again")
                    }
                    "Check for updates" -> checkUpdates(silent = false)
                    "How the hub finds your apps" -> about()
                    "\u2665 Support My Hub" -> support()
                    "Reset all tracked time" -> confirmReset()
                    else -> usageAccess()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /**
     * Ask GitHub whether a newer My Hub has been published, and offer it.
     *
     * The silent form runs once per launch and says nothing unless there is something to offer: a
     * phone with no connection, or one already running the newest release, sees no difference at
     * all. The manual form answers either way, so tapping Check for updates never does nothing.
     */
    private fun checkUpdates(silent: Boolean) {
        // A copy installed by Play is updated by Play. Asking GitHub as well would offer a download
        // the store already handles, and pointing a Play install at an APK is exactly what Play's
        // policy forbids, so the check is skipped and the listing is opened instead.
        if (Updater.fromPlay(this)) {
            if (!silent) Updater.openStore(this)
            return
        }
        if (!silent) toast("Checking for updates\u2026")
        Updater.check { update ->
            runOnUiThread {
                if (update != null) offerUpdate(update)
                else if (!silent) toast("My Hub ${BuildConfig.VERSION_NAME} is the newest there is")
            }
        }
    }

    /** Say what the release changes, and what tapping the button will do about it. */
    private fun offerUpdate(update: Update) {
        val notes = update.notes.lines()
            .map { it.trim().removePrefix("-").trim() }
            .filter { it.isNotEmpty() }
            .take(8)
            .joinToString("\n\u2022 ", prefix = "\u2022 ")
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("My Hub ${update.version} is out")
            .setMessage(
                "You have ${BuildConfig.VERSION_NAME}.\n\n" +
                    (if (notes.isNotEmpty()) "$notes\n\n" else "") +
                    "The release opens in your browser: the APK downloads there and your " +
                    "phone's own installer takes it from outside the hub. My Hub does not " +
                    "download or install anything itself."
            )
            .setPositiveButton("Open the release") { _, _ -> browseUpdate(update) }
            .setNegativeButton("Later", null)
            .show()
    }

    /**
     * The update is taken from the release page in the browser, not from here. Nothing is
     * downloaded into the hub's cache and nothing is handed to an installer, which is exactly why
     * the app no longer asks for permission to install packages - and why Play Protect stopped
     * reading it as a dropper.
     */
    private fun browseUpdate(update: Update) = Updater.browse(this, update)

    /**
     * The tip jar. The five amounts are Stripe Payment Links - fixed amounts on a page Stripe
     * hosts - so the hub only hands a URL to the browser. Nothing unlocks, no key is here, and
     * there is no server of ours in between.
     */
    private fun support() {
        val labels = Support.LINKS.map { Support.label(it.first) }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Support My Hub")
            .setItems(labels) { _, which -> Support.open(this, Support.LINKS[which].second) }
            .setNegativeButton("Not now", null)
            .show()
    }

    /** The one-off switch that lets the hub read how long another app was in front. */
    private fun usageAccess() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            toast("Turn My Hub on in Usage access, then come back")
        } catch (e: Exception) {
            toast("Usage access lives in Settings \u2192 Apps \u2192 Special access on this phone")
        }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Reset all tracked time")
            .setMessage(
                "Every card's total goes back to zero and its second line disappears again. " +
                    "Nothing else changes: the apps themselves, and which app each card opens, " +
                    "are untouched."
            )
            .setPositiveButton("Reset") { _, _ ->
                Usage.forgetAll(this)
                fill()
                toast("Tracked time cleared")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun about() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("How the hub finds your apps")
            .setMessage(
                "A card opens the app it found on this phone.\n\n" +
                    "The four cards the hub was built with look for their apps by package name, " +
                    "and if that finds nothing they look for an app whose own name matches. " +
                    "SideCut's card opens the release build, com.SideCut.myapp, and knows the name " +
                    "the phone shows it under, SideCut Play; MeasureT installs from the Compass-EDC " +
                    "repository as com.CompassEDC.myapp; UJournal installs from the Journal " +
                    "repository as app.ujournal.\n\n" +
                    "Slot 1 and Slot 2 are yours: they hold nothing until you choose an app for " +
                    "them, and then they take that app's icon and name. Tap an empty slot to " +
                    "choose, or hold one that is filled to change it or empty it again.\n\n" +
                    "Any card can be pointed at any app: press and hold it and choose the one you " +
                    "want. The choice is remembered, and 'Use the default' puts a built-in card " +
                    "back to searching.\n\n" +
                    "Nothing about you is sent anywhere. The hub makes one connection of its own: " +
                    "when it opens it asks GitHub for the newest release of itself, so it can tell " +
                    "you when a newer one exists and take you to it instead of leaving you to hunt " +
                    "for it. That request is for a public release and carries nothing else; Check " +
                    "for updates behind the gear asks again on demand. A copy installed from Google " +
                    "Play makes no such request at all: Play is what installs its updates, and " +
                    "Check for updates opens the Play listing instead.\n\n" +
                    "When a card has been opened from here, it also says how long that app was " +
                    "actually in front, taken from the phone's own usage log - which stops " +
                    "counting when the screen does or you move to something else, so a night's " +
                    "sleep is not charged to a card. Reading that log needs Usage access: a " +
                    "one-off switch, not a permission prompt, found in the menu behind the gear."
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

    private fun rounded(color: Int, radiusDp: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
}
