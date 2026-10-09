package data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import model.together.isLive
import model.together.parseSession
import uniffi.mediagram_core.TogetherMessage

private const val TAG = "together"

/** How often the sessions a screen here is in are read again, and the soonest a dropped listener is taken up again. */
private const val RECHECK_MS = 30_000L

/**
 * Reading the channel into [sessions]: finding the live session, hearing
 * pushes, and re-reading what a push may have missed. Each read is weighed
 * against what changed here while it was on its way, since that is newer,
 * and nothing suspends between that weighing and acting on it.
 *
 * Runs on [DefaultTogetherLive]'s serial dispatcher, which [scope] runs on.
 */
internal class TogetherReads(
    private val channel: TogetherChannel,
    private val sessions: TogetherSessions,
    private val scope: CoroutineScope,
    private val durationOf: suspend (setId: String) -> Double?,
    private val now: () -> Long,
) {
    private var lastHeard: LiveSession? = null

    /**
     * The newest live session: in the newest messages, or the newest one
     * known here, read by id when a busy channel has pushed it out of them.
     * Stale ones found on the way are deleted, as `docs/mlib-spec.md` §7b asks
     * of whoever reads them. A session heard, or moved here, while reading is
     * newer than the read, so it counts as found. Throws when the channel
     * can't be read.
     */
    suspend fun discover(): LiveSession? {
        val known = sessions.known
        val before = known.toMap()
        val found = channel.recent().associateTo(LinkedHashMap()) { it.messageId to parseSession(it.text) }
        val remembered = known.keys.maxOrNull()
        if (remembered != null && remembered !in found) {
            val session = channel.read(remembered)?.let(::parseSession)
            if (session != null) found[remembered] = session else sessions.forget(remembered)
        }
        val sets = found.values.mapNotNull { it?.set } + known.keys.mapNotNull { sessions.view(it)?.set }
        val lengths = sets.toSet().associateWith { lengthOf(it) }
        var newest: LiveSession? = null
        for (id in found.keys + known.keys.filter { known[it] !== before[it] }) {
            val pending = sessions.pending(id)
            val session =
                when {
                    pending -> sessions.view(id)
                    // An edit heard, or an own move landed, while reading: newer than the read.
                    known[id] !== before[id] -> known[id]
                    else -> found[id]
                } ?: continue
            if (!isLive(session, lengths[session.set], now())) {
                // With an own move still to go, ending and deleting it is the sender's job.
                if (!pending) {
                    sessions.forget(id)
                    scope.launch { orDefault(Unit, "deleting a stale session") { channel.delete(id) } }
                }
                continue
            }
            if (!pending) known[id] = session
            if (newest == null || id > newest.id) newest = LiveSession(id, session)
        }
        return newest
    }

    /**
     * Hears pushes and re-reads every 30 s, for as long as it is collected.
     * A listener that fails is taken up again, never sooner than a re-read.
     */
    suspend fun listen(): Unit =
        coroutineScope {
            launch {
                channel
                    .heard()
                    .retryWhen { failure, _ ->
                        if (failure is CancellationException || failure !is Exception) return@retryWhen false
                        Log.w(TAG, "stopped hearing edits", failure)
                        delay(RECHECK_MS)
                        true
                    }.collect { hear(it) }
            }
            while (true) {
                delay(RECHECK_MS)
                recheck()
            }
        }

    /** A push. The core keeps only the newest message heard, so two pushes can read the same one: it is news once. */
    private suspend fun hear(message: TogetherMessage) {
        sessions.reachable.value = true
        val heard = LiveSession(message.messageId, parseSession(message.text) ?: return)
        if (heard == lastHeard) return
        lastHeard = heard
        // The length first: the echo rule then checks and acts with nothing suspending in between.
        val live = isLive(heard.session, lengthOf(heard.session.set), now())
        sessions.take(heard.id, heard.session, live)
    }

    /**
     * Re-reads every session a screen here is in: a push missed while the
     * connection was down is not replayed. A read across which an own move
     * started or landed, or an edit was heard, may predate it, so it is set
     * aside; one that can't be made is tried again on the next tick.
     */
    private suspend fun recheck() {
        if (sessions.sender.busy) return
        for ((id, session) in sessions.known.toList()) {
            if (sessions.pending(id) || session.members.none { it.screen in sessions.local }) continue
            val text =
                try {
                    channel.read(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    continue
                }
            val read = text?.let(::parseSession)
            // Looked up before the check below, so nothing can change between that check and acting on it.
            val live = read != null && isLive(read, lengthOf(read.set), now())
            if (sessions.pending(id) || sessions.known[id] !== session) continue
            sessions.reachable.value = true
            if (read == null) sessions.forget(id) else if (!same(read, session)) sessions.take(id, read, live)
        }
    }

    suspend fun lengthOf(set: String): Double? = orDefault(null, "the length of $set") { durationOf(set) }
}
