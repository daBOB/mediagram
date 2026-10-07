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
import androidx.compose.ui.unit.dp
import catalog.CatalogTab
import catalog.Department
import catalog.KeptKind
import catalog.Latest
import catalog.MagazineHome
import designsystem.Overscan
import designsystem.Spacing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
 * the ones near the viewport ever composed, so a heavy section (a poster
 * strip's `AsyncImage`s, a course list's own cards) never builds before the
 * remote is anywhere near it.
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
 * [latest] gives the two bands that follow the magazine header — Latest
 * series and Latest courses; [magazine] already carries the cover, the
 * features and its own "Recently added" row. [onSeeAll] opens the tab a
 * band is a window onto.
 */
@Composable
internal fun TvHome(
    magazine: MagazineHome,
    latest: Latest,
    watch: WatchSnapshot,
    listState: LazyListState,
    onPlay: (setId: String) -> Unit,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onToggleWatchlist: (setId: String, listed: Boolean) -> Unit,
    onSeeAll: (CatalogTab) -> Unit,
    // Where Up from the cover's own action row leads — the bar's own
    // selected pill, the same stop Back already reaches from anywhere on
    // this page ([ui.tv.chrome.TvLibraryChrome]'s own `BackHandler`).
    upExit: FocusRequester,
    restoreKey: String? = null,
) {
    val editorial = magazine.editorial
    val watchlist = remember(watch) { watch.watchlist.toSet() }
    val series = latest.series
    val courses = latest.courses

    // Only stops with a focus requester of their own are restorable; any other
    // key (the quote, This month's rows) falls back to homeTargetOf's default.
    val sections =
        remember(editorial, magazine, series, courses) {
            listOf(
                DeptSection(TvHomeSection.COVER, editorial.cover.map { it.setId }),
                DeptSection(TvHomeSection.FEATURES, editorial.features.map { it.set.setId }),
                DeptSection(TvHomeSection.CONTINUE, magazine.resumeCards.map { it.set.setId }),
                DeptSection(TvHomeSection.RECENT, magazine.recentlyAdded.map { it.setId }),
                DeptSection(TvHomeSection.SERIES, series.map { it.key }),
                DeptSection(TvHomeSection.COURSES, courses.map { it.key }),
            )
        }
    val included = remember(sections) { sections.filter { it.stops.isNotEmpty() }.map { it.id } }
    // Every section the list below draws an item for, in its order — the
    // one place that decides which items exist, so the arrival below scrolls
    // to, and waits for, the item that really holds its stop. Not `included`:
    // Continue drawn for its quote alone, or Recently added for This month
    // alone, is an item with no stop in it, and indexing past it by
    // `included` scrolled one item short of every band below.
    val hasCover = editorial.cover.isNotEmpty()
    val drawn =
        remember(editorial, magazine, series, courses) {
            buildList {
                if (hasCover) add(TvHomeSection.COVER)
                if (editorial.features.isNotEmpty()) add(TvHomeSection.FEATURES)
                if (magazine.resumeCards.isNotEmpty() || editorial.quote != null) add(TvHomeSection.CONTINUE)
                if (magazine.recentlyAdded.isNotEmpty() || editorial.thisMonth.isNotEmpty()) add(TvHomeSection.RECENT)
                if (series.isNotEmpty()) add(TvHomeSection.SERIES)
                if (courses.isNotEmpty()) add(TvHomeSection.COURSES)
            }
        }
    // The band the remote was last in, saved with the catalog like the
    // list's own scroll: a title two bands carry at once — a new upload
    // that is also trending — comes back to the band it was opened from.
    // Read, not keyed, so moving between bands never re-aims the arrival.
    var lastSection by rememberSaveable { mutableStateOf<TvHomeSection?>(null) }
    val target = remember(sections, restoreKey) { homeTargetOf(sections, restoreKey, lastSection) }

    fun Modifier.remembersBand(section: TvHomeSection) = onFocusChanged { if (it.hasFocus) lastSection = section }
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
    var arrived by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        if (target == null || !takesFocus || arrived) return@LaunchedEffect
        val itemIndex = drawn.indexOf(target.section).takeIf { it >= 0 } ?: return@LaunchedEffect
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

    // What the list's own `focusRestorer` falls back to when focus enters it
    // with no remembered child — which is always: the chrome's own `onExit`
    // around bar and page replaces the restorer's, so the restorer never
    // saves one. The arrival's own request above enters this list too (from
    // the bar's Home pill, where Android puts the remote the moment the
    // pushed frame's focused button is removed, or from nothing at all), so
    // a fallback naming the first section redirected that request whenever
    // the first section was still composed — a lead feature card leaves the
    // cover half in view, so Back from it restores a scroll that composes
    // the cover again: the remote went to the cover's Watch now, which the
    // arrival had just scrolled out of view, and once the list let that item
    // go, to the bar's first pill. Pointing at the arrival's own stop until
    // it has landed makes the redirect land where the request was going;
    // afterwards, an entry from the bar (Down onto the page) still lands on
    // the first section, as it always has.
    val firstStop = included.firstOrNull()?.let(entryFocus::getValue) ?: coverFocus
    val enterFallback = target?.takeUnless { arrived }?.let { entryFocus.getValue(it.section) } ?: firstStop

    val pagePadding = LocalTvPagePadding.current
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
                    .focusRestorer(fallback = enterFallback),
            contentPadding = PaddingValues(top = if (hasCover) 0.dp else pagePadding.top, bottom = pagePadding.bottom + Overscan.horizontal),
        ) {
            if (TvHomeSection.COVER in drawn) {
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
            if (TvHomeSection.FEATURES in drawn) {
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
            if (TvHomeSection.CONTINUE in drawn) {
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
                                onSeeAllContinue = { onSeeAll(CatalogTab.Kept(KeptKind.CONTINUE)) },
                                focusAt = stopAt(TvHomeSection.CONTINUE)?.takeIf { it < magazine.resumeCards.size },
                                focus = continueFocus,
                            )
                        }
                    }
                }
            }
            if (TvHomeSection.RECENT in drawn) {
                item(key = "recent") {
                    Box(gutter.padding(top = Spacing.extraLarge).remembersBand(TvHomeSection.RECENT)) {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoView) {
                            TvRecentBand(
                                recentlyAdded = magazine.recentlyAdded,
                                totalFilms = magazine.recentlyAddedTotal,
                                thisMonth = editorial.thisMonth,
                                onOpenTitle = onOpenTitle,
                                onSeeAllMovies = { onSeeAll(CatalogTab.Dept(Department.MOVIES)) },
                                focusAt = stopAt(TvHomeSection.RECENT)?.takeIf { it < magazine.recentlyAdded.size },
                                focus = recentFocus,
                            )
                        }
                    }
                }
            }
            if (TvHomeSection.SERIES in drawn) {
                item(key = "series") {
                    Box(gutter.padding(top = Spacing.extraLarge).remembersBand(TvHomeSection.SERIES)) {
                        Column {
                            TvBandHeading(title = "Latest series", count = latest.seriesTotal)
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
            if (TvHomeSection.COURSES in drawn) {
                item(key = "courses") {
                    Box(gutter.padding(top = Spacing.extraLarge).remembersBand(TvHomeSection.COURSES)) {
                        Column {
                            TvBandHeading(title = "Latest courses", count = latest.coursesTotal)
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
