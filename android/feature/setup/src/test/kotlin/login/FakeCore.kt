package setup.login

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

/**
 * [signInFailures] and [passwordFailures] reject that many attempts before
 * accepting, which is how the real core behaves: it keeps the pending login
 * and password tokens across a rejection, so the very next call can succeed.
 * [requestCodeCalls] is counted so a test can prove a retry did not go back
 * and ask Telegram for another code.
 */
class FakeCore(
    private val authorized: Boolean = false,
    private val signInOutcome: AuthOutcome = AuthOutcome.DONE,
    private val requestCodeFails: Boolean = false,
    private val signInFailures: Int = 0,
    private val passwordFailures: Int = 0,
) : CoreClient {
    var requestCodeCalls = 0
        private set

    var requestFailure: Exception? = null
    var signInFailure: Exception? = null
    var passwordFailure: Exception? = null
    private var signInAttempts = 0
    private var passwordAttempts = 0

    override fun isAuthorized(): Boolean = authorized

    override suspend fun requestCode(phone: String): String {
        requestCodeCalls++
        requestFailure?.let { throw it }
        if (requestCodeFails) error("could not request a code")
        return "token"
    }

    override suspend fun signIn(
        token: String,
        code: String,
    ): AuthOutcome {
        signInAttempts++
        signInFailure?.let { throw it }
        if (signInAttempts <= signInFailures) error("the code was not accepted")
        return signInOutcome
    }

    override suspend fun checkPassword(password: String) {
        passwordAttempts++
        passwordFailure?.let { throw it }
        if (passwordAttempts <= passwordFailures) error("the password was not accepted")
    }

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

/**
 * The sign-in screen is only ever reached once the Telegram application
 * identity is stored, so the core is there before the ViewModel asks —
 * which is what this stands in for.
 */
class ResolvedCoreProvider(
    private val client: CoreClient,
) : CoreProvider {
    override suspend fun replace(
        apiId: Int,
        apiHash: String,
    ) = Unit

    override val core: StateFlow<CoreClient?> = MutableStateFlow(client)

    override suspend fun awaitCore(): CoreClient = client

    override suspend fun coreOrNull(): CoreClient = client

    override suspend fun supply(
        apiId: Int,
        apiHash: String,
    ) = Unit

    override suspend fun forget() = Unit
}
