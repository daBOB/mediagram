package player

import data.WatchStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import model.ListOfSets
import model.Profile
import model.Progress
import model.WatchSnapshot
import model.Watched

/**
 * Tracks state-write calls in memory, the same shape [WatchStateRepository]
 * promises its real implementation keeps: a write with nobody chosen does
 * nothing, and every successful one is reflected in [snapshot] at once.
 * Real enough for [ProgressRecorder] and [player.PlayerViewModel] to
 * exercise against without a core behind either.
 */
class FakeWatchStateRepository(
    profileChosen: Boolean = true,
    initialSnapshot: WatchSnapshot = WatchSnapshot.Empty,
) : WatchStateRepository {
    override val profiles: StateFlow<List<Profile>> = MutableStateFlow(emptyList())
    override val chosenProfileId: StateFlow<String?> = MutableStateFlow(if (profileChosen) "p1" else null)

    private val _snapshot = MutableStateFlow(initialSnapshot)
    override val snapshot: StateFlow<WatchSnapshot> = _snapshot

    /** Every write this fake was asked for, in order, e.g. `"setProgress s1 12.0 100.0"`. */
    val calls = mutableListOf<String>()

    override suspend fun chooseProfile(id: String) = true

    override suspend fun createProfile(name: String): Profile? = null

    override suspend fun setProgress(
        setId: String,
        at: Double,
        duration: Double?,
    ) {
        if (chosenProfileId.value == null) return
        calls += "setProgress $setId $at $duration"
        _snapshot.value =
            _snapshot.value.copy(
                progress = _snapshot.value.progress.filterNot { it.setId == setId } + Progress(setId, at, duration, 0),
            )
    }

    override suspend fun clearProgress(setId: String) {
        if (chosenProfileId.value == null) return
        calls += "clearProgress $setId"
        _snapshot.value = _snapshot.value.copy(progress = _snapshot.value.progress.filterNot { it.setId == setId })
    }

    override suspend fun setWatched(
        setId: String,
        finished: Boolean,
    ) {
        if (chosenProfileId.value == null) return
        calls += "setWatched $setId $finished"
        _snapshot.value =
            if (finished) {
                _snapshot.value.copy(watched = _snapshot.value.watched + Watched(setId, 0))
            } else {
                _snapshot.value.copy(watched = _snapshot.value.watched.filterNot { it.setId == setId })
            }
    }

    override suspend fun setWatchlisted(
        setId: String,
        listed: Boolean,
    ) {
        if (chosenProfileId.value == null) return
        calls += "setWatchlisted $setId $listed"
        _snapshot.value =
            _snapshot.value.copy(
                watchlist = if (listed) _snapshot.value.watchlist + setId else _snapshot.value.watchlist - setId,
            )
    }

    override suspend fun setKids(
        setId: String,
        marked: Boolean,
    ) {
        if (chosenProfileId.value == null) return
        calls += "setKids $setId $marked"
        _snapshot.value =
            _snapshot.value.copy(
                kids = if (marked) _snapshot.value.kids + setId else _snapshot.value.kids - setId,
            )
    }

    override suspend fun createList(name: String): ListOfSets? {
        if (chosenProfileId.value == null) return null
        calls += "createList $name"
        val made = ListOfSets(id = "list-${_snapshot.value.collections.size + 1}", name = name, items = emptyList())
        _snapshot.value = _snapshot.value.copy(collections = _snapshot.value.collections + made)
        return made
    }

    override suspend fun renameList(
        id: String,
        name: String,
    ): Boolean {
        if (chosenProfileId.value == null || _snapshot.value.collections.none { it.id == id }) return false
        calls += "renameList $id $name"
        _snapshot.value =
            _snapshot.value.copy(
                collections = _snapshot.value.collections.map { if (it.id == id) it.copy(name = name) else it },
            )
        return true
    }

    override suspend fun deleteList(id: String): Boolean {
        if (chosenProfileId.value == null || _snapshot.value.collections.none { it.id == id }) return false
        calls += "deleteList $id"
        _snapshot.value = _snapshot.value.copy(collections = _snapshot.value.collections.filterNot { it.id == id })
        return true
    }

    override suspend fun setInList(
        id: String,
        setId: String,
        included: Boolean,
    ): Boolean {
        if (chosenProfileId.value == null || _snapshot.value.collections.none { it.id == id }) return false
        calls += "setInList $id $setId $included"
        _snapshot.value =
            _snapshot.value.copy(
                collections =
                    _snapshot.value.collections.map { list ->
                        if (list.id != id) return@map list
                        val items = if (included) list.items + setId else list.items - setId
                        list.copy(items = items)
                    },
            )
        return true
    }

    override suspend fun reload() = Unit
}
