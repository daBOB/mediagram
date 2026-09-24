package catalog

import data.CoreClient
import data.CoreProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.CatalogFacts
import uniffi.mediagram_core.FetchReport
import uniffi.mediagram_core.LibraryChoice
import uniffi.mediagram_core.SetSummary
import uniffi.mediagram_core.TitleInfo

/** Local core boundary for catalog tests; tests override the IO they exercise. */
class CatalogCore : CoreClient {
    override fun isAuthorized(): Boolean = true

    override suspend fun requestCode(phone: String): String = error("catalog tests do not sign in")

    override suspend fun signIn(
        token: String,
        code: String,
    ): AuthOutcome = error("catalog tests do not sign in")

    override suspend fun checkPassword(password: String) = Unit

    override suspend fun listLibraries(): List<LibraryChoice> = emptyList()

    override suspend fun refreshLibrary(handle: String): Long = 0

    override suspend fun refreshCatalog(
        url: String,
        keyB64: String,
    ): Long = 0

    override suspend fun listSets(): List<SetSummary> = emptyList()

    override fun posterPath(posterKey: String): String? = null

    override suspend fun titleInfo(posterKey: String): TitleInfo? = null

    override suspend fun totalSize(setId: String): Long = 0

    override suspend fun catalogFacts(): CatalogFacts = CatalogFacts("channel", 0uL, 0uL, 0u, null)

    override suspend fun read(
        setId: String,
        offset: Long,
        len: Int,
    ): ByteArray = ByteArray(0)

    override suspend fun fetchMissing(
        tmdbKey: String,
        language: String,
    ): FetchReport = FetchReport(0u, 0u, 0u, 0u, 0u, 0u)

    override fun close() = Unit
}

class CatalogCoreProvider(
    private val client: CoreClient,
) : CoreProvider {
    override val core: StateFlow<CoreClient?> = MutableStateFlow(client)

    override suspend fun replace(
        apiId: Int,
        apiHash: String,
    ) = Unit

    override suspend fun awaitCore(): CoreClient = client

    override suspend fun coreOrNull(): CoreClient = client

    override suspend fun supply(
        apiId: Int,
        apiHash: String,
    ) = Unit

    override suspend fun forget() = Unit
}
