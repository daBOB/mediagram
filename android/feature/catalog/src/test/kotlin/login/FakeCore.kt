package login

import data.CoreClient
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.SetSummary

class FakeCore(
    private val authorized: Boolean = false,
    private val signInOutcome: AuthOutcome = AuthOutcome.DONE,
    private val requestCodeFails: Boolean = false,
) : CoreClient {
    override fun isAuthorized(): Boolean = authorized

    override suspend fun requestCode(phone: String): String {
        if (requestCodeFails) error("could not request a code")
        return "token"
    }

    override suspend fun signIn(token: String, code: String): AuthOutcome = signInOutcome
    override suspend fun checkPassword(password: String) = Unit
    override suspend fun refreshCatalog(url: String, keyB64: String): Long = 0
    override fun listSets(): List<SetSummary> = emptyList()
    override fun posterPath(posterKey: String): String? = null
    override fun totalSize(setId: String): Long = 0
    override suspend fun read(setId: String, offset: Long, len: Int): ByteArray = ByteArray(0)
}
