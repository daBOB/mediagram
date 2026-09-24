package data

import kotlinx.coroutines.awaitCancellation
import uniffi.mediagram_core.AccountSummary
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.CatalogFacts
import uniffi.mediagram_core.FetchReport
import uniffi.mediagram_core.LibraryChoice
import uniffi.mediagram_core.LibraryEvent
import uniffi.mediagram_core.ListRow
import uniffi.mediagram_core.PreferenceRow
import uniffi.mediagram_core.Profile
import uniffi.mediagram_core.SetSummary
import uniffi.mediagram_core.StateSnapshot
import uniffi.mediagram_core.SyncOutcome
import uniffi.mediagram_core.TitleInfo

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

    /** Installs the newest index that library's channel holds; answers its set count. */
    suspend fun refreshLibrary(handle: String): Long

    /**
     * The published-package reader, which is the only path that carries
     * poster art. Nothing in the first-run flow reaches it any more; it is
     * kept whole for the round that brings posters back.
     */
    suspend fun refreshCatalog(url: String, keyB64: String): Long
    suspend fun listSets(): List<SetSummary>
    fun posterPath(posterKey: String): String?

    /**
     * What the index records about a title, or nothing. A course has no
     * provider entry and a library assembled without a TMDB key has no rows
     * at all; both are ordinary, so neither is an error.
     */
    suspend fun titleInfo(posterKey: String): TitleInfo?
    suspend fun totalSize(setId: String): Long

    /**
     * What the installed catalog is, for the System screen's "Catalogue"
     * block: where it came from, how much it holds, and which schema it
     * was written with. Never fails — a count that could not be taken
     * reads back as zero, because a screen that cannot draw is worse than
     * one that says a library is empty.
     */
    suspend fun catalogFacts(): CatalogFacts
    suspend fun read(setId: String, offset: Long, len: Int): ByteArray

    /**
     * Fills in both of the things a library can arrive without: the poster
     * artwork a channel index has no room for, and the descriptions of
     * whatever nobody ran `mediagram metadata` over before pushing it. One
     * run answers both, because they come from one request per title.
     *
     * [language] is only a fallback. The library itself says what language
     * it was described in and that is what the provider is asked in; this is
     * what to ask in when it says nothing, and the device is the only thing
     * that knows it.
     *
     * The key is spent on this call and never stored by the core — Kotlin
     * owns holding it, so the start-over dialog's promise to clear it stays
     * true from exactly one place.
     */
    suspend fun fetchMissing(tmdbKey: String, language: String): FetchReport

    /**
     * Waits until library [handle] changes in a way worth a round: another
     * device's watch state, or a newly published index. Only a hint — act
     * on it with the ordinary refresh, and expect nothing missed while not
     * listening to be replayed. [ownDevice] is this device's watch-state id,
     * so its own writes are not reported back. Throws when listening stops;
     * call again after a pause.
     *
     * Waits for ever by default, so a test fake that has no events to give
     * need not say so; [DefaultCoreClient] is the only real implementation.
     */
    suspend fun nextLibraryEvent(handle: String, ownDevice: String): LibraryEvent = awaitCancellation()

    /**
     * Everyone this account's devices have created. A local read — nothing
     * here touches the network — refreshed by whatever asked, never by this
     * call itself.
     */
    suspend fun profiles(): List<Profile> = emptyList()

    /** Adds a new viewer under [name], or `null` when the write failed. */
    suspend fun createProfile(name: String): Profile? = null

    /**
     * Who this device is set to watch as, or `null` before the picker has
     * run. Local to this device — a sync round never sets it, the same way
     * a television keeps its own choice rather than inheriting a laptop's.
     */
    suspend fun chosenProfile(): String? = null

    /** Sets which profile this device watches as. `false` when [id] names nobody. */
    suspend fun chooseProfile(id: String): Boolean = false

    /**
     * Takes everything that was theirs with it. The web only ever removes a
     * profile the same way — no rename on either surface — so a device
     * still holding it brings it back on its next sync round rather than
     * this being the one true delete.
     */
    suspend fun deleteProfile(id: String): Boolean = false

    /** One profile's everything, in one read: progress, watched, lists. */
    suspend fun snapshot(profileId: String): StateSnapshot =
        StateSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    suspend fun setProgress(profileId: String, setId: String, at: Double, duration: Double?) = Unit
    suspend fun clearProgress(profileId: String, setId: String) = Unit
    suspend fun setWatched(profileId: String, setId: String, finished: Boolean) = Unit
    suspend fun setWatchlisted(profileId: String, setId: String, listed: Boolean) = Unit

    /**
     * Marks a set for kids, or not. Global rather than per-profile — every
     * profile on this account sees the same marks, the way the player loads
     * it once for the whole session rather than per viewer.
     */
    suspend fun setKids(setId: String, marked: Boolean) = Unit

    suspend fun createCollection(profileId: String, name: String): ListRow? = null
    suspend fun renameCollection(profileId: String, id: String, name: String): Boolean = false
    suspend fun deleteCollection(profileId: String, id: String): Boolean = false
    suspend fun setInCollection(profileId: String, id: String, setId: String, included: Boolean): Boolean = false

    /**
     * Every choice this profile has made, in one round trip: there are a
     * handful of these per show, and a page needs one the instant a title
     * opens — exactly when it has no time to ask for it.
     */
    suspend fun preferences(profileId: String): List<PreferenceRow> = emptyList()

    /** Remembers a choice, or forgets it ([value] `null`). */
    suspend fun setPreference(profileId: String, scope: String, name: String, value: String?): Boolean = false

    /**
     * A summary or subtitle track already sitting in the index. [kind] is
     * `"summary"` or `"subtitle"`; anything else, or a set with no such
     * text, answers `null`.
     */
    suspend fun setText(setId: String, kind: String, lang: String): String? = null

    /** This device's watch-state identity, minted once and kept beside `state.db`. */
    fun stateDeviceId(): String = ""

    /**
     * One round with the library's state channel: what it took in, whether
     * it sent anything, what went wrong. Never throws — a round that could
     * not run at all answers with [uniffi.mediagram_core.SyncOutcome.failed]
     * set rather than raising, so a caller with no network never has to
     * wrap this in its own try/catch to stay usable offline.
     */
    suspend fun syncState(handle: String): SyncOutcome = SyncOutcome(0uL, false, null)

    /** The datacentre this login lives on, read from the stored key; `null` before any login. */
    fun dcId(): Int? = null

    /** Who is signed in: name and username, never the number. Asks Telegram. */
    suspend fun account(): AccountSummary = error("no account behind this core")

    /**
     * Signs this device out at Telegram, then drops the connection and the
     * stored key — which go even when Telegram cannot be reached. Deleting
     * the key file alone left the login valid in the account's sessions.
     */
    suspend fun signOut() = Unit

    /**
     * Drops the native core and, with it, the authenticated connection it
     * holds open. Deleting the auth key file does not close a connection
     * that is already up — it stays authorised as the account it signed in
     * as, for as long as anything can still reach it. Signing a device out
     * has to mean this as well.
     */
    fun close()
}
