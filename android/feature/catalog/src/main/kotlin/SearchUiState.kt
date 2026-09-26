package catalog

import model.MediaSet
import model.PersonHit
import uniffi.mediagram_core.SearchHit

/**
 * What the search screen renders. There is no loading state of its own:
 * the debounce built into [SearchViewModel] is the only wait a viewer
 * notices, and a flash of a spinner between two sets of results would say
 * less than the two results themselves do.
 *
 * [Ready] carries the core's own hits rather than sets already joined to
 * them: the join needs the catalog the screen already holds
 * ([searchRowsOf]), and asking the core for it again here would rebuild
 * every [MediaSet] and re-check every poster on disk for a query that
 * changes with every keystroke.
 */
sealed interface SearchUiState {
    /** The field is empty, or has not yet been typed in. */
    data object Idle : SearchUiState

    /**
     * [hits] and [people] both empty is what the web calls "nothing found"
     * — not a failure. [people] rides alongside [hits] rather than joined
     * to it: like [hits], it is what the core itself ranked, before
     * [searchGroupsOf] narrows it to titles this profile can see.
     */
    data class Ready(val hits: List<SearchHit>, val people: List<PersonHit> = emptyList()) : SearchUiState

    /** The core could not be asked at all — a round that raised rather than answering. */
    data class Failed(val message: String) : SearchUiState
}

/**
 * One ranked hit joined back onto the set it named. [set] carries every fact
 * the row draws — title, location, technical line, progress — the same
 * [MediaSet] every shelf card already reads; [matched] and [excerpt] are the
 * only two things a hit adds that a set does not already say about itself.
 */
data class SearchRow(val set: MediaSet, val matched: String, val excerpt: String?, val held: Boolean = false)

/**
 * Joins [hits] onto the catalog [CatalogUiState] already holds — no second
 * read of the core, no rebuilding of a [MediaSet] or a re-check of a
 * poster's file, both of which [CatalogViewModel] has already paid for
 * building the shelves. A hit naming a set the catalog no longer has is
 * dropped rather than shown as a broken row. [held] rides the same
 * [CatalogUiState.Ready.heldIds] every other card reads.
 */
fun searchRowsOf(hits: List<SearchHit>, catalogState: CatalogUiState): List<SearchRow> {
    val heldIds = (catalogState as? CatalogUiState.Ready)?.heldIds.orEmpty()
    return hits.mapNotNull { hit ->
        catalogState.mediaSet(hit.setId)?.let { SearchRow(it, hit.matched, hit.excerpt, hit.setId in heldIds) }
    }
}
