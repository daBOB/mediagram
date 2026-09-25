package player

import data.WatchStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _actionNotice = MutableStateFlow<String?>(null)

    /** A mark write that could not be confirmed, said until dismissed or the title changes. */
    val actionNotice: StateFlow<String?> = _actionNotice.asStateFlow()

    /** Bumped on every open and stop, so a write that finishes late cannot speak about a title no longer open. */
    private var generation = 0L

    /** Whether the chosen profile is a kids profile — a child does not approve titles for themselves. */
    private val onKidsProfile =
        combine(repository.profiles, repository.chosenProfileId) { profiles, chosen ->
            profiles.firstOrNull { it.id == chosen }?.kids == true
        }

    /**
     * `null` between titles, the same gate `player.js` puts in front of
     * its own three buttons. Joined from [openSetId] rather than read
     * once, so a write from this screen or a sync round pulled in behind
     * it updates a pressed toggle's label without being asked again.
     */
    val marks: StateFlow<PlayerMarksState?> =
        combine(openSetId, openFsk, repository.snapshot, onKidsProfile) { setId, fsk, snapshot, kidsProfile ->
            setId?.let {
                PlayerMarksState(
                    watchlisted = it in snapshot.watchlist,
                    kids = it in snapshot.kids,
                    lists = snapshot.collections,
                    memberOf = snapshot.collections.filter { list -> it in list.items }.mapTo(HashSet()) { list -> list.id },
                    kidsVerdict = kidsVerdictOf(fsk),
                    ageLabel = ageLabelOf(fsk),
                    canMarkKids = !kidsProfile,
                )
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** A title opened or the player left: any notice was about the one before. */
    fun reset() {
        generation++
        _actionNotice.value = null
    }

    fun dismissNotice() {
        _actionNotice.value = null
    }

    fun toggleWatchlist() {
        val setId = session.openSetId ?: return
        val listed = setId in repository.snapshot.value.watchlist
        write("Watchlist update") {
            repository.setWatchlisted(setId, !listed)
            true
        }
    }

    /** Marked here rather than on a shelf: "this is where a viewer is when they find out what a film actually is." */
    fun toggleKids() {
        val setId = session.openSetId ?: return
        // A kids profile does not approve titles for itself.
        if (marks.value?.canMarkKids == false) return
        // A rated title is not marked: its rating already decided.
        if (kidsVerdictOf(openFsk.value) != KidsVerdict.UNRATED) return
        val marked = setId in repository.snapshot.value.kids
        write("Kids update") {
            repository.setKids(setId, !marked)
            true
        }
    }

    fun setInList(
        listId: String,
        included: Boolean,
    ) {
        val setId = session.openSetId ?: return
        write("list update") { repository.setInList(listId, setId, included) }
    }

    /**
     * Makes a new list and puts the open title straight on it — one action
     * where the web's `addToButton` needs two, since its list of lists lives
     * on the Collections shelf and this dialog does not want to send a
     * viewer mid-film away from the player to reach it. See `AddToListDialog`.
     */
    fun createListAndAdd(name: String) {
        val setId = session.openSetId ?: return
        write("list update") {
            val created = repository.createList(name) ?: return@write false
            repository.setInList(created.id, setId, true)
        }
    }

    /** A write can commit before its snapshot reload fails; only repository snapshots acknowledge marks. */
    private fun write(
        action: String,
        block: suspend () -> Boolean,
    ) {
        val started = generation
        scope.launch {
            val confirmed =
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    false
                }
            if (started != generation) return@launch
            val failure = "Could not confirm the $action. Check it and try again."
            if (!confirmed) {
                _actionNotice.value = failure
            } else if (_actionNotice.value == failure) {
                _actionNotice.value = null
            }
        }
    }
}
