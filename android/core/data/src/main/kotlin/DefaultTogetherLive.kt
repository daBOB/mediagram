package data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import model.together.Member
import model.together.Playback
import model.together.PlaybackChange
import model.together.Session
import model.together.acted
import model.together.finished
import model.together.isLive
import model.together.joined
import model.together.left
import model.together.parseSession
import model.together.started
import model.together.writeSession
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.ContinuationInterceptor

private const val TAG = "together"

// What a screen writes, every screen parses: a position or speed no parser
// accepts would end the session for all of them.
private fun Double.position(): Double? = takeIf { it.isFinite() && it >= 0 }

private fun Double.speed(): Double? = takeIf { it.isFinite() && it > 0 }

/** The act move: [moved]'s own playback fields laid over whatever session is newest when it goes. */
private fun Session.playbackOf(moved: Session) =
    copy(set = moved.set, playing = moved.playing, pos = moved.pos, rate = moved.rate, at = moved.at, by = moved.by)

/**
 * [TogetherLive] for this device. Android has no server, so this plays the
 * web server hub's part (`web/src/together/hub.ts`) as well as a tab's: the
 * screens' moves reach the channel through one [TogetherSender], each
 * re-applied to the newest session known when it goes, and every edit heard
 * is kept in [TogetherSessions] and shown in [live].
 *
 * All of that state is touched on one serial dispatcher, so the hub's
 * reasoning, that state changes only where it suspends, carries over
 * unchanged from the web's single thread. [scope] is the process's
 * (`AppScope`), so a leave outlives the player that asked for it.
 */
class DefaultTogetherLive(
    private val channel: TogetherChannel,
    scope: CoroutineScope,
    /** A title's length in seconds, or `null` when unknown: liveness depends on it. */
    durationOf: suspend (setId: String) -> Double?,
    private val now: () -> Long = System::currentTimeMillis,
) : TogetherLive {
    private val serial =
        (scope.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher ?: Dispatchers.Default)
            .limitedParallelism(1)
    private val work = scope + serial
    private val sessions = TogetherSessions(channel, work)
    private val reads = TogetherReads(channel, sessions, work, durationOf, now)
    private val starting = Mutex()
    private val joinNext = AtomicReference<Int?>(null)

    override val live: StateFlow<LiveSession?> = sessions.shown.asStateFlow()
    override val available: StateFlow<Boolean> = sessions.reachable.asStateFlow()

    init {
        // Listening lasts exactly as long as someone collects [live] (`LibraryEvents`):
        // no stream held open for a phone in a pocket.
        work.launch {
            sessions.shown.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .collectLatest { if (it) reads.listen() }
        }
    }

    override suspend fun refresh(): LiveSession? =
        withContext(serial) {
            try {
                look()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.w(TAG, "could not read the sessions", e)
                sessions.shown.value
            }
        }

    override suspend fun start(
        me: Member,
        own: Playback,
    ): Started =
        withContext(serial) {
            starting.withLock {
                look()?.let { return@withLock Started(it, conflict = true) }
                val playback = own.copy(pos = own.pos.position() ?: 0.0, rate = own.rate.speed() ?: 1.0)
                val session = started(UUID.randomUUID().toString(), me, playback, now())
                // Once on its way, the message is this device's to keep, whatever becomes of the caller.
                withContext(NonCancellable) {
                    val id = channel.send(writeSession(session))
                    sessions.local += me.screen
                    sessions.keep(id, session)
                    Started(LiveSession(id, session), conflict = false)
                }
            }
        }

    override suspend fun join(
        id: Int,
        me: Member,
    ): LiveSession? =
        withContext(serial) {
            if (!sessions.pending(id)) {
                val before = sessions.known[id]
                val read = channel.read(id)?.let(::parseSession) ?: return@withContext sessions.forget(id)
                val live = isLive(read, reads.lengthOf(read.set), now())
                // A move made meanwhile, or an edit heard, knows newer than this read.
                val overtaken = sessions.pending(id) || sessions.known[id] !== before
                if (!overtaken && !live) return@withContext sessions.forget(id)
                if (!overtaken) sessions.known[id] = read
            }
            sessions.queue(id, me.screen) { joined(it, me) }?.let { LiveSession(id, it) }
        }

    override suspend fun act(
        id: Int,
        screen: String,
        change: PlaybackChange,
    ): Session? =
        withContext(serial) {
            val current = sessions.view(id)?.takeUnless { it.ended } ?: return@withContext null
            val sane = change.copy(pos = change.pos?.position(), rate = change.rate?.speed())
            val moved = acted(current, sane, screen, now())
            sessions.queue(id, screen) { it.playbackOf(moved) }
        }

    override fun leave(
        id: Int,
        screen: String,
    ) {
        work.launch { sessions.queue(id, screen) { left(it, screen) } }
    }

    override fun end(
        id: Int,
        screen: String,
    ) {
        work.launch { sessions.queue(id, screen) { finished(it, screen) } }
    }

    override fun joinOnOpen(id: Int) = joinNext.set(id)

    override fun takeJoin(): Int? = joinNext.getAndSet(null)

    /** [TogetherReads.discover], and what it says about reaching Telegram. */
    private suspend fun look(): LiveSession? {
        val newest =
            try {
                reads.discover()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                sessions.reachable.value = false
                throw e
            }
        sessions.reachable.value = true
        sessions.shown.value = newest
        return newest
    }
}
