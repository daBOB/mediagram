package data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import model.together.Session
import model.together.writeSession

/** Compared as written: what reaches the channel is the text. */
internal fun same(
    a: Session,
    b: Session,
): Boolean = writeSession(a) == writeSession(b)

/**
 * What this device holds each watch-together session to be, and what it
 * shows of them: the web hub's state (`hub.ts`) and a tab's view
 * (`together-now.js`) in one. The newest known per message, with the moves
 * of this device's screens still to go, is [sender]'s; nothing here suspends.
 *
 * Touched only on [DefaultTogetherLive]'s serial dispatcher, which [scope]
 * runs on.
 */
internal class TogetherSessions(
    channel: TogetherChannel,
    scope: CoroutineScope,
) {
    val sender =
        TogetherSender(channel, scope, forget = { forget(it) }, lost = { lost(it) }, reached = { reachable.value = it })
    val known: MutableMap<Int, Session> get() = sender.known

    /** Screens whose moves went through here: their echoes are not news. */
    val local = mutableSetOf<String>()

    /** [TogetherLive.live]. */
    val shown = MutableStateFlow<LiveSession?>(null)

    /** [TogetherLive.available]. */
    val reachable = MutableStateFlow(false)

    fun view(id: Int): Session? = sender.view(id)

    fun pending(id: Int): Boolean = sender.pending(id)

    /** Queues [screen]'s move unless the session is over here; answers the session as now held. */
    fun queue(
        id: Int,
        screen: String,
        move: Move,
    ): Session? {
        sender.view(id)?.takeUnless { it.ended } ?: return null
        local += screen
        sender.queue(id, move)
        return tell(id)
    }

    /**
     * An edit heard or re-read, [live] as its title's length has it. Another
     * screen's is always kept: moves still waiting are applied to it. This
     * device's own echo is old news while a newer move waits, or when it is
     * not the edit on its way. One that is over, ended or stale, is over here
     * too, unless an own move is still to go: ending and deleting it is then
     * the sender's.
     */
    fun take(
        id: Int,
        session: Session,
        live: Boolean,
    ) {
        if (session.by in local) {
            if (sender.queued(id)) return
            val flying = sender.inFlight(id)
            if (flying != null && !same(flying, session)) return
        }
        if (live || sender.pending(id)) keep(id, session) else forget(id)
    }

    fun keep(
        id: Int,
        session: Session,
    ) {
        known[id] = session
        tell(id)
    }

    /** A move given up on: what the channel holds is shown again, marked so the screen that moved takes it too. */
    private fun lost(id: Int) {
        if (id in known) tell(id, lost = true)
    }

    /** The session is over here: forgotten, and out of [shown]. */
    fun forget(id: Int): LiveSession? {
        known.remove(id)
        tell(id)
        return null
    }

    /**
     * Shows what this device holds session [id] to be; nothing once it is
     * over. Another session heard while a screen here is in the one shown
     * does not take its place, as it does not in a web tab: that screen
     * follows its own until a read answers the newest.
     */
    private fun tell(
        id: Int,
        lost: Boolean = false,
    ): Session? {
        val session = sender.view(id)?.takeUnless { it.ended }
        val current = shown.value
        shown.value =
            when {
                current?.id == id -> session?.let { LiveSession(id, it, lost) }
                session == null -> current
                current != null && mine(current.session) && !mine(session) -> current
                else -> LiveSession(id, session, lost)
            }
        return session
    }

    private fun mine(session: Session): Boolean = session.members.any { it.screen in local }
}
