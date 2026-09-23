package player

import data.WatchStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import model.ListOfSets
import model.Profile
import model.Progress
import model.Watched
import model.WatchSnapshot

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

    override suspend fun choose(id: String) = true

    override suspend fun create(name: String): Profile? = null

    override suspend fun setProgress(setId: String, at: Double, duration: Double?) {
        if (chosenProfileId.value == null) return
        calls += "setProgress $setId $at $duration"
        _snapshot.value = _snapshot.value.copy(
            progress = _snapshot.value.progress.filterNot { it.setId == setId } + Progress(setId, at, duration, 0),
        )
    }

    override suspend fun clearProgress(setId: String) {
        if (chosenProfileId.value == null) return
        calls += "clearProgress $setId"
        _snapshot.value = _snapshot.value.copy(progress = _snapshot.value.progress.filterNot { it.setId == setId })
    }

    override suspend fun setWatched(setId: String, finished: Boolean) {
        if (chosenProfileId.value == null) return
        calls += "setWatched $setId $finished"
        _snapshot.value = if (finished) {
            _snapshot.value.copy(watched = _snapshot.value.watched + Watched(setId, 0))
        } else {
            _snapshot.value.copy(watched = _snapshot.value.watched.filterNot { it.setId == setId })
        }
    }

    override suspend fun setWatchlisted(setId: String, listed: Boolean) = Unit

    override suspend fun setKids(setId: String, marked: Boolean) = Unit

    override suspend fun createList(name: String): ListOfSets? = null

    override suspend fun renameList(id: String, name: String) = false

    override suspend fun deleteList(id: String) = false

    override suspend fun setInList(id: String, setId: String, included: Boolean) = false

    override suspend fun reload() = Unit
}
