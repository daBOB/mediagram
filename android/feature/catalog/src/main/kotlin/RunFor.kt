package catalog

import model.Kind
import model.MediaSet

/**
 * The run [set] plays into when opened with no queue of its own — its
 * collection's flattened order, or nothing for a film. Ported from the
 * web's `playsNext` (`web/public/lib/playback/plays-next.js`): the
 * collection is found by matching [MediaSet.show] against the library's
 * shows and courses, the same lookup the web player runs before `nextInQueue`.
 *
 * Set and catalog state in, a run of ids out — never a [MediaSet] — because
 * `:feature:player` may not import `:feature:catalog` (feature modules do
 * not import one another) and can only ever be handed the ids themselves.
 */
fun runFor(set: MediaSet, state: CatalogUiState): List<String> {
    if (set.kind == Kind.MOVIE) return emptyList()
    val show = set.show ?: return emptyList()
    val sameName = (state as? CatalogUiState.Ready)
        ?.shelves
        ?.asSequence()
        ?.flatMap { it.entries }
        ?.filterIsInstance<Entry.Collection>()
        ?.filter { it.name == show }
        ?: return emptyList()
    // A name alone can name two collections — a course and a documentary
    // folder, or two shows, sharing it by coincidence — so the one that
    // actually holds this set, not just the first with a matching name, is
    // the one it belongs to.
    val order = sameName.map { playOrder(it.divisions) }.find { items -> items.any { it.setId == set.setId } }
    return order?.map(MediaSet::setId) ?: emptyList()
}
