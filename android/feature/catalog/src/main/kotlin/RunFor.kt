package catalog

import model.Kind
import model.MediaSet

/**
 * The run [set] plays into when opened with no queue of its own — its
 * collection's flattened order, or nothing for a film. Ported from
 * `openTitle`'s "else" branch in `app.js`: the collection is found by
 * matching [MediaSet.show] against the library's shows and courses, the
 * same lookup the web player runs before falling back to `nextAfter`.
 *
 * Set and catalog state in, a run of ids out — never a [MediaSet] — because
 * `:feature:player` may not import `:feature:catalog` (feature modules do
 * not import one another) and can only ever be handed the ids themselves.
 */
fun runFor(set: MediaSet, state: CatalogUiState): List<String> {
    if (set.kind == Kind.MOVIE) return emptyList()
    val show = set.show ?: return emptyList()
    val collection = (state as? CatalogUiState.Ready)
        ?.shelves
        ?.asSequence()
        ?.flatMap { it.entries }
        ?.filterIsInstance<Entry.Collection>()
        ?.find { it.name == show }
        ?: return emptyList()
    return playOrder(collection.divisions).map(MediaSet::setId)
}
