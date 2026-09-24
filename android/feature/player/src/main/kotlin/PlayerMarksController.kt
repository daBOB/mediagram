package player

import data.WatchStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import model.KidsVerdict
import model.ageLabelOf
import model.kidsVerdictOf

/**
 * The Watchlist, Kids and Add-to-list controls — ported from `player.js`'s
 * `watchlistButton`/`kidsButton`/`addToButton` click handlers — split out
 * of [PlayerViewModel] to keep that file under the project's line
 * guideline. Reads [PlayerSession.openSetId] rather than holding a copy of
 * its own, so there is exactly one place that decides which set is open.
 */
class PlayerMarksController(
    private val scope: CoroutineScope,
    private val session: PlayerSession,
    private val repository: WatchStateRepository,
    openSetId: StateFlow<String?>,
    private val openFsk: StateFlow<String?>,
) {
    /**
     * `null` between titles, the same gate `player.js` puts in front of
     * its own three buttons. Joined from [openSetId] rather than read
     * once, so a write from this screen or a sync round pulled in behind
     * it updates a pressed toggle's label without being asked again.
     */
    val marks: StateFlow<PlayerMarksState?> = combine(openSetId, openFsk, repository.snapshot) { setId, fsk, snapshot ->
        setId?.let {
            PlayerMarksState(
                watchlisted = it in snapshot.watchlist,
                kids = it in snapshot.kids,
                lists = snapshot.collections,
                memberOf = snapshot.collections.filter { list -> it in list.items }.mapTo(HashSet()) { list -> list.id },
                kidsVerdict = kidsVerdictOf(fsk),
                ageLabel = ageLabelOf(fsk),
            )
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleWatchlist() {
        val setId = session.openSetId ?: return
        val listed = setId in repository.snapshot.value.watchlist
        scope.launch { repository.setWatchlisted(setId, !listed) }
    }

    /** Marked here rather than on a shelf: "this is where a viewer is when they find out what a film actually is." */
    fun toggleKids() {
        val setId = session.openSetId ?: return
        // A rated title is not marked: its rating already decided.
        if (kidsVerdictOf(openFsk.value) != KidsVerdict.UNRATED) return
        val marked = setId in repository.snapshot.value.kids
        scope.launch { repository.setKids(setId, !marked) }
    }

    fun setInList(listId: String, included: Boolean) {
        val setId = session.openSetId ?: return
        scope.launch { repository.setInList(listId, setId, included) }
    }

    /**
     * Makes a new list and puts the open title straight on it — one action
     * where the web's `addToButton` needs two, since its list of lists lives
     * on the Collections shelf and this dialog does not want to send a
     * viewer mid-film away from the player to reach it. See `AddToListDialog`.
     */
    fun createListAndAdd(name: String) {
        val setId = session.openSetId ?: return
        scope.launch {
            val created = repository.createList(name) ?: return@launch
            repository.setInList(created.id, setId, true)
        }
    }
}
