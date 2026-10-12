package data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.together.Session
import model.together.writeSession
import uniffi.mediagram_core.TogetherEdit

/** A screen's move, applied to whatever session is newest when it is sent. Each is idempotent. */
internal typealias Move = (Session) -> Session

/** How long a move whose edit threw waits before it is tried again. */
internal const val EDIT_RETRY_MS = 30_000L

/** How many times a move's edit may throw before the move is given up on, so a lasting error is not tried for ever. */
internal const val EDIT_TRIES = 3

private const val TAG = "together"

/**
 * The one sender of this device's watch-together edits (`docs/mlib-spec.md`
 * §7b), the twin of the web's `sender.ts`. It keeps the screens' moves per
 * message, composed in the order they were made, and sends them one edit at
 * a time, each applied to the newest session known when it goes.
 *
 * An edit that throws, as one does while Telegram can't be reached, is tried
 * again after [EDIT_RETRY_MS], never sooner, until it has thrown [EDIT_TRIES]
 * times. The move is then given up on, and the screens are shown what the
 * channel holds.
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
    /** A move was given up on: the screens follow what the channel holds again, the one that moved included. */
    private val lost: (id: Int) -> Unit,
    /** Whether the last edit was answered, or threw: Telegram reached or not. */
    private val reached: (Boolean) -> Unit,
) {
    /** The newest session known per message: heard, read, or on its way from here. */
    val known = mutableMapOf<Int, Session>()

    /** The moves per message not yet sent, composed in the order they were made. */
    private val outgoing = linkedMapOf<Int, Move>()

    /** The edit on its way: what it says is the newest known until it resolves. */
    private var flying: Pair<Int, Session>? = null
    private var sending: Job? = null

    /** How many times in a row each message's edit has thrown. */
    private val failures = mutableMapOf<Int, Int>()

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
        reached(outcome != null)
        if (outcome == null) return failed(id, move)
        failures.remove(id)
        when {
            outcome == TogetherEdit.Gone -> forget(id)
            outcome is TogetherEdit.Wait -> {
                // Flood-limited: the move goes again once allowed.
                again(id, move)
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

    /** A thrown edit goes again after the retry wait, or, the last time, is given up on. Nothing is tried sooner either way. */
    private suspend fun failed(
        id: Int,
        move: Move,
    ) {
        val tries = (failures[id] ?: 0) + 1
        if (tries < EDIT_TRIES) {
            failures[id] = tries
            again(id, move)
        } else {
            failures.remove(id)
            Log.w(TAG, "a watch-together move was given up after $EDIT_TRIES failed edits")
            lost(id)
        }
        delay(EDIT_RETRY_MS)
    }

    /** The move goes again, ahead of any made since, onto whatever is newest by then. */
    private fun again(
        id: Int,
        move: Move,
    ) {
        val later = outgoing[id]
        outgoing[id] = if (later == null) move else { s -> later(move(s)) }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
