package setup

import data.CoreProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import settings.LibrarySettings
import javax.inject.Inject

/**
 * Which libraries this account can read, which one this device chose, and
 * how a choice becomes a catalog on disk.
 *
 * Separate from the ViewModel that shows it, and with no composable in
 * sight, because the television surface asks exactly these four questions
 * of exactly the same account. A picker is the one part of setup that is
 * genuinely the same on a phone and across a room — what differs is how a
 * list is rendered and how it is moved through, not what is in it.
 */
class Libraries
    @Inject
    constructor(
        private val coreProvider: CoreProvider,
        private val settings: LibrarySettings,
        private val dispatcher: CoroutineDispatcher,
    ) {
        /** What the account can see, in the order Telegram itself lists it. */
        suspend fun list(): List<LibraryOption> = coreProvider.awaitCore().listLibraries().map { LibraryOption(it.handle, it.title) }

        /**
         * Installs that library's catalog and, only then, remembers the choice.
         *
         * Only then on purpose, and for the same reason the core is built
         * before its identity is stored: a handle written before the catalog
         * arrived would send every later launch straight past the picker to a
         * library that is not there, with no way back to the list.
         */
        suspend fun install(handle: String) {
            coreProvider.awaitCore().refreshLibrary(handle)
            withContext(dispatcher) { settings.write(handle) }
        }

        /** The handle this device chose, or `null` while the picker is still due. */
        suspend fun chosen(): String? = withContext(dispatcher) { settings.read() }

        suspend fun forget(): Unit = withContext(dispatcher) { settings.clear() }
    }
