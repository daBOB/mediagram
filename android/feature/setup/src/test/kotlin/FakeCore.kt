package setup

import data.CoreClient
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.CatalogFacts
import uniffi.mediagram_core.FetchReport
import uniffi.mediagram_core.LibraryChoice
import uniffi.mediagram_core.SetSummary
import uniffi.mediagram_core.TitleInfo

/**
 * [authorized] is a `var` because the real answer changes underneath the
 * app: the core reads its auth key from disk on every call, so an account
 * signed out from another device flips this without anything here asking
 * it to.
 *
 * [listFailure] and [installFailure] are `var` for the same shape of
 * reason: the library step is the one question answered over the network,
 * so "fails now, works when asked again" is the ordinary case rather than
 * an exotic one.
 */
class FakeCore(
    var authorized: Boolean = false,
    private val libraries: List<LibraryChoice> = emptyList(),
    var listFailure: Exception? = null,
    var installFailure: Exception? = null,
) : CoreClient {

    /** Counted so a test can prove a re-derivation did not go back to Telegram. */
    var listCalls = 0
        private set

    /** Which handle was installed, or `null` if none was. */
    var installedHandle: String? = null
        private set

    override fun isAuthorized(): Boolean = authorized
    override suspend fun requestCode(phone: String): String = "token"
    override suspend fun signIn(token: String, code: String): AuthOutcome = AuthOutcome.DONE
    override suspend fun checkPassword(password: String) = Unit

    override suspend fun listLibraries(): List<LibraryChoice> {
        listCalls++
        listFailure?.let { throw it }
        return libraries
    }

    override suspend fun refreshLibrary(handle: String): Long {
        installFailure?.let { throw it }
        installedHandle = handle
        return libraries.size.toLong()
    }

    override suspend fun refreshCatalog(url: String, keyB64: String): Long = 0
    override suspend fun listSets(): List<SetSummary> = emptyList()
    override fun posterPath(posterKey: String): String? = null
    override suspend fun titleInfo(posterKey: String): TitleInfo? = null
    override suspend fun totalSize(setId: String): Long = 0
    override suspend fun catalogFacts(): CatalogFacts = CatalogFacts("channel", 0uL, 0uL, 0u, null)
    override suspend fun read(setId: String, offset: Long, len: Int): ByteArray = ByteArray(0)
    override suspend fun fetchMissing(tmdbKey: String, language: String): FetchReport =
        FetchReport(0u, 0u, 0u, 0u, 0u, 0u)
    override fun close() = Unit
}

/** A library as the core would list it, so a test only names its title. */
fun choice(title: String, handle: String = title.lowercase().replace(" ", "-")): LibraryChoice =
    LibraryChoice(handle = handle, title = title)
