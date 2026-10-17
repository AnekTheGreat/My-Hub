// The arithmetic behind the hub's time per card, checked on the JVM.
//
// UsageSum is pure on purpose: the phone hands it the usage log's lines and this test hands it
// the same shape, so the scenarios that made the first version wrong - a night's sleep, a detour
// through another app, a launch line written a moment before the tap - can be replayed exactly.
//
//   sh dev/check-usage-sum.sh
//
// Every case is a name, the answer it must give, and why it is in the list.
import com.myhub.app.UsageEvent
import com.myhub.app.UsageSum

private var passed = 0
private var failed = 0

private fun case(name: String, want: Long, got: Long) {
    if (want == got) {
        passed++
        println("  ok   $name")
    } else {
        failed++
        println("  FAIL $name: want ${want}ms, got ${got}ms")
    }
}

private const val MIN = 60_000L
private const val HOUR = 60 * MIN

private fun fg(pkg: String, at: Long) = UsageEvent(pkg, UsageSum.IN_FRONT, at)
private fun bg(pkg: String, at: Long) = UsageEvent(pkg, UsageSum.OUT_OF_FRONT, at)

fun main() {
    val app = "com.SideCut.myapp"
    val from = 1_000_000L

    println("usage-sum:")

    // The launch line lands a moment before the tap is written down. Without the padding the
    // visit would open on a "went away" line and be worth nothing.
    case(
        "a launch line just before the tap still counts from the tap",
        10 * MIN,
        UsageSum.foreground(
            app, from, from + 2 * HOUR,
            listOf(fg(app, from - 300), bg(app, from + 10 * MIN))
        )
    )

    // The bug from the phone: tapped at night, used for a minute and a half, hub opened the next
    // morning and the card claimed nine hours.
    case(
        "an overnight gap is not charged to the card",
        90_000,
        UsageSum.foreground(
            app, from, from + 9 * HOUR,
            listOf(fg(app, from), bg(app, from + 90_000))
        )
    )

    // Home, a detour through another app, then back into SideCut, then the hub.
    case(
        "two stretches add up, the time in another app does not",
        7 * MIN,
        UsageSum.foreground(
            app, from, from + 2 * HOUR,
            listOf(
                fg(app, from),
                bg(app, from + 5 * MIN),
                fg("com.other.app", from + 30 * MIN),
                bg("com.other.app", from + 50 * MIN),
                fg(app, from + 65 * MIN),
                bg(app, from + 67 * MIN)
            )
        )
    )

    // Nothing in the log is no evidence, and no evidence is zero - never the clock again.
    case("a log with no line for this app is worth nothing", 0, UsageSum.foreground(app, from, from + 8 * HOUR, emptyList()))

    case(
        "another app's lines are not this app's time",
        0,
        UsageSum.foreground(
            app, from, from + HOUR,
            listOf(fg("com.other.app", from), bg("com.other.app", from + HOUR))
        )
    )

    // Still in front when the hub came back: the visit ends at the return, not at the next line.
    // It starts when the log says the app came to the front - the launch takes a moment.
    case(
        "an app still in front at the return counts up to the return",
        30 * MIN - 200,
        UsageSum.foreground(app, from, from + 30 * MIN, listOf(fg(app, from + 200)))
    )

    // A pause is a stop, not a remark: the four minutes before it and the hour after the resume
    // are both real time in the app, and the two hours in between are not.
    case(
        "a pause ends a stretch and the resume starts the next one",
        64 * MIN,
        UsageSum.foreground(
            app, from, from + 3 * HOUR,
            listOf(
                fg(app, from),
                bg(app, from + 4 * MIN),
                fg(app, from + 2 * HOUR),
                bg(app, from + 3 * HOUR)
            )
        )
    )

    // The screen went off in the app and the hub was opened hours later: the pause line is where
    // the count stops, so nothing after it is charged to the card.
    case(
        "the screen going off ends the visit",
        4 * MIN,
        UsageSum.foreground(
            app, from, from + 6 * HOUR,
            listOf(fg(app, from), bg(app, from + 4 * MIN))
        )
    )

    case("a visit with no length is worth nothing", 0, UsageSum.foreground(app, from, from, listOf(fg(app, from))))

    // A line after the return can only be a clock skew; the visit still cannot run past `to`.
    case(
        "a line after the return cannot stretch the visit",
        5 * MIN,
        UsageSum.foreground(app, from, from + 5 * MIN, listOf(fg(app, from), bg(app, from + HOUR)))
    )

    println("usage-sum: $passed passed, $failed failed")
    if (failed != 0) kotlin.system.exitProcess(1)
}
