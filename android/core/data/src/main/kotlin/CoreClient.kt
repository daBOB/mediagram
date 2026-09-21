package data

import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.CatalogFacts
import uniffi.mediagram_core.LibraryChoice
import uniffi.mediagram_core.PosterReport
import uniffi.mediagram_core.SetSummary
import uniffi.mediagram_core.ShowInfo

/**
 * The seam between this app and the generated native core. The generated
 * `Core` class is a concrete wrapper around the native library, so nothing
 * above this line depends on it directly — every ViewModel and repository
 * depends on this interface instead. [DefaultCoreClient] wraps the real
 * thing; a fake stands in for it under test.
 */
interface CoreClient {
    fun isAuthorized(): Boolean
    suspend fun requestCode(phone: String): String
    suspend fun signIn(token: String, code: String): AuthOutcome
    suspend fun checkPassword(password: String)

    /**
     * The libraries this account could read, each under a handle that means
     * nothing outside the core. Titles are for rendering; the handle is what
     * goes back to [refreshLibrary]. No channel identifier crosses this line.
     */
    suspend fun listLibraries(): List<LibraryChoice>

    /** Installs the catalog pinned in that library's channel; answers its set count. */
    suspend fun refreshLibrary(handle: String): Long

    /**
     * The published-package reader, which is the only path that carries
     * poster art. Nothing in the first-run flow reaches it any more; it is
     * kept whole for the round that brings posters back.
     */
    suspend fun refreshCatalog(url: String, keyB64: String): Long
    fun listSets(): List<SetSummary>
    fun posterPath(posterKey: String): String?

    /**
     * What the index records about a title, or nothing. A course has no
     * provider entry and a library assembled without a TMDB key has no rows
     * at all; both are ordinary, so neither is an error.
     */
    fun showInfo(posterKey: String): ShowInfo?
    fun totalSize(setId: String): Long

    /**
     * What the installed catalog is, for the System screen's "Catalogue"
     * block: where it came from, how much it holds, and which schema it
     * was written with. Never fails — a count that could not be taken
     * reads back as zero, because a screen that cannot draw is worse than
     * one that says a library is empty.
     */
    fun catalogFacts(): CatalogFacts
    suspend fun read(setId: String, offset: Long, len: Int): ByteArray

    /**
     * Fetches poster artwork for every title in the catalog TMDB can answer
     * about. The key is spent on this call and never stored by the core —
     * Kotlin owns holding it, so the start-over dialog's promise to clear it
     * stays true from exactly one place.
     */
    suspend fun fetchPosters(tmdbKey: String, language: String): PosterReport

    /**
     * Drops the native core and, with it, the authenticated connection it
     * holds open. Deleting the auth key file does not close a connection
     * that is already up — it stays authorised as the account it signed in
     * as, for as long as anything can still reach it. Signing a device out
     * has to mean this as well.
     */
    fun close()
}
