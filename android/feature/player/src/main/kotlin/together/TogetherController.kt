package player.together

import data.ArmedCast
import data.TogetherLive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * Starting, joining and taking a cast are in `TogetherJoining.kt`,
 * following in `TogetherFollowing.kt`; the state they share is this class's.
 */
internal class TogetherController(
    val live: TogetherLive,
    handle: PlayerHandle,
    /** Opens a title the session moved to, as Next does; the second is called when the daily limit refuses it. */
    val openTitle: (String, onRefused: () -> Unit) -> Unit,
    /** A title in this profile's catalog, or `null`: a kids profile does not see some. */
    val titleOf: suspend (String) -> MediaSet?,
    /** What this screen is called in a session: TV, Tablet or Phone. */
    private val label: () -> String,
    val scope: CoroutineScope,
    val now: () -> Long = System::currentTimeMillis,
) {
    val screen = UUID.randomUUID().toString()
    val steps = TogetherSteps(handle, now)
    val view = TogetherView(scope, screen)
    val ui: StateFlow<TogetherUi> = view.ui

    /** Words over the picture for [holdMs], through the pill watch together's own notices use. */
    fun tell(text: String, holdMs: Long) = view.show(text, holdMs)
    val me get() = Member(screen, label())
    var setId: String? = null

    /** The session this screen is in, and that session as this screen follows it, also as [following] for what watches it. */
    var id: Int? = null
    var current: Session? = null
        set(value) {
            field = value
            currentFlow.value = value
        }
    private val currentFlow = MutableStateFlow<Session?>(null)
    val following: StateFlow<Session?> = currentFlow.asStateFlow()

    /** Set once a cast this screen plays as the TV is over, or was never taken: the player closes then. */
    val castEnded = MutableStateFlow(false)
    val castOver: StateFlow<Boolean> = castEnded.asStateFlow()

    /** A cast offered playing plays once it is taken and its first frame is on screen ([firstFrame]), whichever comes last. */
    var playAtFrame = false
    var framed = false

    /** A title opened because the session moved to it: opening it is not the viewer's move. */
    var expecting: String? = null

    /** Counts leaving: a start or join answered after this screen left, closed or stopped waiting is too late to enter. */
    var generation = 0
    var drift: Job? = null
    private var settle: Job? = null
    var watching: Job? = null

    /** Whether the player screen is started: out of a session, it listens only then. */
    var shown = true

    /**
     * The player opens [set]. Answers where to start it, and whether to
     * play, when that is the session's place: for a title opened in a
     * session, or to join one, it is that rather than where this profile
     * left off. With [cast], the TV takes that cast here; see [takeCast].
     */
    fun open(set: String, cast: ArmedCast? = null): Playback? {
        setId = set
        val followed = expecting == set
        expecting = null
        // Taken on every open, a cast's included: a join armed for this one never waits for a later one.
        val joining = live.takeJoin()
        if (cast != null) return takeCast(cast)
        if (id != null && !followed) viewer(PlaybackChange(set = set, pos = 0.0, playing = true))
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
        castEnded.value = false
    }

    /**
     * Out of the session, if in one: whatever was waiting to go, or to be
     * answered, is dropped, and so is a title on its way because the session
     * moved there, which opened later is the viewer's own.
     */
    fun end(message: String?) {
        if (current?.cast?.tv == screen) castEnded.value = true
        // A cast's start at its first frame goes with it: a later session's frame is no reason to play.
        playAtFrame = false
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
}
