package catalog

import data.WatchStateRepository
import model.MediaSet
import model.Profile
import model.WatchSnapshot
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
internal fun WatchStateRepository.profileView(): ProfileView = profileViewOf(chosenProfile.value, snapshot.value)

/** [profile]'s [ProfileView] over [watch], its own snapshot: a kid's marks and limit, and its hidden sections. */
internal fun profileViewOf(
    profile: Profile?,
    watch: WatchSnapshot,
): ProfileView {
    val kid = profile?.takeIf { it.kids }
    return ProfileView(kid?.let { KidsView(watch.kidsMarks, it.kidsLimit) }, profile?.hiddenFrom.orEmpty())
}

/** The shelves [view] is shown, built from [sets] before any profile's filter: the one filter every surface applies. */
internal fun profileShelves(
    view: ProfileView,
    sets: List<MediaSet>,
): List<Shelf> {
    val visible = sets.filter { it.sectionKey() !in view.hidden }
    return shelvesOf(view.kids?.let { forKidsProfile(visible, it.marks, it.limit) } ?: visible)
        .filter { it.department.name.lowercase() !in view.hidden }
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
    val shelves = profileShelves(view, sets)
    return if (view.kids != null && !shelves.hasContent()) CatalogUiState.KidsEmpty(view.kids.limit) else shown.copy(shelves = shelves)
}
