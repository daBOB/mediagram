package catalog

import data.WatchStateRepository
import model.MediaSet
import model.forKidsProfile
import model.hiddenFrom
import model.sectionKey

/** What a kids profile is shown by: the hand marks by age, and its own limit. */
internal data class KidsView(
    val marks: Map<String, Int>,
    val limit: Int,
)

/** What the chosen profile is shown by: the kid's [kids] view, if a kid, and the section keys [hidden] from it. */
internal data class ProfileView(
    val kids: KidsView?,
    val hidden: Set<String>,
)

/** The chosen profile's [ProfileView], as the repository holds it now. */
internal fun WatchStateRepository.profileView(): ProfileView {
    val profile = chosenProfile.value
    val kid = profile?.takeIf { it.kids }
    return ProfileView(kid?.let { KidsView(snapshot.value.kidsMarks, it.kidsLimit) }, profile?.hiddenFrom.orEmpty())
}

/**
 * [shown] for [view], rebuilt from [sets] — the sets behind it, before any
 * profile's filter. One place the filter applies: every wall and title page
 * is built from these shelves, and watch together's Join is offered from them.
 * A hidden section's shelf goes whole, so it keeps no empty tab.
 */
internal fun projected(
    shown: CatalogUiState,
    view: ProfileView,
    sets: List<MediaSet>,
): CatalogUiState {
    if (shown !is CatalogUiState.Ready || (view.kids == null && view.hidden.isEmpty())) return shown
    val visible = sets.filter { it.sectionKey() !in view.hidden }
    val shelves =
        shelvesOf(view.kids?.let { forKidsProfile(visible, it.marks, it.limit) } ?: visible)
            .filter { it.department.name.lowercase() !in view.hidden }
    return if (view.kids != null && !shelves.hasContent()) CatalogUiState.KidsEmpty(view.kids.limit) else shown.copy(shelves = shelves)
}
