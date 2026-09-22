package data

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    /**
     * Which core is current, for callers that cannot suspend to ask — a
     * data source being created on a loader thread, a ViewModel deciding
     * whether the sign-in it is showing still refers to this identity.
     *
     * It is a flow rather than a getter because the answer changes: a
     * start-over replaces the core, and anything holding the previous one
     * would otherwise go on using an identity the person was told had been
     * signed out.
     */
    val core: StateFlow<CoreClient?>

    /** The core, once there are credentials to build it from. Suspends until then. */
    suspend fun awaitCore(): CoreClient

    /** The core if the credentials are already stored, `null` if they are not. */
    suspend fun coreOrNull(): CoreClient?

    /** Stores an identity and builds the core from it. */
    suspend fun supply(apiId: Int, apiHash: String)

    /** Closes the core and forgets the identity it was built from. */
    suspend fun forget()

    /**
     * Swaps the application identity under the same login: the old core is
     * closed before the new one is built, because both would open the one
     * data directory and its one auth key. The new identity is kept only
     * once Telegram has answered through it; otherwise the previous one is
     * rebuilt and the failure rethrown.
     */
    suspend fun replace(apiId: Int, apiHash: String)
}

/**
 * Builds at most one core per stored identity, on first demand, on
 * [dispatcher].
 *
 * Never on the calling thread: the first build loads the native library,
 * decrypts the stored identity through the keystore and stats the auth key
 * file, and both callers reach this from the main dispatcher.
 *
 * [build] is passed in rather than called directly because the generated
 * `Core` class is final and belongs behind the dependency-injection seam;
 * this class knows only that something can turn credentials into a
 * [CoreClient]. That also lets a test drive the whole lifecycle without a
 * native library.
 */
class StoredCoreProvider(
    private val settings: TelegramSettings,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val build: (TelegramCredentials) -> CoreClient,
) : CoreProvider {

    // Guards the read-then-build sequence: two screens resolving at once
    // must not each construct a core over the same data directory.
    private val mutex = Mutex()

    private val built = MutableStateFlow<CoreClient?>(null)
    override val core: StateFlow<CoreClient?> = built.asStateFlow()

    override suspend fun awaitCore(): CoreClient = coreOrNull() ?: built.filterNotNull().first()

    override suspend fun coreOrNull(): CoreClient? = mutex.withLock {
        built.value ?: withContext(dispatcher) {
            settings.read()?.let { credentials -> build(credentials) }
        }?.also { built.value = it }
    }

    /**
     * Built before it is stored, so an identity that cannot produce a core
     * is not left behind to be retried on every launch: a construction
     * failure that had already been written would turn one bad entry into a
     * crash loop with no way back to the first step.
     */
    override suspend fun supply(apiId: Int, apiHash: String) = mutex.withLock {
        val credentials = TelegramCredentials(apiId, apiHash)
        val client = withContext(dispatcher) {
            build(credentials).also { settings.write(apiId, apiHash) }
        }
        // Assigning last is what resumes whoever is parked in awaitCore().
        built.value = client
    }

    override suspend fun replace(apiId: Int, apiHash: String): Unit = mutex.withLock {
        built.value?.let { open -> withContext(dispatcher) { open.close() } }
        built.value = null
        val candidate = withContext(dispatcher) { build(TelegramCredentials(apiId, apiHash)) }
        try {
            // Building never talks to Telegram, so a mistyped hash would pass
            // it. Asking who is signed in is what proves the new identity.
            candidate.account()
        } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
            // Closed before anything else is built over the same data
            // directory. The stored identity was never changed, so the next
            // [awaitCore] builds the previous one again, as on any launch.
            withContext(NonCancellable + dispatcher) { candidate.close() }
            throw failure
        }
        withContext(dispatcher) { settings.write(apiId, apiHash) }
        built.value = candidate
    }

    override suspend fun forget(): Unit = mutex.withLock {
        val previous = built.value
        built.value = null
        withContext(dispatcher) {
            settings.clear()
            // Closing, not just dropping: the core holds a live, authorised
            // Telegram connection, and deleting the auth key file on disk
            // does nothing to one that is already open.
            previous?.close()
        }
    }
}
