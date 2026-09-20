package setup

import data.CoreClient
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.SetSummary

/**
 * [authorized] is a `var` because the real answer changes underneath the
 * app: the core reads its auth key from disk on every call, so an account
 * signed out from another device flips this without anything here asking
 * it to.
 */
class FakeCore(var authorized: Boolean = false) : CoreClient {
    override fun isAuthorized(): Boolean = authorized
    override suspend fun requestCode(phone: String): String = "token"
    override suspend fun signIn(token: String, code: String): AuthOutcome = AuthOutcome.DONE
    override suspend fun checkPassword(password: String) = Unit
    override suspend fun refreshCatalog(url: String, keyB64: String): Long = 0
    override fun listSets(): List<SetSummary> = emptyList()
    override fun posterPath(posterKey: String): String? = null
    override fun totalSize(setId: String): Long = 0
    override suspend fun read(setId: String, offset: Long, len: Int): ByteArray = ByteArray(0)
    override fun close() = Unit
}
