package model.together

/*
 * Casting to a TV: the handshake (offer, then the TV's answer), the TV's track
 * list, and how a cast ends. Ported line for line from
 * `web/public/lib/together/cast.js`; `web/test/fixtures/together/cast.json` holds
 * both to the same cases. A cast is an ordinary session with a remote in it.
 */

/** How long a caster waits for the TV to answer an offer. */
const val CAST_ANSWER_MS = 5_000L

/** Lists and labels are cut so a full cast fits in one Telegram message. */
const val AUDIO_TRACKS_MAX = 8
const val TEXT_TRACKS_MAX = 24
const val TRACK_LABEL_MAX = 32

/** What the TV knows when an offer reaches it: what it is playing, why it would refuse, and the household it joined. */
data class TvState(
    val screen: String,
    val busy: String?,
    val refusal: String?,
    val household: String?,
)

sealed interface TvAnswer {
    data object Ignore : TvAnswer

    data class Refuse(
        val note: String,
    ) : TvAnswer

    data class Busy(
        val note: String,
    ) : TvAnswer

    data object Accept : TvAnswer
}

enum class Ending { FINISHED, STOPPED }

fun offered(
    id: String,
    me: Member,
    own: Playback,
    profile: String,
    household: String,
    now: Long,
): Session =
    Session(
        session = id,
        set = own.set,
        playing = own.playing,
        pos = toMillis(own.pos),
        rate = own.rate,
        at = now,
        by = me.screen,
        members = listOf(me.copy(remote = true)),
        ended = false,
        cast = Cast(from = me.label, profile = profile, household = household, state = CastState.OFFER),
    )

/**
 * Whether [s] is a cast for another household than [household] (`null`, a
 * device that has joined none, has none of its own): that household's TV, not
 * this device's to answer, join or steer.
 */
fun castElsewhere(
    s: Session,
    household: String?,
): Boolean = s.cast != null && s.cast.household != household

/**
 * Refusing wins over busy: a TV that will not play it has nothing to replace.
 * Another household's TV says nothing at all, not no: its refusal would
 * overwrite the answer of the TV the cast is for.
 */
fun answerOf(
    s: Session,
    tv: TvState,
    now: Long,
): TvAnswer {
    val cast = s.cast
    if (cast == null || !isRequest(s) || castElsewhere(s, tv.household) || s.ended || cast.state == CastState.BUSY || cast.state == CastState.REFUSED || !isLive(s, null, now)) {
        return TvAnswer.Ignore
    }
    if (tv.refusal != null) return TvAnswer.Refuse(tv.refusal)
    if (cast.state == CastState.OFFER && tv.busy != null) return TvAnswer.Busy(tv.busy)
    return TvAnswer.Accept
}

/** `pos` and `at` stay the offer's, so the request goes stale a minute after it was made. */
private fun answered(
    s: Session,
    state: CastState,
    tv: String,
    note: String,
): Session = s.copy(by = tv, cast = s.cast!!.copy(state = state, note = note))

fun busied(
    s: Session,
    tv: String,
    note: String,
): Session = answered(s, CastState.BUSY, tv, note)

fun refusedBy(
    s: Session,
    tv: String,
    note: String,
): Session = answered(s, CastState.REFUSED, tv, note)

fun replaced(
    s: Session,
    me: String,
    own: Playback,
    now: Long,
): Session =
    s.copy(set = own.set, playing = own.playing, pos = toMillis(own.pos), rate = own.rate, at = now, by = me, cast = s.cast!!.copy(state = CastState.REPLACE, note = ""))

/** The first TV to write wins; a later one finds `tv` set and leaves it. */
fun accepted(
    s: Session,
    me: Member,
    now: Long,
): Session? {
    val cast = s.cast!!
    if (cast.tv != "") return null
    return joined(s, me.copy(remote = false)).copy(
        playing = false,
        pos = toMillis(expectedPosition(s, now)),
        at = now,
        cast = cast.copy(tv = me.screen, state = CastState.ON, note = ""),
    )
}

private fun capped(
    list: List<Track>,
    max: Int,
) = list.take(max).map { Track(it.id, it.label.trim().take(TRACK_LABEL_MAX).trim()) }

fun tracked(
    s: Session,
    tv: String,
    tracks: CastTracks,
    audio: String,
    text: String,
): Session =
    s.copy(
        by = tv,
        cast = s.cast!!.copy(tracks = CastTracks(capped(tracks.audio, AUDIO_TRACKS_MAX), capped(tracks.text, TEXT_TRACKS_MAX)), audio = audio, text = text),
    )

fun chose(
    s: Session,
    screen: String,
    audio: String? = null,
    text: String? = null,
): Session = s.copy(by = screen, cast = s.cast!!.copy(audio = audio ?: s.cast.audio, text = text ?: s.cast.text))

/** How a cast ended for [me]: the TV reached the end, or someone or something stopped it. */
fun endingOf(
    s: Session,
    me: String,
): Ending? {
    val cast = s.cast
    if (!s.ended || cast == null || s.by == me) return null
    return if (s.by == cast.tv && s.members.any { it.screen == cast.tv }) Ending.FINISHED else Ending.STOPPED
}
