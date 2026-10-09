package model.together

import kotlin.math.roundToLong

/** What each screen writes; ported from `moves.js`. A change made while following is never one of these. */
data class Playback(
    val set: String,
    val playing: Boolean,
    val pos: Double,
    val rate: Double,
)

/** The viewer's own change; a null field keeps the session's current value. */
data class PlaybackChange(
    val set: String? = null,
    val playing: Boolean? = null,
    val pos: Double? = null,
    val rate: Double? = null,
)

// roundToLong rounds half up for positives, as JS Math.round does; kotlin.math.round rounds half to even.
private fun toMillis(secs: Double): Double = (secs * 1000).roundToLong() / 1000.0

fun started(
    id: String,
    me: Member,
    own: Playback,
    now: Long,
): Session = Session(id, own.set, own.playing, toMillis(own.pos), own.rate, now, me.screen, listOf(me), ended = false)

fun joined(
    s: Session,
    me: Member,
): Session {
    val present = s.members.any { it.screen == me.screen }
    return s.copy(by = me.screen, members = if (present) s.members else s.members + me)
}

fun left(
    s: Session,
    screen: String,
): Session {
    val members = s.members.filter { it.screen != screen }
    return s.copy(by = screen, members = members, ended = members.isEmpty())
}

fun acted(
    s: Session,
    change: PlaybackChange,
    me: String,
    now: Long,
): Session =
    s.copy(
        set = change.set ?: s.set,
        playing = change.playing ?: s.playing,
        pos = toMillis(change.pos ?: expectedPosition(s, now)),
        rate = change.rate ?: s.rate,
        at = now,
        by = me,
    )

/** The first screen to reach the end of a film, or of the last episode, ends the session for everyone. */
fun finished(
    s: Session,
    screen: String,
): Session = s.copy(playing = false, by = screen, ended = true)
