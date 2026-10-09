package data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.together.Session
import model.together.isLive
import model.together.parseSession
import uniffi.mediagram_core.TogetherMessage

/**
 * Reading the channel into [sessions]: finding the live session, hearing
 * pushes, and re-reading what a push may have missed. Each read is weighed
 * against what changed here while it was on its way, since that is newer.
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
     * of whoever reads them. Throws when the channel can't be read.
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
        for ((id, read) in found) {
            val pending = sessions.pending(id)
            val session =
                when {
                    pending -> sessions.view(id)
                    // An edit heard, or an own move landed, while reading: newer than the read.
                    known[id] !== before[id] -> known[id]
                    else -> read
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

    /** A push. The core keeps only the newest message heard, so two pushes can read the same one: it is news once. */
    suspend fun hear(message: TogetherMessage) {
        sessions.reachable.value = true
        val heard = LiveSession(message.messageId, parseSession(message.text) ?: return)
        if (heard == lastHeard) return
        lastHeard = heard
        take(heard.id, heard.session)
    }

    /**
     * Re-reads every session a screen here is in: a push missed while the
     * connection was down is not replayed. A read across which an own move
     * started or landed, or an edit was heard, may predate it, so it is set
     * aside; one that can't be made is tried again on the next tick.
     */
    suspend fun recheck() {
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
            if (sessions.pending(id) || sessions.known[id] !== session) continue
            sessions.reachable.value = true
            val read = text?.let(::parseSession)
            if (read == null) sessions.forget(id) else if (!same(read, session)) take(id, read)
        }
    }

    suspend fun lengthOf(set: String): Double? = orDefault(null, "the length of $set") { durationOf(set) }

    private suspend fun take(
        id: Int,
        session: Session,
    ) = sessions.take(id, session, isLive(session, lengthOf(session.set), now()))
}
