package data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
 * Reads the chosen handle before every wait. With no library chosen there is
 * nothing to listen to, and it goes quiet until the core changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoreLibraryEvents(
    private val coreProvider: CoreProvider,
    private val settings: LibrarySettings,
    private val retryAfter: Duration = 30.seconds,
) : LibraryEvents {

    override fun events(): Flow<LibraryEvent> =
        coreProvider.core.filterNotNull().flatMapLatest { core -> listenOn(core) }

    private fun listenOn(core: CoreClient): Flow<LibraryEvent> = flow {
        while (true) {
            val handle = settings.read() ?: return@flow
            val event = try {
                core.nextLibraryEvent(handle, OWN_DEVICE)
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

    private companion object {
        /**
         * No watch-state device id exists on this device yet, so nothing is
         * filtered as this device's own. Harmless while only index events are
         * acted on; the watch-state sync passes its real id when it lands.
         */
        const val OWN_DEVICE = ""
    }
}
