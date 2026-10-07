package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import catalog.Entry
import catalog.FranchisePage
import catalog.franchiseLineOf
import model.MediaSet
import model.WatchSnapshot
import ui.tv.chrome.LocalTvPagePadding
import ui.tv.chrome.TvPagePadding
import ui.tv.rememberStableRequester

/**
 * One franchise's own page — the television twin of the web's
 * `collections-page.js#renderFranchise`: a hero naming it, how many films
 * and the years they span, its lead's art, and TMDB's own introduction to it
 * (when there is one) inside the hero's copy; then its films in release
 * order, under that heading, as a plain wall.
 *
 * Opened fresh with an introduction, the remote rests on the introduction
 * itself, so the page shows from its top the way the web's does: TMDB's
 * introductions run to several lines, and landing on the first film instead
 * would scroll the franchise's own name off the screen before anyone had
 * read it. The introduction asks for the whole hero above it whenever it
 * takes the remote ([revealsFromTop]): asking only for itself, a page that
 * holds still for a stop already in its safe band, or a television moving
 * it to its pivot, could leave the name scrolled off above it. Down from it
 * is the first film. Coming back from a film lands on that film, as every
 * wall does; with no introduction the first film takes the remote and the
 * hero's words, at its foot, stay in view.
 *
 * The hero sits inside the wall's own page margins, its words flush with
 * the plates below rather than inset a second time — a pushed frame has no
 * bar for its art to bleed under.
 */
@Composable
internal fun TvFranchisePage(
    page: FranchisePage,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
) {
    val franchise = page.franchise
    val (positions, watchedIds) = rememberWatchMarks(watch)
    val line = remember(franchise) { franchiseLineOf(franchise) }
    val overview = page.overview?.takeIf(String::isNotBlank)
    // Once per visit, not on every return of the hero into composition: a
    // lazy header scrolled away and back would otherwise pull the remote up
    // to it again from wherever the viewer had got to.
    var arrived by rememberSaveable { mutableStateOf(false) }
    val readsFirst = overview != null && restoreKey == null && !arrived
    val overviewFocus = remember { FocusRequester() }
    val firstFilm = remember { FocusRequester() }
    TvPage(takesArrivalFocus = !readsFirst) {
        TvWall(
            items = franchise.films,
            key = MediaSet::setId,
            restoreKey = restoreKey,
            onOpen = { set -> onOpenTitle(set.setId) },
            header = {
                CompositionLocalProvider(LocalTvPagePadding provides NoInset) {
                    TvDepartmentHero(
                        title = franchise.name,
                        line = line,
                        lead = franchise.films.find { it.backdropPath != null },
                        // The introduction taking the remote asks for the hero from its own top,
                        // name and years included, not for the paragraph alone.
                        modifier = Modifier.revealsFromTop(),
                        franchiseTitle = true,
                        overview =
                            overview?.let { text ->
                                {
                                    TvReadableParagraph(text, modifier = Modifier.focusRequester(overviewFocus).focusProperties { down = firstFilm })
                                    if (readsFirst) {
                                        LaunchedEffect(Unit) {
                                            overviewFocus.requestFocus()
                                            arrived = true
                                        }
                                    }
                                }
                            },
                    )
                }
            },
            headings = mapOf(0 to "In release order"),
            plate = { set, modifier, onOpen ->
                val stop = modifier.focusRequester(rememberStableRequester(firstFilm.takeIf { set.setId == franchise.films.first().setId }))
                TvEntryPlate(Entry.Film(set), positions, watchedIds, onOpen, stop, heldIds)
            },
        )
    }
}

private val NoInset = TvPagePadding(0.dp, 0.dp, 0.dp, 0.dp)
