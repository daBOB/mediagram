package playback

import data.CoreClient
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.CoreException
import uniffi.mediagram_core.LibraryChoice
import uniffi.mediagram_core.SetSummary

/**
 * A [CoreClient] whose [read] and [totalSize] are configurable, so a test
 * can assert on exact byte offsets and lengths rather than on placeholder
 * zeros. [read] honours [totalSize] the way the real core does: it throws
 * once `offset >= totalSize` rather than fabricating bytes past the end,
 * so a caller that mis-clamps its own bookkeeping fails loudly instead of
 * getting plausible-looking garbage.
 */
class FakeCore(
    private val totalSize: Long = 0L,
    private val bytesOf: (offset: Long, len: Int) -> ByteArray = { _, len -> ByteArray(len) },
) : CoreClient {
    override fun isAuthorized(): Boolean = true
    override suspend fun requestCode(phone: String): String = "token"
    override suspend fun signIn(token: String, code: String): AuthOutcome = AuthOutcome.DONE
    override suspend fun checkPassword(password: String) = Unit
    override suspend fun listLibraries(): List<LibraryChoice> = emptyList()
    override suspend fun refreshLibrary(handle: String): Long = 0
    override suspend fun refreshCatalog(url: String, keyB64: String): Long = 0
    override fun listSets(): List<SetSummary> = emptyList()
    override fun posterPath(posterKey: String): String? = null
    override fun totalSize(setId: String): Long = totalSize

    override suspend fun read(setId: String, offset: Long, len: Int): ByteArray {
        if (offset >= totalSize) throw CoreException.NotFound("offset $offset is at or past the end")
        val clampedLen = minOf(len.toLong(), totalSize - offset).toInt()
        return bytesOf(offset, clampedLen)
    }

    override fun close() = Unit
}
