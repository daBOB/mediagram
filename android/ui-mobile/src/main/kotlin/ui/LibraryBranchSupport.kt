package ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import catalog.ANIME
import catalog.CatalogTabs
import catalog.CatalogUiState
import catalog.DOCUMENTARIES
import catalog.Destination
import catalog.Shelf
import catalog.allSetsById
import catalog.heroArtOf
import catalog.magazineHomeOf
import designsystem.Backdrop
import designsystem.LocalBackdrop
import model.WatchSnapshot
import ui.catalog.DepartmentScrollStates
import ui.chrome.HeroListState
import ui.chrome.asHeroListState

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

/**
 * Which of [tabs]' own tabs [title] names, or Home when it names none.
 *
 * A title saved before a shelf list shift (Documentaries landing between
 * Series and Tutorials, or any future department) no longer matches
 * anything at its old index, so restoring by the plain index would reopen
 * on whichever tab now sits there instead of the one that was actually
 * left open. A title survives the shift; only a title this build no longer
 * has at all — a stale save, or nothing chosen yet — falls back to Home.
 */
internal fun restoredTabIndex(tabs: CatalogTabs, title: String): Int = tabs.titles.indexOf(title).takeIf { it >= 0 } ?: 0

/** What this device holds in full, or nothing while the shelves are still loading. */
internal fun CatalogUiState.heldIdsOrEmpty(): Set<String> = (this as? CatalogUiState.Ready)?.heldIds.orEmpty()

/** The shelves a title or a collection page ranks Similar/a franchise link against, or nothing while still loading. */
internal fun CatalogUiState.shelvesOrEmpty(): List<Shelf> = (this as? CatalogUiState.Ready)?.shelves.orEmpty()

/**
 * Whichever list a hero on screen right now would bleed the bar over — the
 * shelves' own home cover when [chosenTab] is Home and has one, a
 * department's own hoisted position when [activeShelfTitle] names one of
 * the four that draw real lead art, `null` everywhere else (a kept wall,
 * Collections, a plain shelf, or a department with nothing to lead its own
 * hero with). Split out of [LibraryBranches] once that function's own body
 * grew past this computation being able to stay inline and legible.
 */
@Composable
internal fun rememberActiveHeroState(
    shelves: List<Shelf>,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    now: Long,
    chosenTab: Int,
    activeShelfTitle: String?,
    homeListState: LazyListState,
    deptScroll: DepartmentScrollStates,
): HeroListState? {
    val hasCover =
        remember(shelves, watch, heldIds, now) {
            magazineHomeOf(shelves, watch, editorsChoice = watch.editorsChoice, now = now, heldIds = heldIds).editorial.cover.isNotEmpty()
        }
    val byId = remember(shelves) { allSetsById(shelves) }
    val hasHeroArt =
        remember(shelves, byId, watch, activeShelfTitle) { heroArtOf(activeShelfTitle, shelves, byId, watch) } != null &&
            LocalBackdrop.current != Backdrop.SOLID
    return remember(chosenTab, activeShelfTitle, hasCover, hasHeroArt, homeListState, deptScroll) {
        when {
            chosenTab == 0 -> if (hasCover) homeListState.asHeroListState() else null
            !hasHeroArt -> null
            activeShelfTitle == "Movies" -> deptScroll.movies.asHeroListState()
            activeShelfTitle == "Series" -> deptScroll.series.asHeroListState()
            activeShelfTitle == ANIME -> deptScroll.anime.asHeroListState()
            activeShelfTitle == "Tutorials" -> deptScroll.tutorials.asHeroListState()
            activeShelfTitle == DOCUMENTARIES -> deptScroll.documentaries.asHeroListState()
            else -> null
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
