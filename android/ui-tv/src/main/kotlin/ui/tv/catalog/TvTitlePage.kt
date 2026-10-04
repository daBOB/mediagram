package ui.tv.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import catalog.factsLine
import catalog.filmDetailFacts
import catalog.franchisesIn
import data.ProgressPoint
import data.ResumePoint
import designsystem.Overscan
import designsystem.Spacing
import model.MediaSet
import model.Progress
import model.TitleCredits
import model.ageLabel
import model.clockTime
import playback.FilmPreloadState
import uniffi.mediagram_core.TitleInfo

/**
 * What a title is, before playing it — the television's `film-page.js`: the
 * opening spread ([TvTitleSpread]) with the pills that start it, then tabs —
 * Overview, Cast (once credits name somebody), Similar, Details.
 *
 * Play takes the remote the moment the page appears. It is the one thing
 * here to press and the reason a viewer came, so a single centre press
 * from the plate that opened this starts the title. It says "Resume from"
 * where [progress] is a place the player will actually carry on from —
 * [ResumePoint.resumeAt], the rule the player and the web's film page both
 * start by, so a glance at the opening or a position in the credits says
 * Play.
 *
 * Its genres open their own pages through [onOpenGenre]; coming back from
 * one, [restoreKey] names it and that link takes the remote instead of Play.
 * The same [restoreKey] also picks up whichever of Cast or Similar it was
 * opened from — a person's or a similar film's id — and drives both which
 * tab is initially showing and which of its plates takes focus, recomputed
 * fresh on every composition: this page is torn down and rebuilt on the way
 * back from a person's page rather than staying alive underneath it, so a
 * plain `rememberSaveable` restart has nothing of its own to restore from.
 *
 * [info] being null is ordinary rather than a failure, for the phone's
 * reason: a course has no provider entry, and a library assembled without
 * a TMDB key has none at all, so each block it would fill is left out.
 *
 * Cast is offered as a tab only once [credits] name somebody, as `cast.js`
 * adds it; Similar always, saying so when nothing in the library is like
 * it. [onToggleEditorsChoice] is the ⋯'s one choice, `null` on a kids
 * profile, since a household mark is not a kids profile's to make.
 */
@Composable
internal fun TvTitlePage(
    set: MediaSet,
    info: TitleInfo?,
    progress: Progress?,
    onPlay: () -> Unit,
    onOpenGenre: (String) -> Unit = {},
    restoreKey: String? = null,
    credits: TitleCredits = TitleCredits.Empty,
    onOpenPerson: (personId: Long) -> Unit = {},
    shouldRequestPortrait: (Long) -> Boolean = { false },
    fetchPortrait: suspend (Long) -> String? = { null },
    similar: List<MediaSet> = emptyList(),
    onOpenTitle: (setId: String) -> Unit = {},
    allFilms: List<MediaSet> = emptyList(),
    onOpenFranchise: (id: Long) -> Unit = {},
    editorsChoice: String? = null,
    onToggleEditorsChoice: (() -> Unit)? = null,
    preload: TvTitlePreloadUi? = null,
    watchlisted: Boolean = false,
    onToggleWatchlist: () -> Unit = {},
) {
    val play = remember { FocusRequester() }
    val preloadPlate = remember { FocusRequester() }
    val resumeAt = remember(progress) { progress?.let { ResumePoint.resumeAt(ProgressPoint(it.at, it.duration)) } }
    val franchise =
        remember(set.setId, set.collectionId, allFilms) {
            set.collectionId?.let { id -> franchisesIn(allFilms).find { it.id == id } }
        }
    val tabs =
        remember(credits) {
            buildList {
                add("Overview")
                if (credits.cast.isNotEmpty()) add("Cast")
                add("Similar")
                add("Details")
            }
        }
    // See the class doc: restoreKey, not rememberSaveable, is what survives.
    val initialTab =
        remember(tabs, credits, similar, restoreKey) {
            when {
                restoreKey == null -> 0
                credits.onCastTab(restoreKey) -> tabs.indexOf("Cast")
                similar.any { it.setId == restoreKey } -> tabs.indexOf("Similar")
                else -> 0
            }
        }
    var selected by rememberSaveable(set.setId) { mutableIntStateOf(initialTab) }
    // Credits arrive after the page does — on the way back from a person they are still
    // empty at first, so [initialTab] starts at 0 and the saved state keeps it. Once they
    // land and name what was opened, switch to its tab.
    LaunchedEffect(initialTab) { if (restoreKey != null && initialTab > 0) selected = initialTab }
    if (selected >= tabs.size) selected = 0
    val scroll = rememberScrollState()

    TvPage {
        Column(modifier = Modifier.fillMaxSize().testTag(TvTitlePageBodyTag).verticalScroll(scroll).padding(bottom = Overscan.vertical)) {
            TvTitleSpread(
                backdropPath = set.backdropPath ?: set.posterPath,
                title = set.title,
                facts = factsLine(set.year, set.durationSecs, set.ageLabel(), set.genres),
                overview = info?.overview,
                tagline = info?.tagline,
            ) {
                TvPillRow {
                    TvSpreadPill(
                        text = resumeAt?.let { "▶ Resume from ${clockTime(it)}" } ?: "▶ Play",
                        onClick = onPlay,
                        solid = true,
                        modifier = Modifier.focusRequester(play),
                    )
                    preload?.let { p ->
                        TvPreloadPlate(
                            state = p.state,
                            onClick = p.onToggle,
                            focusRequester = preloadPlate,
                            queuedAheadLabel = p.queuedAheadLabel,
                            needsSpaceBudgetBytes = p.needsSpaceBudgetBytes,
                        )
                        // Remove takes the plate it removed with it — land back
                        // on the main plate rather than wherever focus search
                        // finds next.
                        if (p.state is FilmPreloadState.Done) {
                            TvPreloadRemovePlate(onClick = { preloadPlate.requestFocus(); p.onRemove() })
                        }
                    }
                    TvListPill(watchlisted = watchlisted, onToggle = onToggleWatchlist)
                    TvMorePill(editorsChoiceChoice(onToggleEditorsChoice, pinned = editorsChoice == set.setId))
                }
                preload?.let { p ->
                    TvPreloadDetailLines(p.state, p.serverLine, p.onOpenStorage, modifier = Modifier.padding(top = Spacing.small))
                }
            }
            TvSectionTabs(
                titles = tabs,
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.padding(horizontal = Overscan.horizontal).revealsPageBelow { scroll.viewportSize },
            )
            Box(modifier = Modifier.padding(horizontal = Overscan.horizontal).padding(top = Spacing.medium)) {
                when (tabs[selected]) {
                    "Cast" -> TvCastRow(credits, onOpenPerson, shouldRequestPortrait, fetchPortrait, restoreKey)
                    "Similar" -> TvSimilarFilms(similar, onOpenTitle, restoreKey)
                    "Details" -> TvFactSheet(filmDetailFacts(set))
                    else -> TvFilmOverview(set, info, franchise, onOpenGenre, onOpenFranchise, genreFocus = restoreKey)
                }
            }
        }
    }
    // Arrival only — keyed on the title, not the tab, so pressing Overview
    // leaves the remote on its tab rather than throwing it back up to Play.
    LaunchedEffect(set.setId) {
        if (selected == 0 && restoreKey !in set.genres) play.requestFocus()
    }
}

/** The ⋯'s editor's-choice toggle, worded as the phone's menu words it, or none on a kids profile. */
internal fun editorsChoiceChoice(
    onToggle: (() -> Unit)?,
    pinned: Boolean,
): List<Pair<String, () -> Unit>> =
    onToggle?.let { listOf((if (pinned) "Remove as editor's choice" else "Make editor's choice") to it) }.orEmpty()
