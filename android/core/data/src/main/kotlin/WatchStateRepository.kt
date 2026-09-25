package data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import model.ListOfSets
import model.Profile
import model.Progress
import model.Watched
import model.WatchSnapshot
import uniffi.mediagram_core.StateSnapshot
import uniffi.mediagram_core.ProgressRow
import uniffi.mediagram_core.WatchedRow
import uniffi.mediagram_core.ListRow

/**
 * The chosen profile's watch state, over [CoreClient]. Everything reads
 * from the in-memory flows below; a write goes to the core first, on
 * [dispatcher], and only updates the flow once the core has confirmed it —
 * this is the one copy the rest of the app trusts, so it never shows a
 * change that a closed app or a refused write could still lose.
 */
interface WatchStateRepository {
    /** Everyone this account's devices have created; empty until [reload] runs once. */
    val profiles: StateFlow<List<Profile>>

    /**
     * Who this device is set to watch as, `null` before the picker has run.
     * Local to this device — nothing here is touched by a sync round.
     */
    val chosenProfileId: StateFlow<String?>

    /** The chosen profile's everything; [WatchSnapshot.Empty] until one is chosen. */
    val snapshot: StateFlow<WatchSnapshot>

    /** Sets which profile this device watches as, and reloads its snapshot. */
    suspend fun choose(id: String): Boolean

    /** Adds a new viewer under [name]; does not choose it — matches the web, which leaves that to a second tap. */
    suspend fun create(name: String): Profile?

    suspend fun setProgress(setId: String, at: Double, duration: Double?)
    suspend fun clearProgress(setId: String)
    suspend fun setWatched(setId: String, finished: Boolean)
    suspend fun setWatchlisted(setId: String, listed: Boolean)

    /** Marks a set for kids, or not. Global, not this profile's own — see [CoreClient.setKids]. */
    suspend fun setKids(setId: String, marked: Boolean)

    suspend fun createList(name: String): ListOfSets?
    suspend fun renameList(id: String, name: String): Boolean
    suspend fun deleteList(id: String): Boolean
    suspend fun setInList(id: String, setId: String, included: Boolean): Boolean

    /**
     * Re-reads [profiles], [chosenProfileId] and, if one is chosen,
     * [snapshot], from the core. Called once by whoever first needs this
     * device's state, and again by [WatchSync] after a round that pulled
     * rows — nothing here refreshes itself.
     */
    suspend fun reload()

    /**
     * Removes a profile and everything it has watched, then re-reads who
     * exists and who is chosen — the core forgets the choice itself when it
     * named the one removed. False when nothing was removed.
     *
     * Local, as on the web: a profile another device's sync document still
     * names is created again by the next round that pulls it.
     */
    suspend fun deleteProfile(id: String): Boolean = false

    /**
     * Treats a title as watched to the end — `markFinished` in the web's
     * `watch-state.js`: what the player does when the credits roll, and what
     * a viewer does by hand from Continue for something finished elsewhere
     * or given up on. The position goes, because a finished title has nowhere
     * to resume to; the fact that it finished stays, and a title already
     * finished keeps the date it was first finished.
     */
    suspend fun markFinished(setId: String) {
        clearProgress(setId)
        if (snapshot.value.watched.none { it.setId == setId }) setWatched(setId, true)
    }
}

class DefaultWatchStateRepository(
    private val coreProvider: CoreProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WatchStateRepository {

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    override val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _chosenProfileId = MutableStateFlow<String?>(null)
    override val chosenProfileId: StateFlow<String?> = _chosenProfileId.asStateFlow()

    private val _snapshot = MutableStateFlow(WatchSnapshot.Empty)
    override val snapshot: StateFlow<WatchSnapshot> = _snapshot.asStateFlow()

    override suspend fun reload() {
        val core = coreProvider.awaitCore()
        val (profileList, chosen) = withContext(dispatcher) {
            core.profiles().map(::toProfile) to core.chosenProfile()
        }
        _profiles.value = profileList
        _chosenProfileId.value = chosen
        _snapshot.value = chosen?.let { id -> withContext(dispatcher) { core.snapshot(id) }.toModel() }
            ?: WatchSnapshot.Empty
    }

    override suspend fun choose(id: String): Boolean {
        val core = coreProvider.awaitCore()
        val chose = withContext(dispatcher) { core.chooseProfile(id) }
        if (chose) {
            _chosenProfileId.value = id
            refreshSnapshot(core, id)
        }
        return chose
    }

    override suspend fun deleteProfile(id: String): Boolean {
        val core = coreProvider.awaitCore()
        val removed = withContext(dispatcher) { core.deleteProfile(id) }
        if (removed) reload()
        return removed
    }

    override suspend fun create(name: String): Profile? {
        val core = coreProvider.awaitCore()
        val created = withContext(dispatcher) { core.createProfile(name) } ?: return null
        _profiles.value = _profiles.value + toProfile(created)
        return toProfile(created)
    }

    override suspend fun setProgress(setId: String, at: Double, duration: Double?) = writing { core, id ->
        core.setProgress(id, setId, at, duration)
    }

    override suspend fun clearProgress(setId: String) = writing { core, id -> core.clearProgress(id, setId) }

    override suspend fun setWatched(setId: String, finished: Boolean) = writing { core, id ->
        core.setWatched(id, setId, finished)
    }

    override suspend fun setWatchlisted(setId: String, listed: Boolean) = writing { core, id ->
        core.setWatchlisted(id, setId, listed)
    }

    override suspend fun setKids(setId: String, marked: Boolean) = writing { core, _ -> core.setKids(setId, marked) }

    override suspend fun createList(name: String): ListOfSets? {
        val id = _chosenProfileId.value ?: return null
        val core = coreProvider.awaitCore()
        val created = withContext(dispatcher) { core.createCollection(id, name) } ?: return null
        refreshSnapshot(core, id)
        return created.toModel()
    }

    override suspend fun renameList(id: String, name: String): Boolean = listWriting { core, profileId ->
        core.renameCollection(profileId, id, name)
    }

    override suspend fun deleteList(id: String): Boolean = listWriting { core, profileId ->
        core.deleteCollection(profileId, id)
    }

    override suspend fun setInList(id: String, setId: String, included: Boolean): Boolean = listWriting { core, profileId ->
        core.setInCollection(profileId, id, setId, included)
    }

    /** A write that always has somewhere to write to — no chosen profile, nothing to do. */
    private suspend fun writing(write: suspend (CoreClient, String) -> Unit) {
        val id = _chosenProfileId.value ?: return
        val core = coreProvider.awaitCore()
        withContext(dispatcher) { write(core, id) }
        refreshSnapshot(core, id)
    }

    /** As [writing], for a call that answers whether it took effect. */
    private suspend fun listWriting(write: suspend (CoreClient, String) -> Boolean): Boolean {
        val id = _chosenProfileId.value ?: return false
        val core = coreProvider.awaitCore()
        val ok = withContext(dispatcher) { write(core, id) }
        if (ok) refreshSnapshot(core, id)
        return ok
    }

    private suspend fun refreshSnapshot(core: CoreClient, profileId: String) {
        _snapshot.value = withContext(dispatcher) { core.snapshot(profileId) }.toModel()
    }
}

private fun toProfile(profile: uniffi.mediagram_core.Profile): Profile = Profile(profile.id, profile.name)

private fun ProgressRow.toModel(): Progress = Progress(setId, at, duration, updatedAt)

private fun WatchedRow.toModel(): Watched = Watched(setId, finishedAt)

private fun ListRow.toModel(): ListOfSets = ListOfSets(id, name, items)

private fun StateSnapshot.toModel(): WatchSnapshot = WatchSnapshot(
    progress = progress.map { it.toModel() },
    watched = watched.map { it.toModel() },
    watchlist = watchlist,
    kids = kids,
    collections = collections.map { it.toModel() },
)
