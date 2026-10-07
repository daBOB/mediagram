package ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import catalog.CatalogTab
import catalog.CatalogUiState
import catalog.Department
import catalog.Shelf
import catalog.allSetsById
import catalog.heroArtOf
import catalog.magazineHomeOf
import designsystem.Backdrop
import designsystem.LocalBackdrop
import model.WatchSnapshot
import ui.chrome.BrowseActions
import ui.chrome.LibraryScaffold
import ui.chrome.ProfileBarState
import ui.common.LibraryPositions
import ui.common.MenuActions
import ui.common.catalog.DepartmentScrollStates
import ui.common.chrome.HeroListState
import ui.common.chrome.asHeroListState

/**
 * One screen of the library under the app's chrome, and what leaving it
 * means.
 *
 * The system back gesture and the bar's back arrow are the same departure
 * said twice, so they are given the same lambda here rather than at each
 * branch — a screen that wired one and forgot the other would go back in
 * two different places depending on which the viewer reached for.
 *
 * Takes [at] itself rather than an `onSearch` lambda: every branch opens
 * search the same way — pushed over whatever it was already showing — so
 * there is nothing left for a caller to decide.
 */
@Composable
internal fun LibraryBranch(
    destination: Destination,
    menu: MenuActions,
    profile: ProfileBarState,
    browse: BrowseActions,
    at: LibraryPositions,
    onLeave: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onLeave)
    LibraryScaffold(
        destination = destination,
        onBack = onLeave,
        menu = menu,
        profile = profile,
        browse = browse,
        onSearch = at::openSearch,
        content = content,
    )
}

/** What this device holds in full, or nothing while the shelves are still loading. */
internal fun CatalogUiState.heldIdsOrEmpty(): Set<String> = (this as? CatalogUiState.Ready)?.heldIds.orEmpty()

/** The shelves a title or a collection page ranks Similar/a franchise link against, or nothing while still loading. */
internal fun CatalogUiState.shelvesOrEmpty(): List<Shelf> = (this as? CatalogUiState.Ready)?.shelves.orEmpty()

/**
 * Whichever list a hero on screen right now would bleed the bar over — the
 * shelves' own home cover when [chosenTab] is Home and has one, a
 * department's own hoisted position when [chosenTab] is a department whose
 * hero draws real lead art, `null` everywhere else (a kept wall,
 * Collections, or a department with nothing to lead its own hero with).
 * Split out of [LibraryBranches] once that function's own body
 * grew past this computation being able to stay inline and legible.
 */
@Composable
internal fun rememberActiveHeroState(
    shelves: List<Shelf>,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    now: Long,
    chosenTab: CatalogTab,
    homeListState: LazyListState,
    deptScroll: DepartmentScrollStates,
): HeroListState? {
    val hasCover =
        remember(shelves, watch, heldIds, now) {
            magazineHomeOf(shelves, watch, editorsChoice = watch.editorsChoice, now = now, heldIds = heldIds).editorial.cover.isNotEmpty()
        }
    val byId = remember(shelves) { allSetsById(shelves) }
    val department = (chosenTab as? CatalogTab.Dept)?.department
    val hasHeroArt =
        remember(shelves, byId, watch, department) { heroArtOf(department, shelves, byId, watch) } != null &&
            LocalBackdrop.current != Backdrop.SOLID
    return remember(chosenTab, hasCover, hasHeroArt, homeListState, deptScroll) {
        when {
            chosenTab == CatalogTab.Home -> if (hasCover) homeListState.asHeroListState() else null
            !hasHeroArt -> null
            else -> when (department) {
                Department.MOVIES -> deptScroll.movies.asHeroListState()
                Department.SERIES -> deptScroll.series.asHeroListState()
                Department.ANIME -> deptScroll.anime.asHeroListState()
                Department.TUTORIALS -> deptScroll.tutorials.asHeroListState()
                Department.DOCUMENTARIES -> deptScroll.documentaries.asHeroListState()
                null -> null
            }
        }
    }
}

/**
 * Gives [content] a saved-state slot of its own under [frameKey], or none
 * for the shelves (a `null` key), which keep theirs in a holder of their own.
 *
 * A slot outlives its frame leaving the screen while [isHeld] still says the
 * frame is on the stack — something was pushed over it, and back must find it
 * as it was — and is dropped once it is not: a popped frame's key only comes
 * round again if the same title is reopened at the same depth, which should
 * open fresh, and a slot held forever would ride along in the saved instance
 * state for the rest of the session.
 */
@Composable
internal fun SaveableStateHolder.keyedFrame(frameKey: String?, isHeld: (String) -> Boolean, content: @Composable () -> Unit) {
    if (frameKey == null) return content()
    key(frameKey) {
        DisposableEffect(frameKey) { onDispose { if (!isHeld(frameKey)) removeState(frameKey) } }
        SaveableStateProvider(frameKey, content)
    }
}
