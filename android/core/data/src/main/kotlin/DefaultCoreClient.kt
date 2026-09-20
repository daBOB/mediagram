package data

import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.Core
import uniffi.mediagram_core.LibraryChoice
import uniffi.mediagram_core.SetSummary

/** Delegates every call straight through to the generated native core. */
class DefaultCoreClient(private val core: Core) : CoreClient {

    override fun isAuthorized(): Boolean = core.isAuthorized()

    override suspend fun requestCode(phone: String): String = core.requestCode(phone)

    override suspend fun signIn(token: String, code: String): AuthOutcome = core.signIn(token, code)

    override suspend fun checkPassword(password: String) = core.checkPassword(password)

    override suspend fun listLibraries(): List<LibraryChoice> = core.listLibraries()

    override suspend fun refreshLibrary(handle: String): Long = core.refreshLibrary(handle).toLong()

    override suspend fun refreshCatalog(url: String, keyB64: String): Long =
        core.refreshCatalog(url, keyB64).toLong()

    override fun listSets(): List<SetSummary> = core.listSets()

    override fun posterPath(posterKey: String): String? = core.posterPath(posterKey)

    override fun totalSize(setId: String): Long = core.totalSize(setId).toLong()

    override suspend fun read(setId: String, offset: Long, len: Int): ByteArray =
        core.read(setId, offset.toULong(), len.toUInt())

    // The generated object is a handle on a Rust value; closing it releases
    // that value and every connection inside it. A later call on a closed
    // handle throws rather than quietly working, which is the point.
    override fun close() = core.close()
}
