package data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
    suspend fun supply(
        apiId: Int,
        apiHash: String,
    )

    /**
     * Closes the current core and clears its account files while reopening is excluded.
     * Device credentials are retained. Cleanup completes despite caller cancellation;
     * failures propagate and a failed close remains owned for a later retry.
     */
    suspend fun resetAccount(storage: CoreStorage)

    /** Closes the core and forgets the identity it was built from. */
    suspend fun forget()

    /**
     * Swaps the application identity under the same login: the old core is
     * closed before the new one is built, because both would open the one
     * data directory and its one auth key. The new identity is kept only
     * once Telegram has answered through it; otherwise the previous one is
     * built again on the next ask and the failure rethrown.
     *
     * That answer proves the id and the connection, not the hash: Telegram
     * checks an api_hash only when signing in, so a mistyped one surfaces at
     * the next sign-in rather than here.
     */
    suspend fun replace(
        apiId: Int,
        apiHash: String,
    )
}

/**
 * Builds at most one core per stored identity on [dispatcher]. Native library,
 * keystore, and auth-key filesystem work stay off the calling thread.
 *
 * [build] hides the final generated Core behind [CoreClient], allowing lifecycle
 * tests without a native library. Unpublished clients are closed on failure,
 * including cancellation at dispatcher handoffs; close failures are suppressed.
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
    private var pendingClose: CoreClient? = null
    override val core: StateFlow<CoreClient?> = built.asStateFlow()

    override suspend fun awaitCore(): CoreClient = coreOrNull() ?: built.filterNotNull().first()

    override suspend fun coreOrNull(): CoreClient? =
        mutex.withLock {
            if (pendingClose != null) withContext(NonCancellable + dispatcher) { closeHeld() }
            built.value?.let { return@withLock it }
            var candidate: CoreClient? = null
            try {
                withContext(dispatcher) {
                    settings.read()?.let { credentials -> build(credentials).also { candidate = it } }
                }?.also { built.value = it }
            } catch (
                @Suppress("TooGenericExceptionCaught") failure: Throwable,
            ) {
                discard(candidate, failure)
            }
        }

    /**
     * Built before it is stored, so an identity that cannot produce a core
     * is not left behind to be retried on every launch: a construction
     * failure that had already been written would turn one bad entry into a
     * crash loop with no way back to the first step.
     */
    override suspend fun supply(
        apiId: Int,
        apiHash: String,
    ) = mutex.withLock {
        if (pendingClose != null) withContext(NonCancellable + dispatcher) { closeHeld() }
        val credentials = TelegramCredentials(apiId, apiHash)
        var candidate: CoreClient? = null
        try {
            val client =
                withContext(dispatcher) {
                    build(credentials).also {
                        candidate = it
                        settings.write(apiId, apiHash)
                    }
                }
            // Assigning last is what resumes whoever is parked in awaitCore().
            built.value = client
        } catch (
            @Suppress("TooGenericExceptionCaught") failure: Throwable,
        ) {
            discard(candidate, failure)
        }
    }

    override suspend fun replace(
        apiId: Int,
        apiHash: String,
    ): Unit =
        mutex.withLock {
            withContext(NonCancellable + dispatcher) { closeHeld() }
            // Capture ownership before returning across a cancellable dispatcher handoff.
            var candidate: CoreClient? = null
            try {
                val client =
                    withContext(dispatcher) {
                        build(TelegramCredentials(apiId, apiHash)).also { candidate = it }
                    }
                client.account()
                withContext(NonCancellable + dispatcher) { settings.write(apiId, apiHash) }
                built.value = client
            } catch (
                @Suppress("TooGenericExceptionCaught") failure: Throwable,
            ) {
                discard(candidate, failure)
            }
        }

    private suspend fun discard(
        candidate: CoreClient?,
        failure: Throwable,
    ): Nothing {
        val closed =
            withContext(NonCancellable) {
                withContext(dispatcher) { runCatching { candidate?.close() } }
            }
        // Attach after the dispatcher handoff: coroutine stack recovery can
        // replace a rethrown exception and lose suppression added before it.
        closed.exceptionOrNull()?.takeUnless { it === failure }?.let(failure::addSuppressed)
        throw failure
    }

    override suspend fun resetAccount(storage: CoreStorage): Unit =
        mutex.withLock {
            withContext(NonCancellable + dispatcher) {
                closeHeld()
                storage.clear()
            }
        }

    /** Under the lifecycle mutex and on [dispatcher]; a failed close remains owned. */
    private fun closeHeld() {
        if (pendingClose == null) pendingClose = built.value
        built.value = null
        pendingClose?.close()
        pendingClose = null
    }

    override suspend fun forget(): Unit =
        mutex.withLock {
            if (pendingClose == null) pendingClose = built.value
            built.value = null
            withContext(NonCancellable + dispatcher) {
                val cleared = runCatching { settings.clear() }
                // Closing, not just dropping: the core holds a live, authorised
                // Telegram connection, and deleting the auth key file on disk
                // does nothing to one that is already open.
                val closed = runCatching { closeHeld() }
                val failure = cleared.exceptionOrNull()
                if (failure != null) {
                    closed.exceptionOrNull()?.takeUnless { it === failure }?.let(failure::addSuppressed)
                    throw failure
                }
                closed.getOrThrow()
            }
        }
}
