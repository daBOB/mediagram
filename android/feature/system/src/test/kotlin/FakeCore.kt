package system

import data.CoreClient
import kotlinx.coroutines.CompletableDeferred
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.CatalogFacts
import uniffi.mediagram_core.FetchReport
import uniffi.mediagram_core.LibraryChoice
import uniffi.mediagram_core.SetSummary
import uniffi.mediagram_core.TitleInfo

/**
 * Answers [fetchMissing] with whatever the test sets up: a canned report, or
 * an exception to raise instead. Everything else this interface asks is
 * answered with a default a fetch test never looks at.
 *
 * [gate], when given, holds [fetchMissing] suspended until the test completes
 * it — the only way to prove a second [fetch][FetchViewModel.fetch] call
 * while one is in flight is refused: a fake that returns immediately can
 * never be caught still running.
 */
class FakeCore(
    var report: FetchReport = FetchReport(0u, 0u, 0u, 0u, 0u, 0u),
    var failure: Exception? = null,
    private val gate: CompletableDeferred<Unit>? = null,
) : CoreClient {

    /** The key the last call to [fetchMissing] was actually given. */
    var lastKey: String? = null
        private set

    /** The fallback language the last call to [fetchMissing] was given — the device's, not a constant. */
    var lastLanguage: String? = null
        private set

    /** How many times [fetchMissing] actually ran — a call the in-flight guard refused never increments this. */
    var fetchCalls: Int = 0
        private set

    override fun isAuthorized(): Boolean = true
    override suspend fun requestCode(phone: String): String = "token"
    override suspend fun signIn(token: String, code: String): AuthOutcome = AuthOutcome.DONE
    override suspend fun checkPassword(password: String) = Unit
    override suspend fun listLibraries(): List<LibraryChoice> = emptyList()
    override suspend fun refreshLibrary(handle: String): Long = 0
    override suspend fun refreshCatalog(url: String, keyB64: String): Long = 0
    override suspend fun listSets(): List<SetSummary> = emptyList()
    override fun posterPath(posterKey: String): String? = null
    override suspend fun titleInfo(posterKey: String): TitleInfo? = null
    override suspend fun totalSize(setId: String): Long = 0
    override suspend fun catalogFacts(): CatalogFacts = CatalogFacts("channel", 0uL, 0uL, 0u, null)
    override suspend fun read(setId: String, offset: Long, len: Int): ByteArray = ByteArray(0)

    override suspend fun fetchMissing(tmdbKey: String, language: String): FetchReport {
        fetchCalls++
        lastKey = tmdbKey
        lastLanguage = language
        gate?.await()
        failure?.let { throw it }
        return report
    }

    override fun close() = Unit
}

/** A [data.CoreProvider] that already has a core built — a fetch never waits on one. */
class FakeCoreProvider(private val built: CoreClient) : data.CoreProvider {
    override val core: kotlinx.coroutines.flow.StateFlow<CoreClient?> =
        kotlinx.coroutines.flow.MutableStateFlow(built)

    override suspend fun awaitCore(): CoreClient = built
    override suspend fun coreOrNull(): CoreClient = built
    override suspend fun supply(apiId: Int, apiHash: String) = Unit
    override suspend fun forget() = Unit
}
