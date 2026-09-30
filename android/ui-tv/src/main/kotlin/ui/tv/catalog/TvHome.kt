package ui.tv.catalog

import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.unit.dp
import catalog.Entry
import catalog.HomeRow
import catalog.MagazineHome
import catalog.RowContent
import designsystem.Overscan
import designsystem.Spacing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import model.WatchSnapshot
import ui.tv.LocalLibraryCovered
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
    // Where Up from the cover's own action row leads — the bar's own
    // selected pill, the same stop Back already reaches from anywhere on
    // this page ([ui.tv.chrome.TvLibraryChrome]'s own `BackHandler`).
    upExit: FocusRequester,
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
    // The band the remote was last in, saved with the catalog like the
    // list's own scroll: a title two bands carry at once — a new upload
    // that is also trending — comes back to the band it was opened from.
    // Read, not keyed, so moving between bands never re-aims the arrival.
    var lastSection by rememberSaveable { mutableStateOf<TvHomeSection?>(null) }
    val target = remember(sections, restoreKey) { homeTargetOf(sections, restoreKey, lastSection) }

    // Pinned, not only tracked, while focus sits in this band: Compose's own
    // lazy-layout bookkeeping can decide, in a runnable it posts to run
    // after the current frame, that a slot it did not see used this pass is
    // free to deactivate — including the very slot arrival focus just
    // landed in, found on the box straight after a cold-start refresh
    // released the state this page had been frozen behind, once, though
    // nothing here ever disposed it (a `DisposableEffect` on the section
    // never logged one — deactivation, not disposal). A pinned slot is
    // never a candidate for that at all, the same guarantee `LazyColumn`'s
    // own internals lean on for a pinned item, asked for here the public
    // way instead of reached for through them.
    @Composable
    fun Modifier.remembersBand(section: TvHomeSection): Modifier {
        val container = LocalPinnableContainer.current
        var pinned by remember { mutableStateOf<PinnableContainer.PinnedHandle?>(null) }
        DisposableEffect(Unit) { onDispose { pinned?.release() } }
        return onFocusChanged { state ->
            if (state.hasFocus) {
                lastSection = section
                if (pinned == null) pinned = container?.pin()
            } else {
                pinned?.release()
                pinned = null
            }
        }
    }
    val scope = rememberCoroutineScope()

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

    // `takesFocus` is `rememberArrivalReady`'s own delayed signal, combined
    // with whatever a sentinel elsewhere (Search, the bar's own ⋮) is doing
    // (`TvCatalogScreen`'s own doc on the combination) — a real return to
    // this page turns it `true` one frame after the pushed frame that
    // covered it is actually gone, never in the same frame that removal
    // happens in.
    val takesFocus = LocalTakesArrivalFocus.current
    // Raw, not `takesFocus` above — see the doc on `arrived`'s own reset
    // below, which needs this one alongside `restoreKey`.
    val covered = LocalLibraryCovered.current
    // Grants arrival focus once per time this page is shown, not once per
    // value `target` happens to take: a resumed position keeps reordering
    // Continue for as long as its own write is still landing, and a
    // restore whose own row moves *while this wait is still pending* would
    // otherwise restart the wait on every move — a burst of them can ask
    // for focus faster than Compose's own search resolves any one attempt,
    // indistinguishable, to whatever is above this list, from nothing
    // having asked at all, which is what actually sends the remote to the
    // chrome's own bar-pill fallback. Once the one grant below lands,
    // later reorders move the card, not the remote — the same rule this
    // page already keeps for content arriving into a section the viewer
    // has since left, extended to cover the wait for the very first grant
    // too, not only the time after it.
    //
    // With this page kept alive under a pushed frame rather than rebuilt
    // on every Back, "once" can no longer mean "once ever": a real content
    // stop opening here re-arms it, so the very next uncover grants again.
    // Keyed on `covered` *and* `restoreKey`, and only firing when both say
    // "a real stop, freshly opened" (`covered` true, `restoreKey` non-null):
    // a sentinel's own redirect (Search, ⋮, Latest, Genres, Settings,
    // System) covers this page too, but leaves `restoreKey` `null` the
    // whole time, on the way in and after its own consumption alike — this
    // must never re-arm the fallback stop below, or the moment that
    // consumption clears `takesFocus` (`TvCatalogNav.kt`'s own restore,
    // which does not wait a frame the way this page's own grant does) this
    // effect would fire the fallback and steal the remote right back from
    // wherever the sentinel just sent it — reproduced in Robolectric
    // (`TvMenuTest`/`TvSearchAndGenreTest`) before this guard existed.
    var arrived by remember { mutableStateOf(false) }
    LaunchedEffect(covered, restoreKey) { if (covered && restoreKey != null) arrived = false }
    // `covered`, read fresh rather than trusted through `takesFocus` alone:
    // the moment a real stop opens, `target` and the reset above both react
    // in the same composition `covered` itself turns `true` in, but
    // `rememberArrivalReady`'s own `false` for that same instant is one
    // recomposition behind — read here, `takesFocus` can still say `true`
    // for that one pass. `covered` never lags, so it is what actually keeps
    // this effect from granting into a page nothing can see yet.
    LaunchedEffect(target, takesFocus) {
        if (target == null || covered || !takesFocus || arrived) return@LaunchedEffect
        val itemIndex = included.indexOf(target.section).takeIf { it >= 0 } ?: return@LaunchedEffect
        // Scrolled into place — and its own composition confirmed present,
        // via the same item turning up in `visibleItemsInfo` — before the
        // request, always, for every section: a request fired the instant
        // a section's own composable mounts, with no guarantee the *outer*
        // list has scrolled that far yet, could ask a `FocusRequester` for
        // a node that is not part of the current layout at all, and would
        // then fall through to the chrome's own bar-pill fallback exactly
        // as if nothing had asked for it — the fix for a real regression
        // (a title opened from a lower section, closed again, restoring to
        // a pill instead of the poster) once each section requested its
        // own focus independently, racing this scroll rather than waiting
        // on it.
        listState.scrollToItem(itemIndex)
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }.first { info -> info.any { it.index == itemIndex } }
        entryFocus.getValue(target.section).requestFocus()
        arrived = true
    }

    val pagePadding = LocalTvPagePadding.current
    val hasCover = editorial.cover.isNotEmpty()
    val gutter = Modifier.padding(start = pagePadding.start, end = pagePadding.end)
    fun stopAt(section: TvHomeSection): Int? = target?.stop.takeIf { target?.section == section }

    // Keeps a section's own heading from landing flush against the bar
    // once scrolling (this page's own restore above, or the ordinary
    // default that follows *any* focus move) settles it at the very top of
    // the viewport — the same risk the cover's own words carry, fixed the
    // same way, but here at scroll time rather than as fixed padding: a
    // permanent gap this size between *every* pair of sections would make
    // the page needlessly sparse whenever neither is anywhere near the bar.
    // Reset back to the platform's own default one level down, inside each
    // section that scrolls *sideways* on its own (Continue, Recently added,
    // Latest series): this clearance is a vertical-axis concept — applied
    // to a horizontal `Row` too, it would reserve blank space on its own
    // *left* the bar never touches, for no reason.
    val defaultBringIntoView = LocalBringIntoViewSpec.current
    val homeBringIntoView = rememberTvBarClearanceBringIntoView()

    CompositionLocalProvider(LocalBringIntoViewSpec provides homeBringIntoView) {
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
                    // Entering the cover from the rows below scrolls only far
                    // enough to show the stop Up landed on, leaving the cover's
                    // own heading under the bar: asks for the whole cover
                    // instead, which the focused stop's own request is part of.
                    val coverInView = remember { BringIntoViewRequester() }
                    val wholeCover =
                        Modifier
                            .bringIntoViewRequester(coverInView)
                            .onFocusChanged { if (it.hasFocus) scope.launch { coverInView.bringIntoView() } }
                    Box(Modifier.remembersBand(TvHomeSection.COVER).then(wholeCover)) {
                        TvHomeCover(
                            films = editorial.cover,
                            watchlist = watchlist,
                            initialFilmId = stopAt(TvHomeSection.COVER)?.let { editorial.cover.getOrNull(it)?.setId },
                            onPlay = { onPlay(it.setId) },
                            onOpenTitle = onOpenTitle,
                            onToggleWatchlist = onToggleWatchlist,
                            arrivalFocus = coverFocus,
                            upExit = upExit,
                        )
                    }
                }
            }
            if (editorial.features.isNotEmpty()) {
                item(key = "features") {
                    Box(gutter.padding(top = Spacing.extraLarge).remembersBand(TvHomeSection.FEATURES)) {
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
                    Box(gutter.padding(top = Spacing.extraLarge).remembersBand(TvHomeSection.CONTINUE)) {
                        // Reset to the platform's own default for this band's
                        // inner, sideways-scrolling row of cards — see this
                        // function's own doc on `homeBringIntoView` above.
                        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoView) {
                            TvContinueBand(
                                cards = magazine.resumeCards,
                                quote = editorial.quote,
                                onPlay = onPlay,
                                onOpenTitle = onOpenTitle,
                                onSeeAllContinue = { onSeeAll("Continue") },
                                focusAt = stopAt(TvHomeSection.CONTINUE)?.takeIf { it < magazine.resumeCards.size },
                                focus = continueFocus,
                            )
                        }
                    }
                }
            }
            if (magazine.recentlyAdded.isNotEmpty() || editorial.thisMonth.isNotEmpty()) {
                item(key = "recent") {
                    Box(gutter.padding(top = Spacing.extraLarge).remembersBand(TvHomeSection.RECENT)) {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoView) {
                            TvRecentBand(
                                recentlyAdded = magazine.recentlyAdded,
                                totalFilms = magazine.recentlyAddedRow.total,
                                thisMonth = editorial.thisMonth,
                                onOpenTitle = onOpenTitle,
                                onSeeAllMovies = { onSeeAll("Movies") },
                                focusAt = stopAt(TvHomeSection.RECENT)?.takeIf { it < magazine.recentlyAdded.size },
                                focus = recentFocus,
                            )
                        }
                    }
                }
            }
            if (series.isNotEmpty()) {
                item(key = "series") {
                    Box(gutter.padding(top = Spacing.extraLarge).remembersBand(TvHomeSection.SERIES)) {
                        Column {
                            TvBandHeading(title = "Latest series", count = seriesTotal)
                            CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoView) {
                                TvPosterStrip(
                                    shows = series,
                                    onOpen = onOpenCollection,
                                    focusAt = stopAt(TvHomeSection.SERIES),
                                    focus = seriesFocus,
                                )
                            }
                        }
                    }
                }
            }
            if (courses.isNotEmpty()) {
                item(key = "courses") {
                    Box(gutter.padding(top = Spacing.extraLarge).remembersBand(TvHomeSection.COURSES)) {
                        Column {
                            TvBandHeading(title = "Latest courses", count = coursesTotal)
                            TvCourseList(
                                courses = courses,
                                onOpen = onOpenCollection,
                                focusAt = stopAt(TvHomeSection.COURSES),
                                focus = coursesFocus,
                            )
                        }
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
