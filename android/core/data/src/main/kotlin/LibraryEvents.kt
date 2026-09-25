package data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import settings.LibrarySettings
import uniffi.mediagram_core.LibraryEvent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What changes in the chosen library, as Telegram pushes it — within
 * milliseconds of another device writing, where a timer would wait minutes.
 *
 * Events are hints: whoever collects them runs the ordinary refresh, and a
 * missed one costs what it cost before this existed. Listening lasts exactly
 * as long as collecting does, so a collector tied to the screen being
 * visible listens only while the app is in front — no service, no socket
 * held open for a phone in a pocket.
 */
fun interface LibraryEvents {
    fun events(): Flow<LibraryEvent>

    companion object {
        /** Never says anything. For tests, and for screens that do not listen. */
        val None = LibraryEvents { emptyFlow() }
    }
}

/**
 * [LibraryEvents] from the native core.
 *
 * Follows [CoreProvider.core] rather than asking for a core once: a core
 * replaced or forgotten ends the wait on the old one, and cancelling that
 * call is what lets the old core — and its open connection — go. A wait left
 * parked on a closed core would hold both for as long as its channel stayed
 * quiet.
 *
 * Follows library selection too: setup can choose one after listening has
 * begun, and switching it cancels the old wait before starting the new one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoreLibraryEvents(
    private val coreProvider: CoreProvider,
    private val settings: LibrarySettings,
    private val retryAfter: Duration = 30.seconds,
) : LibraryEvents {
    override fun events(): Flow<LibraryEvent> =
        coreProvider.core.flatMapLatest { core ->
            if (core == null) {
                emptyFlow()
            } else {
                settings
                    .selections()
                    .retryWhen { failure, _ ->
                        if (failure is CancellationException || failure !is Exception) throw failure
                        delay(retryAfter)
                        true
                    }.flatMapLatest { handle ->
                        if (handle == null) emptyFlow() else listenOn(core, handle)
                    }
            }
        }

    private fun listenOn(
        core: CoreClient,
        handle: String,
    ): Flow<LibraryEvent> =
        flow {
            while (true) {
                val event =
                    try {
                        core.nextLibraryEvent(handle, core.stateDeviceId())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Listening stopped — offline, or the connection went. A pause
                        // rather than a tight loop against a network that is not there.
                        delay(retryAfter)
                        continue
                    }
                emit(event)
            }
        }
}

/**
 * One collection of [LibraryEvents], shared by every subscriber.
 *
 * The core serves one update stream per connection: a second, uncoordinated
 * `nextLibraryEvent` wait would steal whichever event comes next from
 * whichever caller was already waiting. [delegate] is collected at most
 * once, through [shareIn], and every caller here — the catalog's INDEX
 * handling, [WatchSync]'s STATE handling — reads the same events from that
 * one collection instead.
 *
 * [SharingStarted.WhileSubscribed] keeps the collection alive only while at
 * least one caller is listening, for a short grace period after the last one
 * stops — the same lifetime a lone collector already had, so the catalog's
 * screen-only listening is unchanged: no service, no socket held open for a
 * phone in a pocket.
 */
class SharedLibraryEvents(
    delegate: LibraryEvents,
    scope: CoroutineScope,
) : LibraryEvents {
    private val shared = delegate.events().shareIn(scope, SharingStarted.WhileSubscribed(5_000))

    override fun events(): Flow<LibraryEvent> = shared
}
