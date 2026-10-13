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
            text = "Tap a card to open its app \u00b7 press and hold to change what it opens"
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
        summary.text = "${Apps.ALL.size} apps \u00b7 $found installed"
        if (found == 0) list.addView(note())
    }
