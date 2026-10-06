package testing

import data.CoreProvider
import data.CoreStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uniffi.mediagram_core.CoreInterface

/**
 * A [CoreProvider] whose core is already there, or is swapped in by hand —
 * the one fake every module's tests once built their own copy of, all the
 * same few lines over a [MutableStateFlow]. These tests are about what a caller does
 * with a core, not about waiting for one — `CoreProviderTest` (core:data)
 * covers the waiting, against the real `StoredCoreProvider`.
 *
 * [set] is how a test moves the published core mid-run — a start-over
 * completing, a picker replacing the identity — the same thing reassigning
 * `override val core = MutableStateFlow(...)` did in each of the four it
 * replaces.
 */
class FakeCoreProvider(
    initial: CoreInterface? = null,
) : CoreProvider {
    private val built = MutableStateFlow(initial)

    /**
     * Runs first in every [awaitCore] and [coreOrNull]. The core's own state
     * writes never throw, so a provider that cannot hand its core out is how
     * a repository call fails: a test throws from here to fail one, or
     * suspends here to hold one open. Lets every call through by default.
     */
    var beforeCore: suspend () -> Unit = {}

    override val core: StateFlow<CoreInterface?> = built

    override suspend fun awaitCore(): CoreInterface {
        beforeCore()
        return built.value ?: error("no core built for this fixture")
    }

    override suspend fun coreOrNull(): CoreInterface? {
        beforeCore()
        return built.value
    }

    override suspend fun supply(apiId: Int, apiHash: String) = Unit

    override suspend fun resetAccount(storage: CoreStorage) = error("this fixture does not reset accounts")

    override suspend fun forget() = Unit

    override suspend fun replace(apiId: Int, apiHash: String) = Unit

    /** Swaps the published core, e.g. to simulate a start-over or a replaced identity completing mid-test. */
    fun set(core: CoreInterface?) {
        built.value = core
    }
}
