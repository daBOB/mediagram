package data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.together.Session
import model.together.writeSession
import uniffi.mediagram_core.TogetherEdit

/** A screen's move, applied to whatever session is newest when it is sent. Each is idempotent. */
internal typealias Move = (Session) -> Session

/**
 * The one sender of this device's watch-together edits (`docs/mlib-spec.md`
 * §7b), the twin of the web's `sender.ts`. It keeps the screens' moves per
 * message, composed in the order they were made, and sends them one edit at
 * a time, each applied to the newest session known when it goes.
 *
 * Not thread-safe on purpose: [DefaultTogetherLive] touches it only on its
 * one serial dispatcher, which [scope] runs on, so its state changes only
 * where it suspends, as the web's does on its one thread.
 */
internal class TogetherSender(
    private val channel: TogetherChannel,
    private val scope: CoroutineScope,
    /** The session is over: its message deleted, or found gone. */
    private val forget: (id: Int) -> Unit,
) {
    /** The newest session known per message: heard, read, or on its way from here. */
    val known = mutableMapOf<Int, Session>()

    /** The moves per message not yet sent, composed in the order they were made. */
    private val outgoing = linkedMapOf<Int, Move>()

    /** The edit on its way: what it says is the newest known until it resolves. */
    private var flying: Pair<Int, Session>? = null
    private var sending: Job? = null

    /** Whether any edit is queued or on its way. */
    val busy: Boolean get() = sending?.isActive == true

    /** Whether a move for this message is waiting to go. */
    fun queued(id: Int): Boolean = id in outgoing

    /** Whether a move for this message is waiting to go or on its way. */
    fun pending(id: Int): Boolean = id in outgoing || flying?.first == id

    /** The session this message's edit on its way says, if one is. */
    fun inFlight(id: Int): Session? = flying?.takeIf { it.first == id }?.second

    /** What this device holds the session to be: the newest known, with its moves not yet sent. */
    fun view(id: Int): Session? {
        val base = known[id] ?: return null
        val move = outgoing[id]
        return if (move != null && !base.ended) move(base) else base
    }

    fun queue(
        id: Int,
        move: Move,
    ) {
        val before = outgoing[id]
        outgoing[id] = if (before == null) move else { s -> move(before(s)) }
        if (!busy) sending = scope.launch { drain() }
    }

    private suspend fun drain() {
        while (true) {
            val (id, move) = outgoing.entries.firstOrNull() ?: return
            outgoing.remove(id)
            val base = known[id]
            // Over meanwhile: the move is dropped.
            if (base != null && !base.ended) send(id, move, base)
        }
    }

    private suspend fun send(
        id: Int,
        move: Move,
        base: Session,
    ) {
        val session = move(base)
        known[id] = session
        flying = id to session
        val outcome =
            try {
                orDefault(null, "a watch-together edit") { channel.edit(id, writeSession(session)) }
            } finally {
                // A call cancelled under it is no longer on its way either.
                flying = null
            }
        // Refused or failed: the channel still holds what it held, unless another screen's edit came meanwhile.
        if (outcome != TogetherEdit.Ok && known[id] === session) known[id] = base
        when {
            outcome == TogetherEdit.Gone -> forget(id)
            outcome is TogetherEdit.Wait -> {
                // Flood-limited: the move goes again once allowed, ahead of any made since,
                // onto whatever is newest by then.
                val later = outgoing[id]
                outgoing[id] = if (later == null) move else { s -> later(move(s)) }
                delay(outcome.seconds.toLong() * MILLIS_PER_SECOND)
            }
            // Ending and then deleting the message is this sender's alone,
            // so nothing deletes it before the end is written.
            outcome == TogetherEdit.Ok && session.ended && known[id]?.ended == true -> {
                orDefault(Unit, "deleting a watch-together session") { channel.delete(id) }
                forget(id)
            }
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
