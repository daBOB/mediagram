package player.together

import data.TOGETHER_UNREACHABLE
import data.TogetherLive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import model.MediaSet
import model.together.Member
import model.together.Playback
import model.together.PlaybackChange
import model.together.Session
import model.together.acted
import model.together.expectedPosition
import player.PlayerHandle
import java.util.UUID

/** A scrub sends one edit, this long after the last movement. */
internal const val SEEK_SETTLE_MS = 300L

/**
 * Watch together in the player (`docs/mlib-spec.md` §7b), the twin of the
 * web player's `together.js`. It follows the session this screen is in and
 * sends the viewer's own moves. Only [viewer] sends, and only the viewer's
 * own paths call it ([ViewerPlayer], a speed or restart picked by hand, a
 * title opened by hand), so a change made here to follow never goes back
 * out. [TogetherLive] does the sending, which goes on after the player has
 * gone. Called on the main thread only, as the player is.
 *
 * Starting and joining are in `TogetherJoining.kt`, following in
 * `TogetherFollowing.kt`; the state they share is this class's.
 */
internal class TogetherController(
    val live: TogetherLive,
    handle: PlayerHandle,
    /** Opens a title the session moved to, as Next does. */
    val openTitle: (String) -> Unit,
    /** A title in this profile's catalog, or `null`: a kids profile does not see some. */
    val titleOf: suspend (String) -> MediaSet?,
    /** What this screen is called in a session: TV, Tablet or Phone. */
    private val label: () -> String,
    val scope: CoroutineScope,
    val now: () -> Long = System::currentTimeMillis,
) {
    val screen = UUID.randomUUID().toString()
    val steps = TogetherSteps(handle, now)
    val view = TogetherView(scope)
    val ui: StateFlow<TogetherUi> = view.ui

    /** Words over the picture for [holdMs], through the pill watch together's own notices use. */
    fun tell(text: String, holdMs: Long) = view.show(text, holdMs)
    val me get() = Member(screen, label())
    var setId: String? = null

    /** The session this screen is in, and that session as this screen follows it. */
    var id: Int? = null
    var current: Session? = null

    /** A title opened because the session moved to it: opening it is not the viewer's move. */
    var expecting: String? = null

    /** Counts leaving: a start or join answered after this screen left, closed or stopped waiting is too late to enter. */
    var generation = 0
    var drift: Job? = null
    private var settle: Job? = null
    private var watching: Job? = null

    /** Whether the player screen is started: out of a session, it listens only then. */
    private var shown = true

    /**
     * The player opens [set]. Answers where to start it, and whether to
     * play, when that is the session's place: for a title opened in a
     * session, or to join one, it is that rather than where this profile
     * left off.
     */
    fun open(set: String): Playback? {
        setId = set
        val followed = expecting == set
        expecting = null
        if (id != null && !followed) viewer(PlaybackChange(set = set, pos = 0.0, playing = true))
        val joining = live.takeJoin()
        if (joining != null && id == null) scope.launch { joinHere(joining) }
        listen()
        val session = if (id != null) current else live.live.value?.takeIf { it.id == joining }?.session
        return session?.takeIf { it.set == set }?.let { Playback(set, it.playing, expectedPosition(it, now()), it.rate) }
    }

    /** The Watch together button: leave the session this screen is in, join the one live, or start one. */
    fun onButton() {
        if (id != null) return leave()
        val found = live.live.value
        scope.launch { if (found != null) joinLive(found) else start() }
    }

    /**
     * The viewer's own move. A seek waits [SEEK_SETTLE_MS] for a scrub to
     * stop, so a drag sends one edit; anything else goes at once, and carries
     * the position, a seek still settling included.
     */
    fun viewer(change: PlaybackChange) {
        val session = id ?: return
        val base = current ?: return
        val sane = change.copy(pos = change.pos?.takeIf { it.isFinite() }?.coerceAtLeast(0.0))
        val moved = acted(base, sane, screen, now())
        current = moved
        settle?.cancel()
        val seeking = sane == PlaybackChange(pos = sane.pos)
        val sending = scope.launch {
            if (seeking) delay(SEEK_SETTLE_MS)
            send(session, sane.copy(pos = expectedPosition(moved, now())))
        }
        settle = sending.takeIf { seeking }
    }

    /** The end of a film, or of the last episode: over for everyone. With a title next, opening it moves the session instead. */
    fun onTitleEnded(hasNext: Boolean) {
        val ending = id ?: return
        if (hasNext) return
        end(null)
        live.end(ending, screen)
    }

    /** Leaves the session this screen is in, if any, saying [message]; the leave is sent even once the player has gone. */
    fun leave(message: String? = null) {
        val leaving = id
        end(message)
        if (leaving != null) live.leave(leaving, screen)
    }

    /** The player screen started or stopped. In a session it keeps following in the background, as audio and picture-in-picture do. */
    fun screenShown(shown: Boolean) {
        this.shown = shown
        listen()
    }

    /** The player closing: this screen leaves, and stops listening. */
    fun close() {
        setId = null
        leave()
    }

    /**
     * Out of the session, if in one: whatever was waiting to go, or to be
     * answered, is dropped, and so is a title on its way because the session
     * moved there, which opened later is the viewer's own.
     */
    fun end(message: String?) {
        message?.let(view::show)
        generation++
        id = null
        current = null
        expecting = null
        drift?.cancel()
        drift = null
        settle?.cancel()
        settle = null
        scope.launch { draw() }
        listen()
    }

    /**
     * Follows [TogetherLive] while it is needed, collecting it being what
     * holds the core's push stream open: in a session, or with the player
     * open on a screen that is started. Taken up out of a session, it reads
     * the channel first, since nothing was heard while it was let go.
     */
    fun listen() {
        if (setId == null || !(shown || id != null)) {
            watching?.cancel()
            watching = null
            return
        }
        if (watching != null) return
        watching = scope.launch {
            launch {
                live.available.collect { up ->
                    // A read or an edit just failed: a screen in a session plays on alone, and says why.
                    if (!up && id != null) view.show(TOGETHER_UNREACHABLE)
                    draw()
                }
            }
            live.live.collect { onLive(it) }
        }
        if (id == null) scope.launch { live.refresh() }
    }
}
