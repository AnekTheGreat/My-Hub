package com.myhub.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * The tip jar.
 *
 * SideCut already takes tips through Stripe Payment Links: one fixed amount per link, paid on a
 * card page Stripe hosts, opened in the browser. This is the same five amounts and the same five
 * links. Nothing is baked into the APK beyond a URL string, no key of any kind is in the app, and
 * there is no server of ours in the middle - the phone hands the page to the browser and Stripe
 * does the rest.
 *
 * A tip unlocks nothing here on purpose: the hub is the same app whether you tip or not, which is
 * why there is no receipt to check and nothing to keep in sync. If a link is ever retired, it is
 * retired in one place, in this list.
 */
object Support {

    /** dollars -> Stripe Payment Link. The same tiers, in the same order, as SideCut's Donate tab. */
    val LINKS: List<Pair<Int, String>> = listOf(
        2 to "https://buy.stripe.com/eVqcN48Yn37BeBOagIdMI00",
        5 to "https://buy.stripe.com/28E4gyb6vfUn1P2dsUdMI01",
        10 to "https://buy.stripe.com/4gM4gy5Mb8rVbpC1KcdMI02",
        25 to "https://buy.stripe.com/6oUdR8fmLaA351e1KcdMI03",
        50 to "https://buy.stripe.com/4gM8wOb6v23x8dqcoQdMI04"
    )

    fun label(dollars: Int): String = "Tip \$$dollars through Stripe"

    /** Hand a hosted Stripe page to the browser. The hub never sees a card number. */
    fun open(ctx: Context, url: String) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(ctx, "No browser on this phone", Toast.LENGTH_SHORT).show()
        }
    }
}
