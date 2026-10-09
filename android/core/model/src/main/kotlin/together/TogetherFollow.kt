package model.together

import kotlin.math.abs

/**
 * Following a session: where it is now, whether it is still live, which edit
 * a screen follows, and what that screen must do to match it. Ported from
 * `follow.js`.
 */

/** Further apart than this, a screen seeks. */
const val DRIFT_TOLERANCE_SECS = 0.5

/** A playing session lasts this long past its title's end, then it is stale. */
private const val PAST_END_GRACE_SECS = 120
private const val UNKNOWN_LENGTH_LIFETIME_MS = 6 * 60 * 60 * 1000L
private const val PAUSED_LIFETIME_MS = 3 * 60 * 60 * 1000L

/** A jump this big between two edits reads as a seek, not as playing on. */
private const val SEEK_NOTICE_SECS = 2

/** What a screen is doing now; [set] is null when nothing is open. */
data class ScreenState(
    val set: String?,
    val playing: Boolean,
    val pos: Double,
    val rate: Double,
)

sealed interface Step {
    data class Open(
        val set: String,
        val pos: Double,
        val playing: Boolean,
        val rate: Double,
    ) : Step

    data class Rate(
        val rate: Double,
    ) : Step

    data class Seek(
        val pos: Double,
    ) : Step

    data object Play : Step

    data object Pause : Step
}

enum class Change { PLAYED, PAUSED, SOUGHT, RATE, TITLE, JOINED, LEFT }

data class Noticed(
    val change: Change,
    val label: String?,
)

fun expectedPosition(
    s: Session,
    now: Long,
): Double = if (s.playing) maxOf(0.0, s.pos + (now - s.at) / 1000.0 * s.rate) else s.pos

/**
 * Liveness comes from the state itself, with no keep-alive edits. A film nobody
 * pauses sends no edits for hours, and a keep-alive could overwrite a real
 * action sent in the same second.
 */
fun isLive(
    s: Session,
    durationSecs: Double?,
    now: Long,
): Boolean =
    when {
        s.ended -> false
        !s.playing -> now - s.at < PAUSED_LIFETIME_MS
        durationSecs == null -> now - s.at < UNKNOWN_LENGTH_LIFETIME_MS
        else -> expectedPosition(s, now) <= durationSecs + PAST_END_GRACE_SECS
    }

/**
 * The session to follow after hearing [incoming]. A screen's own edit coming
 * back is old news while its current session is still that write. If another
 * screen's edit came in between, the echo arriving after it means this
 * screen's write landed last, and is the message now.
 */
fun follow(
    current: Session?,
    incoming: Session,
    me: String,
): Session {
    val stillMine = current != null && current.session == incoming.session && current.by == me
    return if (incoming.by == me && stillMine) current else incoming
}

fun decide(
    own: ScreenState,
    s: Session,
    now: Long,
): List<Step> {
    val pos = expectedPosition(s, now)
    if (own.set != s.set) return listOf(Step.Open(s.set, pos, s.playing, s.rate))
    return buildList {
        if (own.rate != s.rate) add(Step.Rate(s.rate))
        if (abs(own.pos - pos) > DRIFT_TOLERANCE_SECS) add(Step.Seek(pos))
        if (own.playing != s.playing) add(if (s.playing) Step.Play else Step.Pause)
    }
}

/** What another screen did, for the two-second notice; null when nothing visible changed. */
fun changeOf(
    prev: Session,
    next: Session,
): Noticed? {
    val label = (next.members + prev.members).firstOrNull { it.screen == next.by }?.label
    val had = prev.members.map { it.screen }.toSet()
    val has = next.members.map { it.screen }.toSet()
    val change =
        when {
            next.set != prev.set -> Change.TITLE
            next.members.any { it.screen !in had } -> Change.JOINED
            prev.members.any { it.screen !in has } -> Change.LEFT
            next.playing != prev.playing -> if (next.playing) Change.PLAYED else Change.PAUSED
            next.rate != prev.rate -> Change.RATE
            abs(expectedPosition(prev, next.at) - next.pos) > SEEK_NOTICE_SECS -> Change.SOUGHT
            else -> return null
        }
    return Noticed(change, label)
}
