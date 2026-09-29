package ui.tv.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import catalog.Entry
import catalog.HomeRow
import catalog.MagazineHome
import catalog.RowContent
import designsystem.Overscan
import designsystem.Spacing
import kotlinx.coroutines.flow.first
import model.WatchSnapshot
import ui.tv.catalog.home.TvBandHeading
import ui.tv.catalog.home.TvContinueBand
import ui.tv.catalog.home.TvCourseList
import ui.tv.catalog.home.TvHomeCover
import ui.tv.catalog.home.TvHomeFeatures
import ui.tv.catalog.home.TvHomeSection
import ui.tv.catalog.home.TvPosterStrip
import ui.tv.catalog.home.TvRecentBand
import ui.tv.catalog.home.homeTargetOf
import ui.tv.chrome.LocalTvPagePadding

/**
 * The magazine home page — the television twin of the phone's `HomeScreen`
 * and, through it, the web's own `home-view.js`: cover story, features,
 * Continue watching beside a pull-quote, Recently added beside This month,
 * then Latest series and Latest courses. A `LazyColumn` of sections, only
 * the ones near the viewport ever composed — unlike the plain rows this
 * page used to draw, which a `Column` + `verticalScroll` always composed in
 * full — so a heavy section (a poster strip's `AsyncImage`s, a course
 * list's own cards) never builds before the remote is anywhere near it.
 *
 * A `LazyColumn` this short (at most six items) can still afford a
 * generous cache window either side of the viewport, wide enough to keep
 * every section actually composed almost all the time on a 540dp screen —
 * the mitigation for the risk a virtualised page raises that a plain
 * `Column` never had: `Down` reaching a section the list has not yet
 * decided to compose. [listState] carries that window; it is hoisted to
 * [TvCatalogScreen] rather than kept here, the same reason the phone's own
 * `HomeScreen` hoists its: the departments bar above reads where the page
 * actually is, through the same instance, to blend itself over the cover.
 *
 * [restoreKey] names the one stop a viewer opened and has come back to —
 * a film from the cover's own rotation, a resume card, a poster, a course
 * — resolved once, pure, by [homeTargetOf]; with none, or one no section
 * still carries, the first section with anything in it takes the arrival
 * stop instead (the cover's own Watch now, when there is a cover).
 *
 * [rows] carries only the plain shelf rows this page still draws as such —
 * Latest series and Latest courses; [magazine] already carries the cover,
 * the features and its own "Recently added" row, so the caller must not
 * hand this Continue, Next up or the Movies shelf here too.
 */
@Composable
internal fun TvHome(
    magazine: MagazineHome,
    rows: List<HomeRow>,
    watch: WatchSnapshot,
    listState: LazyListState,
    onPlay: (setId: String) -> Unit,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onToggleWatchlist: (setId: String, listed: Boolean) -> Unit,
    onSeeAll: (shelf: String) -> Unit,
    restoreKey: String? = null,
) {
    val editorial = magazine.editorial
    val watchlist = remember(watch) { watch.watchlist.toSet() }
    val series = remember(rows) { collectionsOf(rows, "Latest series") }
    val courses = remember(rows) { collectionsOf(rows, "Latest courses") }
    val seriesTotal = remember(rows) { rows.firstOrNull { it.title == "Latest series" }?.total ?: 0 }
    val coursesTotal = remember(rows) { rows.firstOrNull { it.title == "Latest courses" }?.total ?: 0 }

    val sections =
        remember(editorial, magazine, series, courses) {
            listOf(
                // Only stops with a focus requester of their own are
                // restorable — the quote and This month's own rows draw
                // through `onOpenTitle` too but carry none, so a restore
                // key naming one of them would otherwise match a section
                // with nowhere left to send the remote. Left out of the key
                // lists below, such a key falls through every section and
                // reaches `homeTargetOf`'s own graceful default instead —
                // exactly the "missing key" case this function already
                // documents, not a silent dead end.
                TvHomeSection.COVER to editorial.cover.map { it.setId },
                TvHomeSection.FEATURES to editorial.features.map { it.set.setId },
                TvHomeSection.CONTINUE to magazine.resumeCards.map { it.set.setId },
                TvHomeSection.RECENT to magazine.recentlyAdded.map { it.setId },
                TvHomeSection.SERIES to series.map { it.key },
                TvHomeSection.COURSES to courses.map { it.key },
            )
        }
    val included = remember(sections) { sections.filter { (_, keys) -> keys.isNotEmpty() }.map { it.first } }
    val target = remember(sections, restoreKey) { homeTargetOf(sections, restoreKey) }

    val coverFocus = remember { FocusRequester() }
    val featuresFocus = remember { FocusRequester() }
    val continueFocus = remember { FocusRequester() }
    val recentFocus = remember { FocusRequester() }
    val seriesFocus = remember { FocusRequester() }
    val coursesFocus = remember { FocusRequester() }
    val entryFocus =
        remember(coverFocus, featuresFocus, continueFocus, recentFocus, seriesFocus, coursesFocus) {
            mapOf(
                TvHomeSection.COVER to coverFocus,
                TvHomeSection.FEATURES to featuresFocus,
                TvHomeSection.CONTINUE to continueFocus,
                TvHomeSection.RECENT to recentFocus,
                TvHomeSection.SERIES to seriesFocus,
                TvHomeSection.COURSES to coursesFocus,
            )
        }

    // Keyed on `target` alone, not also on `takesFocus`: a sentinel
    // elsewhere (Search, the bar's own ⋮) sends the remote there instead of
    // here by making `takesFocus` read `false` for exactly one composition,
    // then flips it back to `true` the moment that sentinel is consumed —
    // with `target` itself unchanged (the wall key this page was handed
    // stayed `null` throughout both reads). Were `takesFocus` also a key,
    // that flip alone would re-run this effect and pull the remote straight
    // back from the field or button it had just reached. `takesFocus` is
    // still read fresh inside, the same "gate the action, not the key" the
    // rows below repeat for their own delegated effects.
    val takesFocus = LocalTakesArrivalFocus.current
    LaunchedEffect(target) {
        if (target == null || !takesFocus) return@LaunchedEffect
        val itemIndex = included.indexOf(target.section).takeIf { it >= 0 } ?: return@LaunchedEffect
        listState.scrollToItem(itemIndex)
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }.first { info -> info.any { it.index == itemIndex } }
        // The cover and the features row have no inner scrolling row of
        // their own to hand the requester to — every other section binds
        // the same requester to its own targeted stop through `focusAt`/
        // `focus` below, once this section's own composable has mounted.
        if (target.section == TvHomeSection.COVER || target.section == TvHomeSection.FEATURES) {
            entryFocus.getValue(target.section).requestFocus()
        }
    }

    val pagePadding = LocalTvPagePadding.current
    val hasCover = editorial.cover.isNotEmpty()
    val gutter = Modifier.padding(start = pagePadding.start, end = pagePadding.end)
    fun stopAt(section: TvHomeSection): Int? = target?.stop.takeIf { target?.section == section }

    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .focusRestorer(fallback = included.firstOrNull()?.let(entryFocus::getValue) ?: coverFocus),
        contentPadding = PaddingValues(top = if (hasCover) 0.dp else pagePadding.top, bottom = pagePadding.bottom + Overscan.horizontal),
    ) {
        if (hasCover) {
            item(key = "cover") {
                TvHomeCover(
                    films = editorial.cover,
                    watchlist = watchlist,
                    initialFilmId = stopAt(TvHomeSection.COVER)?.let { editorial.cover.getOrNull(it)?.setId },
                    onPlay = { onPlay(it.setId) },
                    onOpenTitle = onOpenTitle,
                    onToggleWatchlist = onToggleWatchlist,
                    arrivalFocus = coverFocus,
                    takesFocus = takesFocus && target?.section == TvHomeSection.COVER,
                )
            }
        }
        if (editorial.features.isNotEmpty()) {
            item(key = "features") {
                Box(gutter.padding(top = Spacing.extraLarge)) {
                    // No `takesFocus` of its own to gate: this row has no
                    // internal scrolling effect at all — the top-level
                    // effect above is what calls `requestFocus()` for
                    // FEATURES, already gated there.
                    TvHomeFeatures(features = editorial.features, onOpenTitle = onOpenTitle, focusAt = stopAt(TvHomeSection.FEATURES), focus = featuresFocus)
                }
            }
        }
        if (magazine.resumeCards.isNotEmpty() || editorial.quote != null) {
            item(key = "continue") {
                Box(gutter.padding(top = Spacing.extraLarge)) {
                    TvContinueBand(
                        cards = magazine.resumeCards,
                        quote = editorial.quote,
                        onPlay = onPlay,
                        onOpenTitle = onOpenTitle,
                        onSeeAllContinue = { onSeeAll("Continue") },
                        focusAt = stopAt(TvHomeSection.CONTINUE)?.takeIf { it < magazine.resumeCards.size },
                        focus = continueFocus,
                        takesFocus = takesFocus,
                    )
                }
            }
        }
        if (magazine.recentlyAdded.isNotEmpty() || editorial.thisMonth.isNotEmpty()) {
            item(key = "recent") {
                Box(gutter.padding(top = Spacing.extraLarge)) {
                    TvRecentBand(
                        recentlyAdded = magazine.recentlyAdded,
                        totalFilms = magazine.recentlyAddedRow.total,
                        thisMonth = editorial.thisMonth,
                        onOpenTitle = onOpenTitle,
                        onSeeAllMovies = { onSeeAll("Movies") },
                        focusAt = stopAt(TvHomeSection.RECENT)?.takeIf { it < magazine.recentlyAdded.size },
                        focus = recentFocus,
                        takesFocus = takesFocus,
                    )
                }
            }
        }
        if (series.isNotEmpty()) {
            item(key = "series") {
                Box(gutter.padding(top = Spacing.extraLarge)) {
                    Column {
                        TvBandHeading(title = "Latest series", count = seriesTotal)
                        TvPosterStrip(
                            shows = series,
                            onOpen = onOpenCollection,
                            focusAt = stopAt(TvHomeSection.SERIES),
                            focus = seriesFocus,
                            takesFocus = takesFocus,
                        )
                    }
                }
            }
        }
        if (courses.isNotEmpty()) {
            item(key = "courses") {
                Box(gutter.padding(top = Spacing.extraLarge)) {
                    Column {
                        TvBandHeading(title = "Latest courses", count = coursesTotal)
                        TvCourseList(
                            courses = courses,
                            onOpen = onOpenCollection,
                            focusAt = stopAt(TvHomeSection.COURSES),
                            focus = coursesFocus,
                            takesFocus = takesFocus,
                        )
                    }
                }
            }
        }
    }
}

/** [row]'s own entries, narrowed to the collections a poster row or a course list actually draws — [HomeRow] can also carry [catalog.SetCard]s (Continue, Next up), which never reach here. */
private fun collectionsOf(
    rows: List<HomeRow>,
    title: String,
): List<Entry.Collection> =
    (rows.firstOrNull { it.title == title }?.content as? RowContent.Entries)
        ?.entries
        ?.filterIsInstance<Entry.Collection>()
        .orEmpty()
