package playback

import data.CoreClient
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.SetSummary

/**
 * A [CoreClient] whose [read] and [totalSize] are configurable, so a test
 * can assert on exact byte offsets and lengths rather than on placeholder
 * zeros.
 */
class FakeCore(
    private val totalSize: Long = 0L,
    private val bytesOf: (offset: Long, len: Int) -> ByteArray = { _, len -> ByteArray(len) },
) : CoreClient {
    override fun isAuthorized(): Boolean = true
    override suspend fun requestCode(phone: String): String = "token"
    override suspend fun signIn(token: String, code: String): AuthOutcome = AuthOutcome.DONE
    override suspend fun checkPassword(password: String) = Unit
    override suspend fun refreshCatalog(url: String, keyB64: String): Long = 0
    override fun listSets(): List<SetSummary> = emptyList()
    override fun posterPath(posterKey: String): String? = null
    override fun totalSize(setId: String): Long = totalSize
    override suspend fun read(setId: String, offset: Long, len: Int): ByteArray = bytesOf(offset, len)
}
