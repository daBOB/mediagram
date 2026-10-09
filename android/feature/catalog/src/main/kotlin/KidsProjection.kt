package catalog

import data.WatchStateRepository
import model.MediaSet
import model.forKidsProfile

/** What a kids profile is shown by: the hand marks by age, and its own limit. */
internal data class KidsView(
    val marks: Map<String, Int>,
    val limit: Int,
)

/** The chosen kid's [KidsView], `null` for a grown-up, as the repository holds it now. */
internal fun WatchStateRepository.kidsView(): KidsView? {
    val kid = chosenProfile.value?.takeIf { it.kids } ?: return null
    return KidsView(snapshot.value.kidsMarks, kid.kidsLimit)
}

/**
 * [shown] for [kids], rebuilt from [sets] — the sets behind it, before any
 * profile's filter. One place the filter applies: every wall and title page
 * is built from these shelves, and watch together's Join is offered from them.
 */
internal fun projected(
    shown: CatalogUiState,
    kids: KidsView?,
    sets: List<MediaSet>,
): CatalogUiState =
    when {
        shown is CatalogUiState.Ready && kids != null -> {
            val shelves = shelvesOf(forKidsProfile(sets, kids.marks, kids.limit))
            if (!shelves.hasContent()) CatalogUiState.KidsEmpty(kids.limit) else shown.copy(shelves = shelves)
        }
        else -> shown
    }
