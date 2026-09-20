package system

import data.CoreClient
import kotlinx.coroutines.CompletableDeferred
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.CatalogFacts
import uniffi.mediagram_core.LibraryChoice
import uniffi.mediagram_core.PosterReport
import uniffi.mediagram_core.SetSummary
import uniffi.mediagram_core.ShowInfo

/**
 * Answers [fetchPosters] with whatever the test sets up: a canned report,
 * or an exception to raise instead. Everything else this interface asks is
 * answered with a default a poster fetch test never looks at.
 *
 * [gate], when given, holds [fetchPosters] suspended until the test
 * completes it — the only way to prove a second [fetch][PostersViewModel.fetch]
 * call while one is in flight is refused: a fake that returns immediately
 * can never be caught still running.
 */
class FakeCore(
    var report: PosterReport = PosterReport(0u, 0u, 0u, 0u),
    var failure: Exception? = null,
    private val gate: CompletableDeferred<Unit>? = null,
) : CoreClient {

    /** The key the last call to [fetchPosters] was actually given. */
    var lastKey: String? = null
        private set

    /** How many times [fetchPosters] actually ran — a call the in-flight guard refused never increments this. */
    var fetchCalls: Int = 0
        private set

    override fun isAuthorized(): Boolean = true
    override suspend fun requestCode(phone: String): String = "token"
    override suspend fun signIn(token: String, code: String): AuthOutcome = AuthOutcome.DONE
    override suspend fun checkPassword(password: String) = Unit
    override suspend fun listLibraries(): List<LibraryChoice> = emptyList()
    override suspend fun refreshLibrary(handle: String): Long = 0
    override suspend fun refreshCatalog(url: String, keyB64: String): Long = 0
    override fun listSets(): List<SetSummary> = emptyList()
    override fun posterPath(posterKey: String): String? = null
    override fun showInfo(posterKey: String): ShowInfo? = null
    override fun totalSize(setId: String): Long = 0
    override fun catalogFacts(): CatalogFacts = CatalogFacts("channel", 0uL, 0uL, 0u)
    override suspend fun read(setId: String, offset: Long, len: Int): ByteArray = ByteArray(0)

    override suspend fun fetchPosters(tmdbKey: String, language: String): PosterReport {
        fetchCalls++
        lastKey = tmdbKey
        gate?.await()
        failure?.let { throw it }
        return report
    }

    override fun close() = Unit
}

/** A [data.CoreProvider] that already has a core built — a poster fetch never waits on one. */
class FakeCoreProvider(private val built: CoreClient) : data.CoreProvider {
    override val core: kotlinx.coroutines.flow.StateFlow<CoreClient?> =
        kotlinx.coroutines.flow.MutableStateFlow(built)

    override suspend fun awaitCore(): CoreClient = built
    override suspend fun coreOrNull(): CoreClient = built
    override suspend fun supply(apiId: Int, apiHash: String) = Unit
    override suspend fun forget() = Unit
}
