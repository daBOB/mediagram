package data

import kotlinx.coroutines.flow.StateFlow
import model.together.Member
import model.together.Playback
import model.together.PlaybackChange
import model.together.Session

/** A session and the channel message that carries it. */
data class LiveSession(
    val id: Int,
    val session: Session,
)

/**
 * What a start found: the session it began, or, with [conflict], the one
 * already live, which the start becomes a join of.
 */
data class Started(
    val live: LiveSession,
    val conflict: Boolean,
)

/**
 * Watch together on this device (`docs/mlib-spec.md` §7b): the newest live
 * session, whether Telegram can be reached, and the moves this device's
 * screens make. Home, the title pages and the player read it; one instance
 * serves the whole process, so a leave outlives the player that asked.
 *
 * A screen is one player instance, named by the random id it passes as
 * `me`/`screen`; its own edits come back from Telegram and are recognised
 * by that id.
 */
interface TogetherLive {
    /**
     * The newest live session, as this device holds it: its own moves still
     * to go included, and `null` once it is over. Collecting it is what
     * listens for edits and re-reads a session a screen here is in every
     * 30 s, so collect it only while a screen that shows it is on screen.
     */
    val live: StateFlow<LiveSession?>

    /** False while Telegram can't be reached, nobody is signed in, or the last read failed. */
    val available: StateFlow<Boolean>

    /**
     * Reads the newest messages again, deleting any stale session found, and
     * answers [live] afterwards. A failed read leaves [live] as it was and
     * [available] false.
     */
    suspend fun refresh(): LiveSession?

    /**
     * Starts a session with [me] its only member, unless one is already live:
     * then that one is answered as a [Started.conflict]. One start at a time,
     * so a second screen's start meets the first one's session. Throws when
     * Telegram can't be reached.
     */
    suspend fun start(
        me: Member,
        own: Playback,
    ): Started

    /** Adds [me] to session [id]; `null` when it is over or gone. Throws when it can't be read. */
    suspend fun join(
        id: Int,
        me: Member,
    ): LiveSession?

    /** The viewer's own change, sent to session [id]; the session as now held, or `null` when it is over here. */
    suspend fun act(
        id: Int,
        screen: String,
        change: PlaybackChange,
    ): Session?

    /** Takes [screen] out of session [id]; the last to go ends and deletes it. Sent even after the caller is gone. */
    fun leave(
        id: Int,
        screen: String,
    )

    /** The end of the last title: over for everyone still in session [id]. Sent even after the caller is gone. */
    fun end(
        id: Int,
        screen: String,
    )

    /** Asks the player that opens next to join session [id]: Home's or a title page's Join. */
    fun joinOnOpen(id: Int)

    /** The session [joinOnOpen] asked for, once; `null` after. */
    fun takeJoin(): Int?
}
