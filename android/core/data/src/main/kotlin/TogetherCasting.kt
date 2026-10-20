package data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import model.together.CAST_ANSWER_MS
import model.together.CastState
import model.together.CastTracks
import model.together.Member
import model.together.Playback
import model.together.accepted
import model.together.busied
import model.together.chose
import model.together.isLive
import model.together.isRequest
import model.together.offered
import model.together.parseSession
import model.together.refusedBy
import model.together.replaced
import model.together.tracked
import model.together.writeSession
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.ContinuationInterceptor

private val NONE = CastAnswer(CastAnswer.Answer.NONE, null, "")
private val ASKING = setOf(CastState.OFFER, CastState.REPLACE)

/**
 * [TogetherCasts] for [DefaultTogetherLive], the twin of the web's
 * `cast-desk.ts`: an offer goes out as a new session message, and the TV
 * answers by editing it. The answer is waited for on what [sessions] keep,
 * never on `channel.heard()`, which a second collector would steal pushes from.
 *
 * Touches [sessions] only on [work]'s dispatcher, [DefaultTogetherLive]'s
 * serial one.
 */
internal class TogetherCasting(
    private val channel: TogetherChannel,
    private val sessions: TogetherSessions,
    private val reads: TogetherReads,
    private val work: CoroutineScope,
    private val now: () -> Long,
) : TogetherCasts {
    private val serial = work.coroutineContext[ContinuationInterceptor]!!
    private val castNext = AtomicReference<ArmedCast?>(null)

    override suspend fun offer(
        me: Member,
        own: Playback,
        profile: String,
        household: String,
    ): CastAnswer =
        withContext(serial) {
            val offer = offered(UUID.randomUUID().toString(), me, own.sane(), profile, household, now())
            waitFor {
                // Once on its way, the message is this device's to answer for, whatever becomes of the caller.
                withContext(NonCancellable) {
                    val id = channel.send(writeSession(offer))
                    sessions.local += me.screen
                    // The TV may have answered before the send returned: that answer is newer, and stays.
                    if (id !in sessions.known) sessions.keep(id, offer)
                    id
                }
            }
        }

    override suspend fun replace(
        id: Int,
        me: Member,
        own: Playback,
        profile: String,
        household: String,
    ): CastAnswer =
        withContext(serial) {
            val read = channel.read(id)?.let(::parseSession)
            val playback = own.sane()
            val at = now()
            val asking = read != null && isRequest(read) && isLive(read, null, at)
            // Nothing is moved for a message no longer known here, as after a restart: it is offered anew too.
            if (!asking || sessions.queue(id, me.screen) { replaced(it, me.screen, playback, at) } == null) {
                return@withContext offer(me, own, profile, household)
            }
            // The edit goes from this dispatcher only once this suspends, so its answer is listened for first.
            waitFor { id }
        }

    override fun cancel(id: Int) {
        work.launch { withdraw(id) }
    }

    override fun refuse(
        id: Int,
        by: String,
        note: String,
    ) {
        work.launch { sessions.queue(id, by) { refusedBy(it, by, note) } }
    }

    override fun busy(
        id: Int,
        by: String,
        note: String,
    ) {
        work.launch { sessions.queue(id, by) { busied(it, by, note) } }
    }

    override suspend fun accept(
        id: Int,
        me: Member,
    ): LiveSession? =
        withContext(serial) {
            val before = sessions.known[id]
            val read = channel.read(id)?.let(::parseSession) ?: return@withContext sessions.forget(id)
            // A move made meanwhile, or an edit heard, knows newer than this read.
            if (!sessions.pending(id) && sessions.known[id] === before) sessions.known[id] = read
            // Only a live offer or replace is a TV's to take: one another TV answered or took keeps its answer.
            sessions.view(id)?.takeIf { it.cast?.state in ASKING && isLive(it, null, now()) } ?: return@withContext null
            // The TV named in `tv` keeps the cast, whoever else's accept reaches the sender first.
            val held = sessions.queue(id, me.screen) { accepted(it, me, now()) ?: it }
            held?.takeIf { it.cast?.tv == me.screen }?.let { LiveSession(id, it) }
        }

    override fun tracks(
        id: Int,
        screen: String,
        tracks: CastTracks,
        audio: String,
        text: String,
    ) {
        work.launch { sessions.queue(id, screen) { tracked(it, screen, tracks, audio, text) } }
    }

    override fun choose(
        id: Int,
        screen: String,
        audio: String?,
        text: String?,
    ) {
        work.launch { sessions.queue(id, screen) { chose(it, screen, audio, text) } }
    }

    override fun castOnOpen(cast: ArmedCast) = castNext.set(cast)

    override fun takeCast(): ArmedCast? = castNext.getAndSet(null)

    /** The first answer kept for the message [send] names, or, none kept within `CAST_ANSWER_MS`, the [deadline]'s. */
    private suspend fun waitFor(send: suspend () -> Int): CastAnswer =
        coroutineScope {
            val heard = Channel<LiveSession>(Channel.UNLIMITED)
            // Subscribed before anything is sent: what is kept is not replayed, and the TV may answer at once.
            val listening =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    // Collecting what is shown is what keeps listening for edits meanwhile.
                    launch { sessions.shown.collect {} }
                    sessions.kept.collect { heard.send(it) }
                }
            try {
                val id = send()
                withTimeoutOrNull(CAST_ANSWER_MS) {
                    heard.receiveAsFlow().mapNotNull { if (it.id == id) answered(id, it.session) else null }.first()
                } ?: deadline(id)
            } finally {
                listening.cancel()
            }
        }

    /**
     * No answer heard in time: the one the channel holds by then, since a push
     * can be missed, kept as if heard, so the remote's next move builds on
     * the TV's answer and not on the offer. Still unanswered, or unreadable,
     * the offer is withdrawn, so no TV takes it after the caster was told it failed.
     */
    private suspend fun deadline(id: Int): CastAnswer {
        val before = sessions.known[id]
        val read = orDefault(null, "reading a cast offer") { channel.read(id) }?.let(::parseSession)
        val live = read != null && isLive(read, reads.lengthOf(read.set), now())
        // An answer heard while reading is newer than the read.
        sessions.known[id]?.takeIf { it !== before }?.let { answered(id, it) }?.let { return it }
        if (read == null) return withdraw(id)
        val answer = answered(id, read) ?: return withdraw(id)
        sessions.take(id, read, live)
        return answer
    }

    /** Deletes the offer, and forgets it here even when the delete fails. */
    private suspend fun withdraw(id: Int): CastAnswer {
        orDefault(Unit, "withdrawing a cast offer") { channel.delete(id) }
        sessions.forget(id)
        return NONE
    }
}
