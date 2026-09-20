package data

import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.Core
import uniffi.mediagram_core.SetSummary

/** Delegates every call straight through to the generated native core. */
class DefaultCoreClient(private val core: Core) : CoreClient {

    override fun isAuthorized(): Boolean = core.isAuthorized()

    override suspend fun requestCode(phone: String): String = core.requestCode(phone)

    override suspend fun signIn(token: String, code: String): AuthOutcome = core.signIn(token, code)

    override suspend fun checkPassword(password: String) = core.checkPassword(password)

    override suspend fun refreshCatalog(url: String, keyB64: String): Long =
        core.refreshCatalog(url, keyB64).toLong()

    override fun listSets(): List<SetSummary> = core.listSets()

    override fun posterPath(posterKey: String): String? = core.posterPath(posterKey)

    override fun totalSize(setId: String): Long = core.totalSize(setId).toLong()

    override suspend fun read(setId: String, offset: Long, len: Int): ByteArray =
        core.read(setId, offset.toULong(), len.toUInt())
}
