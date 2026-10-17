package com.myhub.app

/**
 * The arithmetic behind a card's time, kept away from Android so it can be tested on its own.
 *
 * The phone's usage log is a list of moments: an app came to the front, an app went away. What a
 * visit is worth is the sum of the stretches where this app was in front, clipped to the visit -
 * so a stretch that began before the visit starts counting at the visit, and one that was still
 * running when the hub came back stops there.
 *
 * Two rules matter, and both exist because their opposite was wrong:
 *
 *  - Nothing is counted without a "came to the front" line. If the log has no evidence the app was
 *    ever in front, the visit is worth zero. The old clock assumed the app was in front from the
 *    tap, and a launch that never happened (or a log that was cleared) then turned into hours.
 *  - The log is read with a little padding before the visit, because the "came to the front" line
 *    is written when the app is launched - which is a moment BEFORE the hub finishes recording the
 *    tap. Without the padding every visit would open on a "went away" line and be worth nothing.
 */
data class UsageEvent(val pkg: String, val type: Int, val at: Long)

object UsageSum {

    /** MOVE_TO_FOREGROUND, and the same number as ACTIVITY_RESUMED on API 29+. */
    const val IN_FRONT = 1

    /** MOVE_TO_BACKGROUND, and the same number as ACTIVITY_PAUSED on API 29+. */
    const val OUT_OF_FRONT = 2

    /** How far before the visit the log is read, so the launch line is seen. */
    const val PADDING = 60_000L

    /**
     * Milliseconds [pkg] was in front, inside [from]..[to]. [events] may begin before [from]
     * (that is the padding) and is expected in time order; it does not have to be filtered.
     */
    fun foreground(pkg: String, from: Long, to: Long, events: List<UsageEvent>): Long {
        if (to <= from) return 0L
        var inFront = false
        var start = from
        var total = 0L
        for (event in events) {
            if (event.pkg != pkg) continue
            when (event.type) {
                IN_FRONT -> if (!inFront) {
                    start = if (event.at < from) from else event.at
                    inFront = true
                }
                OUT_OF_FRONT -> if (inFront) {
                    val away = if (event.at > to) to else event.at
                    if (away > start) total += away - start
                    inFront = false
                }
            }
        }
        if (inFront && to > start) total += to - start
        return total
    }
}
