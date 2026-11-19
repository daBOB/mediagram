package ui.common.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import catalog.CatalogUiState
import catalog.cardPosition
import catalog.filmOf
import catalog.mediaSet
import catalog.positionFor
import catalog.versionsOf
import catalog.watchedFractionOf
import model.MediaSet
import model.Progress
import model.WatchSnapshot

/**
 * When each title was finished, read by the card of a film held more than
 * once: finishing one copy closes the place left in another of its cut.
 * Provided once where a surface's library is drawn, rather than threaded
 * through every wall that draws a card, which would otherwise carry it
 * without ever reading it. Empty, nothing is ever closed.
 */
val LocalWatchedAt = compositionLocalOf<(String) -> Long?> { { null } }

/** [watch]'s finishing times, for [LocalWatchedAt]. */
fun watchedAtOf(watch: WatchSnapshot): (String) -> Long? = watch.watched.associate { it.setId to it.finishedAt }::get

/** The rule under the card standing for [set]: for a film held more than once, the newest place in any copy. */
@Composable
fun cardProgressOf(
    set: MediaSet,
    positions: Map<String, Progress>,
): Float? = watchedFractionOf(cardPosition(set, positions, LocalWatchedAt.current))

/** A film whose copy is being asked for, and the run a pick plays into. */
data class VersionQuestion(
    val film: MediaSet,
    val run: List<String>?,
)

/**
 * A play from a card that plays straight away (Continue, My List, a list,
 * search): a film held more than once asks which copy first; anything else
 * plays at once. Asked every time rather than remembered — the right copy
 * depends on the screen. Nothing asks when the copy is already chosen: a row
 * on the film's page, a Join, a scene, or the player handing over to the
 * next title, none of which comes through here.
 */
@Stable
class VersionAsk internal constructor(
    private val filmOf: (String) -> MediaSet?,
    private val openPlayer: (String, List<String>?) -> Unit,
) {
    /** The question on screen, or `null`. */
    var question: VersionQuestion? by mutableStateOf(null)
        private set

    fun play(
        setId: String,
        run: List<String>? = null,
    ) {
        val film = filmOf(setId)?.takeIf { it.versions.isNotEmpty() }
        if (film == null) openPlayer(setId, run) else question = VersionQuestion(film, run)
    }

    /** The viewer's pick: it plays, and takes its film's place in the run so a list carries on after it. */
    fun choose(copy: MediaSet) {
        val asked = question ?: return
        question = null
        val copies = versionsOf(asked.film).mapTo(HashSet(), MediaSet::setId)
        openPlayer(copy.setId, asked.run?.map { if (it in copies) copy.setId else it })
    }

    /** Backed out: nothing plays. */
    fun cancel() {
        question = null
    }
}

@Composable
fun rememberVersionAsk(
    catalogState: CatalogUiState,
    openPlayer: (String, List<String>?) -> Unit,
): VersionAsk {
    val state by rememberUpdatedState(catalogState)
    val open by rememberUpdatedState(openPlayer)
    return remember { VersionAsk({ state.filmOf(it) }, { id, run -> open(id, run) }) }
}

/**
 * Where the copy [setId] starts, when it is one of a film held more than
 * once: the place its cut was left, in whichever copy. `null` for anything
 * else, which starts from its own row as it always has.
 */
fun cutPositionOf(
    catalogState: CatalogUiState,
    setId: String,
): (() -> Progress?)? {
    val ready = catalogState as? CatalogUiState.Ready ?: return null
    val film = ready.filmOf(setId) ?: return null
    val copy = ready.mediaSet(setId) ?: return null
    return { positionFor(copy, film, ready.watch) }
}
