package ui.tv.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import catalog.MoviesDepartment
import designsystem.Overscan
import designsystem.Spacing
import ui.tv.TvTextRow

/** The kicker every department hero carries — `department-hero.js`'s own words, unlike the magazine cover's "Cover story". */
internal const val DeptKicker = "Only in your library"

/**
 * The Movies department's own front page — the television twin of the
 * phone's department screen and the web's `department-pages.js#renderMoviesDept`:
 * a hero for the most popular unwatched film with a backdrop, then Featured,
 * Genres, Acclaimed and Recently added, and a link down to every film the
 * shelf holds. `null` [dept] (an empty Movies shelf) draws nothing — the
 * caller falls back to the plain shelf wall, which says so.
 *
 * Arrival focus and [restoreKey] are [moviesDeptTargetOf]'s own reading —
 * gated on [LocalTakesArrivalFocus], so this page never steals the remote
 * back from Search or Menu the way [TvPage]'s own default would.
 */
@Composable
internal fun TvMoviesDepartmentPage(
    dept: MoviesDepartment,
    onOpenTitle: (setId: String) -> Unit,
    onPlay: (setId: String) -> Unit,
    onOpenGenre: (name: String) -> Unit,
    onOpenAllFilms: () -> Unit,
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
) {
    val first = remember { FocusRequester() }
    val heroFocus = remember { FocusRequester() }
    val target = remember(dept, restoreKey) { moviesDeptTargetOf(dept, restoreKey, heroFocusable = dept.lead != null) }
    val takesFocus = LocalTakesArrivalFocus.current
    // Keyed on the resolved target, not on `dept` itself: a library
    // republish rebuilds `dept` (a fresh instance, same content) far more
    // often than a viewer actually opens a new row, and re-requesting focus
    // every time would pull the remote back from wherever it has moved to.
    LaunchedEffect(target, takesFocus) {
        if (takesFocus) (if (target.first == "hero") heroFocus else first).requestFocus()
    }

    TvPage(takesArrivalFocus = takesFocus) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Overscan.vertical),
        ) {
            dept.lead?.let { lead ->
                // Inset like Home's cover: full-bleed, a 21:9 hero is taller than the space under
                // the masthead and its words sit outside the overscan-safe margin.
                Box(modifier = Modifier.padding(horizontal = Overscan.horizontal)) {
                    TvCoverStory(films = listOf(lead), onPlay = onPlay, onOpenTitle = onOpenTitle, kicker = DeptKicker, arrivalFocus = heroFocus)
                }
            }
            Column(modifier = Modifier.padding(horizontal = Overscan.horizontal)) {
                DeptRow(
                    "Featured",
                    dept.featured,
                    onOpenTitle,
                    focusAt = target.second.takeIf { target.first == "featured" },
                    focus = first,
                    takesFocus = takesFocus,
                    heldIds = heldIds,
                )
                if (dept.genres.isNotEmpty()) {
                    TvSectionHeading("Genres", modifier = Modifier.padding(top = Spacing.large))
                    GenreTileRow(
                        dept.genres,
                        onOpenGenre,
                        focusAt = target.second.takeIf { target.first == "genres" },
                        focus = first,
                        takesFocus = takesFocus,
                    )
                }
                DeptRow(
                    "Acclaimed, not yet seen",
                    dept.acclaimed,
                    onOpenTitle,
                    focusAt = target.second.takeIf { target.first == "acclaimed" },
                    focus = first,
                    takesFocus = takesFocus,
                    heldIds = heldIds,
                )
                DeptRow(
                    "Recently added",
                    dept.recentlyAdded,
                    onOpenTitle,
                    focusAt = target.second.takeIf { target.first == "recentlyAdded" },
                    focus = first,
                    takesFocus = takesFocus,
                    heldIds = heldIds,
                )
                TvTextRow(
                    text = "All ${dept.filmCount} films",
                    onClick = onOpenAllFilms,
                    focusRequester = first.takeIf { target.first == "all" },
                    modifier = Modifier.padding(top = Spacing.large, bottom = Spacing.medium),
                )
            }
        }
    }
}
