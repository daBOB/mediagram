package data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import model.ListOfSets
import model.Profile
import model.Progress
import model.WatchSnapshot
import model.Watched
import uniffi.mediagram_core.ListRow
import uniffi.mediagram_core.ProgressRow
import uniffi.mediagram_core.StateSnapshot
import uniffi.mediagram_core.WatchedRow

/**
 * The chosen profile's watch state, over [CoreClient]. Everything reads
 * from the in-memory flows below; a write goes to the core first, on
 * [dispatcher], and only updates the flow once the core has confirmed it —
 * this is the one copy the rest of the app trusts, so it never shows a
 * change that a closed app or a refused write could still lose.
 *
 * State and list mutations require a chosen profile. Without one, Unit
 * methods do nothing, creation returns null, and list updates return false.
 * This includes the global kids mark. Core/provider failures and snapshot
 * read failures propagate; a write may have committed before its reload fails.
 * Only the originating core and selection may publish a completed read. Reset
 * invalidates retained state and pending publications, without cancelling IO.
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

    /** Chooses and reloads a profile; false means refused or superseded before acknowledgement. */
    suspend fun chooseProfile(id: String): Boolean

    /** Creates without choosing; null means refused or the owning account was reset/replaced. */
    suspend fun createProfile(name: String): Profile?

    /** Saves progress for the chosen profile; does nothing without one. */
    suspend fun setProgress(
        setId: String,
        at: Double,
        duration: Double?,
    )

    /** Clears progress for the chosen profile; does nothing without one. */
    suspend fun clearProgress(setId: String)

    /** Changes completion for the chosen profile; does nothing without one. */
    suspend fun setWatched(
        setId: String,
        finished: Boolean,
    )

    /** Changes watchlist membership for the chosen profile; does nothing without one. */
    suspend fun setWatchlisted(
        setId: String,
        listed: Boolean,
    )

    /** Changes the global kids mark, but still requires a chosen profile; otherwise does nothing. */
    suspend fun setKids(
        setId: String,
        marked: Boolean,
    )

    /** Creates a list; null means no chosen profile or creation refused by the core. */
    suspend fun createList(name: String): ListOfSets?

    /** Renames a list; false means no chosen profile or the core refused the update. */
    suspend fun renameList(
        id: String,
        name: String,
    ): Boolean

    /** Deletes a list; false means no chosen profile or the core refused the deletion. */
    suspend fun deleteList(id: String): Boolean

    /** Changes list membership; false means no chosen profile or the core refused the update. */
    suspend fun setInList(
        id: String,
        setId: String,
        included: Boolean,
    ): Boolean

    /**
     * Re-reads [profiles], [chosenProfileId] and, if one is chosen,
     * [snapshot], from the core. Called once by whoever first needs this
     * device's state, and again by [WatchSync] after a round that pulled
     * rows — nothing here refreshes itself.
     */
    suspend fun reload()

    /** Clears retained account state after a successful reset; already-running reads cannot restore it. */
    fun invalidate()
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

    private val publicationLock = Any()
    private val choices = Mutex()
    private var revision = 0L
    private var resetRevision = 0L
    private var choiceRequest = 0L
    private var selectedCore: CoreClient? = null

    private data class Selection(
        val core: CoreClient,
        val revision: Long,
        val id: String,
    )

    private data class Read(
        val core: CoreClient,
        val revision: Long,
        val profiles: List<Profile>,
        val chosen: String?,
    )

    override fun invalidate() {
        synchronized(publicationLock) {
            revision++
            resetRevision++
            selectedCore = null
            _chosenProfileId.value = null
            _profiles.value = emptyList()
            _snapshot.value = WatchSnapshot.Empty
        }
    }

    override suspend fun reload() {
        val account = synchronized(publicationLock) { resetRevision }
        val core = coreProvider.awaitCore()
        val read =
            choices.withLock {
                val started =
                    synchronized(publicationLock) {
                        if (resetRevision != account || coreProvider.core.value !== core) return
                        revision
                    }
                withContext(dispatcher) { Read(core, started, core.profiles().map(::toProfile), core.chosenProfile()) }
            }
        val snapshot =
            read.chosen?.let { withContext(dispatcher) { core.snapshot(it) }.toModel() }
                ?: WatchSnapshot.Empty
        synchronized(publicationLock) {
            if (!current(read.core, read.revision)) return
            if (selectedCore !== core || _chosenProfileId.value != read.chosen) revision++
            selectedCore = core
            _profiles.value = read.profiles
            _chosenProfileId.value = read.chosen
            _snapshot.value = snapshot
        }
    }

    override suspend fun chooseProfile(id: String): Boolean {
        val (request, account) = synchronized(publicationLock) { ++choiceRequest to resetRevision }
        val core = coreProvider.awaitCore()
        // Serialize the persisted choice and its acknowledgement with reload's selection read.
        // Snapshot reads stay outside the lock so they cannot hold up a newer choice.
        val selected =
            choices.withLock {
                val started =
                    synchronized(publicationLock) {
                        if (request != choiceRequest || account != resetRevision || coreProvider.core.value !== core) return@withLock null
                        ++revision
                    }
                if (!withContext(dispatcher) { core.chooseProfile(id) }) return@withLock null
                synchronized(publicationLock) {
                    if (!current(core, started)) {
                        null
                    } else {
                        revision++
                        selectedCore = core
                        _chosenProfileId.value = id
                        _snapshot.value = WatchSnapshot.Empty
                        Selection(core, revision, id)
                    }
                }
            } ?: return false
        refreshSnapshot(selected)
        return true
    }

    override suspend fun createProfile(name: String): Profile? {
        val started = synchronized(publicationLock) { resetRevision }
        val core = coreProvider.awaitCore()
        val created = withContext(dispatcher) { core.createProfile(name) } ?: return null
        synchronized(publicationLock) {
            if (resetRevision != started || coreProvider.core.value !== core) return null
            _profiles.value = _profiles.value + toProfile(created)
        }
        return toProfile(created)
    }

    override suspend fun setProgress(
        setId: String,
        at: Double,
        duration: Double?,
    ) = writing { core, id ->
        core.setProgress(id, setId, at, duration)
    }

    override suspend fun clearProgress(setId: String) = writing { core, id -> core.clearProgress(id, setId) }

    override suspend fun setWatched(
        setId: String,
        finished: Boolean,
    ) = writing { core, id ->
        core.setWatched(id, setId, finished)
    }

    override suspend fun setWatchlisted(
        setId: String,
        listed: Boolean,
    ) = writing { core, id ->
        core.setWatchlisted(id, setId, listed)
    }

    override suspend fun setKids(
        setId: String,
        marked: Boolean,
    ) = writing { core, _ -> core.setKids(setId, marked) }

    override suspend fun createList(name: String): ListOfSets? {
        val selected = selection() ?: return null
        val created = withContext(dispatcher) { selected.core.createCollection(selected.id, name) } ?: return null
        refreshSnapshot(selected)
        return created.toModel()
    }

    override suspend fun renameList(
        id: String,
        name: String,
    ): Boolean =
        listWriting { core, profileId ->
            core.renameCollection(profileId, id, name)
        }

    override suspend fun deleteList(id: String): Boolean =
        listWriting { core, profileId ->
            core.deleteCollection(profileId, id)
        }

    override suspend fun setInList(
        id: String,
        setId: String,
        included: Boolean,
    ): Boolean =
        listWriting { core, profileId ->
            core.setInCollection(profileId, id, setId, included)
        }

    /** A write that always has somewhere to write to — no chosen profile, nothing to do. */
    private suspend fun writing(write: suspend (CoreClient, String) -> Unit) {
        val selected = selection() ?: return
        withContext(dispatcher) { write(selected.core, selected.id) }
        refreshSnapshot(selected)
    }

    /** As [writing], for a call that answers whether it took effect. */
    private suspend fun listWriting(write: suspend (CoreClient, String) -> Boolean): Boolean {
        val selected = selection() ?: return false
        val ok = withContext(dispatcher) { write(selected.core, selected.id) }
        if (ok) refreshSnapshot(selected)
        return ok
    }

    private suspend fun selection(): Selection? {
        val selected =
            synchronized(publicationLock) {
                val id = _chosenProfileId.value ?: return null
                Selection(selectedCore ?: return null, revision, id)
            }
        val core = coreProvider.awaitCore()
        return synchronized(publicationLock) {
            selected.takeIf { core === it.core && current(it.core, it.revision) }
        }
    }

    private suspend fun refreshSnapshot(selected: Selection) {
        val snapshot = withContext(dispatcher) { selected.core.snapshot(selected.id) }.toModel()
        synchronized(publicationLock) {
            if (current(selected.core, selected.revision) && _chosenProfileId.value == selected.id) {
                _snapshot.value = snapshot
            }
        }
    }

    /** Called with [publicationLock] held so invalidation cannot race the following publication. */
    private fun current(
        core: CoreClient,
        started: Long,
    ): Boolean = revision == started && coreProvider.core.value === core
}

private fun toProfile(profile: uniffi.mediagram_core.Profile): Profile = Profile(profile.id, profile.name)

private fun ProgressRow.toModel(): Progress = Progress(setId, at, duration, updatedAt)

private fun WatchedRow.toModel(): Watched = Watched(setId, finishedAt)

private fun ListRow.toModel(): ListOfSets = ListOfSets(id, name, items)

private fun StateSnapshot.toModel(): WatchSnapshot =
    WatchSnapshot(
        progress = progress.map { it.toModel() },
        watched = watched.map { it.toModel() },
        watchlist = watchlist,
        kids = kids,
        collections = collections.map { it.toModel() },
    )
