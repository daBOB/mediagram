package data

import uniffi.mediagram_core.AccountSummary
import uniffi.mediagram_core.AuthOutcome
import uniffi.mediagram_core.CatalogFacts
import uniffi.mediagram_core.Core
import uniffi.mediagram_core.FetchReport
import uniffi.mediagram_core.LibraryChoice
import uniffi.mediagram_core.LibraryEvent
import uniffi.mediagram_core.ListRow
import uniffi.mediagram_core.PreferenceRow
import uniffi.mediagram_core.Profile
import uniffi.mediagram_core.SearchHit
import uniffi.mediagram_core.SetSummary
import uniffi.mediagram_core.StateSnapshot
import uniffi.mediagram_core.SyncOutcome
import uniffi.mediagram_core.TitleInfo

/** Delegates every call straight through to the generated native core. */
class DefaultCoreClient(private val core: Core) : CoreClient {

    override fun isAuthorized(): Boolean = core.isAuthorized()

    override suspend fun requestCode(phone: String): String = core.requestCode(phone)

    override suspend fun signIn(token: String, code: String): AuthOutcome = core.signIn(token, code)

    override suspend fun checkPassword(password: String) = core.checkPassword(password)

    override suspend fun listLibraries(): List<LibraryChoice> = core.listLibraries()

    override suspend fun nextLibraryEvent(handle: String, ownDevice: String): LibraryEvent =
        core.nextLibraryEvent(handle, ownDevice)

    override fun dcId(): Int? = core.dcId()

    override suspend fun account(): AccountSummary = core.account()

    override suspend fun signOut() = core.signOut()

    override suspend fun refreshLibrary(handle: String): Long = core.refreshLibrary(handle).toLong()

    override suspend fun refreshCatalog(url: String, keyB64: String): Long =
        core.refreshCatalog(url, keyB64).toLong()

    override suspend fun listSets(): List<SetSummary> = core.listSets()

    override fun posterPath(posterKey: String): String? = core.posterPath(posterKey)

    override suspend fun search(query: String): List<SearchHit> = core.search(query)

    override suspend fun titleInfo(posterKey: String): TitleInfo? = core.titleInfo(posterKey)

    override suspend fun totalSize(setId: String): Long = core.totalSize(setId).toLong()

    override suspend fun catalogFacts(): CatalogFacts = core.catalogFacts()

    override suspend fun read(setId: String, offset: Long, len: Int): ByteArray =
        core.read(setId, offset.toULong(), len.toUInt())

    override suspend fun fetchMissing(tmdbKey: String, language: String): FetchReport =
        core.fetchMissing(tmdbKey, language)

    override suspend fun profiles(): List<Profile> = core.profiles()

    override suspend fun createProfile(name: String): Profile? = core.createProfile(name)

    override suspend fun chosenProfile(): String? = core.chosenProfile()

    override suspend fun chooseProfile(id: String): Boolean = core.chooseProfile(id)

    override suspend fun deleteProfile(id: String): Boolean = core.deleteProfile(id)

    override suspend fun snapshot(profileId: String): StateSnapshot = core.snapshot(profileId)

    override suspend fun setProgress(profileId: String, setId: String, at: Double, duration: Double?) =
        core.setProgress(profileId, setId, at, duration)

    override suspend fun clearProgress(profileId: String, setId: String) = core.clearProgress(profileId, setId)

    override suspend fun setWatched(profileId: String, setId: String, finished: Boolean) =
        core.setWatched(profileId, setId, finished)

    override suspend fun setWatchlisted(profileId: String, setId: String, listed: Boolean) =
        core.setWatchlisted(profileId, setId, listed)

    override suspend fun setKids(setId: String, marked: Boolean) = core.setKids(setId, marked)

    override suspend fun createCollection(profileId: String, name: String): ListRow? =
        core.createCollection(profileId, name)

    override suspend fun renameCollection(profileId: String, id: String, name: String): Boolean =
        core.renameCollection(profileId, id, name)

    override suspend fun deleteCollection(profileId: String, id: String): Boolean =
        core.deleteCollection(profileId, id)

    override suspend fun setInCollection(profileId: String, id: String, setId: String, included: Boolean): Boolean =
        core.setInCollection(profileId, id, setId, included)

    override suspend fun preferences(profileId: String): List<PreferenceRow> = core.preferences(profileId)

    override suspend fun setPreference(profileId: String, scope: String, name: String, value: String?): Boolean =
        core.setPreference(profileId, scope, name, value)

    override suspend fun setText(setId: String, kind: String, lang: String): String? =
        core.setText(setId, kind, lang)

    override fun stateDeviceId(): String = core.stateDeviceId()

    override suspend fun syncState(handle: String): SyncOutcome = core.syncState(handle)

    // The generated object is a handle on a Rust value; closing it releases
    // that value and every connection inside it. A later call on a closed
    // handle throws rather than quietly working, which is the point.
    override fun close() = core.close()
}
