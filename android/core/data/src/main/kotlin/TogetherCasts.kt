package data

import model.together.CastState
import model.together.CastTracks
import model.together.Member
import model.together.Playback
import model.together.Session

/**
 * The TV's answer to an offer. Every answer but [Answer.NONE] carries the
 * offer's own message, which [TogetherCasts.replace] and
 * [TogetherCasts.cancel] act on; [note] is a busy TV's title or a refusal's
 * reason, and empty otherwise.
 */
data class CastAnswer(
    val answer: Answer,
    val live: LiveSession?,
    val note: String,
) {
    /** [NONE]: no TV answered in time, and the offer was withdrawn. */
    enum class Answer { ON, BUSY, REFUSED, NONE }
}

/** The TV's answer session [s] of message [id] holds; `null` while no TV has answered it. */
internal fun answered(
    id: Int,
    s: Session,
): CastAnswer? {
    val cast = s.cast ?: return null
    val answer =
        when (cast.state) {
            CastState.ON -> CastAnswer.Answer.ON
            CastState.BUSY -> CastAnswer.Answer.BUSY
            CastState.REFUSED -> CastAnswer.Answer.REFUSED
            CastState.OFFER, CastState.REPLACE -> return null
        }
    return CastAnswer(answer, LiveSession(id, s), cast.note)
}

/** A cast the TV accepted, for the player that opens next to play: request [id], title [set], [from] whom. */
data class ArmedCast(
    val id: Int,
    val set: String,
    val profile: String,
    val from: String,
    val playing: Boolean,
)

/**
 * Casting to a TV, as part of [TogetherLive]: a phone or tablet offers what
 * it plays and waits for the TV's answer; the TV answers and writes its
 * track list; a remote picks a track. A cast is an ordinary session with a
 * remote in it, so the remote's other moves are [TogetherLive]'s own.
 */
interface TogetherCasts {
    /**
     * Sends an offer of [own], with [me] as its remote, and waits up to
     * `CAST_ANSWER_MS` for a TV's answer. None heard by then, the message is
     * read once; still unanswered, or unreadable, the offer is withdrawn and
     * the answer is [CastAnswer.Answer.NONE]. Throws when the offer can't be
     * sent. Once sent, the offer is this device's to answer or withdraw only
     * while the caller waits, so a screen that can close mid-call calls this
     * as it does [TogetherLive.start].
     */
    suspend fun offer(
        me: Member,
        own: Playback,
        profile: String,
    ): CastAnswer

    /**
     * After a busy answer: asks the TV holding request [id] to switch to
     * [own], and waits as [offer] does. A request no longer live, or no longer
     * known here, is offered anew instead. Throws when it can't be read.
     */
    suspend fun replace(
        id: Int,
        me: Member,
        own: Playback,
        profile: String,
    ): CastAnswer

    /** Withdraws offer [id]: its message is deleted. Sent even after the caller is gone. */
    fun cancel(id: Int)

    /** The TV [by] will not play request [id], for [note]. Sent even after the caller is gone. */
    fun refuse(
        id: Int,
        by: String,
        note: String,
    )

    /** The TV [by] is playing [note] and asks before replacing it. Sent even after the caller is gone. */
    fun busy(
        id: Int,
        by: String,
        note: String,
    )

    /**
     * The TV [me] takes request [id] and joins it as a player; the session as
     * now held, or `null` when the message is gone, is no longer a live
     * request, or another TV took it first. Called as [TogetherLive.join] is.
     */
    suspend fun accept(
        id: Int,
        me: Member,
    ): LiveSession?

    /** The TV's tracks, and those it plays, written to cast [id]; long lists are cut to fit one message. */
    fun tracks(
        id: Int,
        screen: String,
        tracks: CastTracks,
        audio: String,
        text: String,
    )

    /** A remote's track choice for cast [id]; a `null` track stays as it is. */
    fun choose(
        id: Int,
        screen: String,
        audio: String? = null,
        text: String? = null,
    )

    /** Asks the player that opens next to play [cast]: the TV's accept. */
    fun castOnOpen(cast: ArmedCast)

    /** The cast [castOnOpen] armed, once; `null` after. */
    fun takeCast(): ArmedCast?
}
