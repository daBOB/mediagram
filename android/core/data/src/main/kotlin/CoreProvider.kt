package data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import settings.TelegramCredentials
import settings.TelegramSettings

/**
 * Hands out the native core. Deferred the way the player is, for a
 * different reason: the player is deferred because building it does real
 * disk work, this because on a first run the credentials it needs have not
 * been typed in yet and are read back asynchronously when they have.
 *
 * There is no core with a blank identity, and no half-built one standing in
 * until the real credentials arrive: either a core exists or [awaitCore]
 * has simply not returned. That is the whole point of the interface —
 * everything above it gets a core that works or gets nothing.
 */
interface CoreProvider {

    /** The core, once there are credentials to build it from. Suspends until then. */
    suspend fun awaitCore(): CoreClient

    /** The core if the credentials are already stored, `null` if they are not. */
    suspend fun coreOrNull(): CoreClient?

    /** Stores an identity and builds the core from it. */
    suspend fun supply(apiId: Int, apiHash: String)

    /** Forgets the stored identity and the core built from it. */
    suspend fun forget()
}

/**
 * Builds at most one core per stored identity, on first demand.
 *
 * [build] is passed in rather than called directly because the generated
 * `Core` class is final and belongs behind the dependency-injection seam;
 * this class knows only that something can turn credentials into a
 * [CoreClient]. That also lets a test drive the whole lifecycle without a
 * native library.
 */
class StoredCoreProvider(
    private val settings: TelegramSettings,
    private val build: (TelegramCredentials) -> CoreClient,
) : CoreProvider {

    // Guards the read-then-build sequence: two screens resolving at once
    // must not each construct a core over the same data directory.
    private val mutex = Mutex()

    private val built = MutableStateFlow<CoreClient?>(null)

    override suspend fun awaitCore(): CoreClient = coreOrNull() ?: built.filterNotNull().first()

    override suspend fun coreOrNull(): CoreClient? = mutex.withLock {
        built.value ?: settings.read()?.let { credentials ->
            build(credentials).also { built.value = it }
        }
    }

    override suspend fun supply(apiId: Int, apiHash: String) {
        settings.write(apiId, apiHash)
        // Building here, rather than leaving it to the next caller, is what
        // resumes whoever is already parked in awaitCore().
        coreOrNull()
    }

    override suspend fun forget() = mutex.withLock {
        settings.clear()
        built.value = null
    }
}
