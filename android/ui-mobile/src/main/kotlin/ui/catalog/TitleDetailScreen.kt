package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import catalog.Franchise
import catalog.Shelf
import catalog.everyFilm
import catalog.factsLine
import catalog.filmDetailSections
import catalog.filmHeaderFacts
import catalog.filmOverviewFacts
import catalog.franchisesIn
import catalog.listedOf
import catalog.playedOf
import catalog.positionFor
import catalog.similarTo
import data.PortraitRequestLog
import data.ProgressPoint
import data.ResumePoint
import designsystem.Spacing
import model.MediaSet
import model.TitleCredits
import model.WatchSnapshot
import model.clockTime
import playback.FilmPreloadState
import ui.common.catalog.TitlePreloadUi
import ui.common.catalog.rememberTitleCredits
import ui.common.catalog.togetherJoinOf
import uniffi.mediagram_core.TitleInfo

/** Wide enough to recognise a poster by, narrow enough to leave the facts a column. */
private val POSTER_WIDTH = 120.dp

/**
 * A film's own page, laid out as a feature article: the opening spread
 * ([TitleSpread]), the pills that start it, then tabs — Overview, Cast (once
 * credits name somebody), Similar, Details. A Compose port of `film-page.js`.
 * While a watch-together session plays this film, Join stands in for Play.
 * [playOnTv] casts it from where Play or Resume would start. A film held
 * more than once has no Play of its own: its Versions list, one row and one
 * Play per copy ([onPlayVersion]), stands in for it, and its runtime moves
 * from the header into the rows, where it is what tells copies apart.
 *
 * [info] being null is ordinary rather than a failure — a course has no
 * provider entry, and a library assembled without a TMDB key has no rows at
 * all. Every block it would fill is left out instead of being shown empty.
 *
 * Parameters past [onOpenGenre] default to inert values so a test composes
 * the page with only what it checks; `LibraryTitleBranches` wires every one.
 */
@Composable
fun TitleDetailScreen(
    set: MediaSet,
    info: TitleInfo?,
    onPlay: () -> Unit,
    onOpenGenre: (String) -> Unit,
    editorsChoice: String? = null,
    onToggleEditorsChoice: (() -> Unit)? = null,
    watch: WatchSnapshot = WatchSnapshot.Empty,
    shelves: List<Shelf> = emptyList(),
    onOpenTitle: (String) -> Unit = {},
    onOpenFranchise: (Long) -> Unit = {},
    onOpenPerson: (Long) -> Unit = {},
    onToggleWatchlist: () -> Unit = {},
    titleCredits: suspend (String) -> TitleCredits = { TitleCredits.Empty },
    fetchPortrait: suspend (Long) -> String? = { null },
    portraits: PortraitRequestLog = PortraitRequestLog(),
    preload: TitlePreloadUi? = null,
    playOnTv: PlayOnTv? = null,
    heldIds: Set<String> = emptySet(),
    onPlayVersion: (MediaSet) -> Unit = {},
) {
    val several = set.versions.isNotEmpty()
    val resumeAt =
        remember(set, watch) {
            positionFor(set, set, watch)?.let { ResumePoint.resumeAt(ProgressPoint(it.at, it.duration)) }
        }
    val credits = rememberTitleCredits(set.posterKey, titleCredits)
    val join = togetherJoinOf(set.setId)
    val franchise =
        remember(set.setId, set.collectionId, shelves) {
            set.collectionId?.let { id -> franchisesIn(everyFilm(shelves)).find { it.id == id } }
        }

    val labels =
        buildList {
            add("Overview")
            if (credits.cast.isNotEmpty()) add("Cast")
            add("Similar")
            add("Details")
        }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TitleSpread(
            backdropPath = set.backdropPath ?: set.posterPath,
            title = set.title,
            facts = filmHeaderFacts(set),
            overview = info?.overview,
            tagline = info?.tagline,
        ) {
            TitlePills(
                playLabel =
                    when {
                        several -> null
                        join != null -> "Join"
                        else -> resumeAt?.let { "Resume from ${clockTime(it)}" } ?: "Play"
                    },
                onPlay = join?.join ?: onPlay,
                watchlisted = listedOf(set, watch.watchlist),
                onToggleWatchlist = onToggleWatchlist,
                editorsChoicePinned = editorsChoice == set.setId,
                onToggleEditorsChoice = onToggleEditorsChoice,
                preloadPill =
                    preload?.let { p ->
                        {
                            PreloadPill(
                                state = p.state,
                                onClick = p.onToggle,
                                queuedAheadLabel = p.queuedAheadLabel,
                                needsSpaceBudgetBytes = p.needsSpaceBudgetBytes,
                            )
                        }
                    },
                preloadRemoveItem =
                    preload?.takeIf { it.state == FilmPreloadState.Done }?.let { p ->
                        { dismiss: () -> Unit -> PreloadRemoveMenuItem(onRemove = p.onRemove, onDismiss = dismiss) }
                    },
                castPill = playOnTv?.let { tv -> { PlayOnTvPill(tv, set.setId, resumeAt ?: 0.0, onPlay) } },
            )
        }
        if (several) VersionsPanel(set, watch, heldIds, onPlayVersion, Modifier.padding(horizontal = Spacing.large).padding(bottom = Spacing.medium))
        preload?.takeIf { showsPreloadBar(it.state) || it.state is FilmPreloadState.NeedsSpace || it.serverLine != null }?.let { p ->
            Column(
                modifier = Modifier.padding(horizontal = Spacing.large).padding(bottom = Spacing.small),
                verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
            ) {
                if (showsPreloadBar(p.state)) PreloadProgressBar(state = p.state, onCancel = p.onToggle)
                if (p.state is FilmPreloadState.NeedsSpace) PreloadStorageLink(onClick = p.onOpenStorage)
                PreloadServerLine(p.serverLine)
            }
        }
        TitleTabs(labels, modifier = Modifier.padding(top = Spacing.small)) { tab ->
            Box(modifier = Modifier.padding(Spacing.large)) {
                when (tab) {
                    "Cast" -> CastPanel(credits, onOpenPerson, fetchPortrait, portraits)
                    "Similar" -> FilmSimilarTab(set, watch, shelves, onOpenTitle)
                    "Details" -> FilmDetailsTab(set)
                    else -> FilmOverviewTab(set, info, franchise, onOpenGenre, onOpenFranchise)
                }
            }
        }
    }
}

@Composable
private fun FilmOverviewTab(
    set: MediaSet,
    info: TitleInfo?,
    franchise: Franchise?,
    onOpenGenre: (String) -> Unit,
    onOpenFranchise: (Long) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        PosterArt(posterPath = set.posterPath, title = set.title, modifier = Modifier.width(POSTER_WIDTH))
        FactSheet(factRows(filmOverviewFacts(set, info, franchise), onOpenGenre, onOpenFranchise))
    }
}

@Composable
private fun FilmSimilarTab(
    set: MediaSet,
    watch: WatchSnapshot,
    shelves: List<Shelf>,
    onOpenTitle: (String) -> Unit,
) {
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    val similar =
        remember(set.setId, shelves, watchedIds) {
            similarTo(set, everyFilm(shelves), seen = { playedOf(it, watchedIds) })
        }
    PosterRow(
        similar.map { pick ->
            PosterRowItem(
                key = pick.setId,
                posterPath = pick.posterPath,
                title = pick.title,
                caption = factsLine(pick.year, pick.durationSecs),
                onClick = { onOpenTitle(pick.setId) },
            )
        },
    )
}

/** The file facts, or for a film held more than once each copy's under its label. */
@Composable
private fun FilmDetailsTab(set: MediaSet) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        for ((label, facts) in filmDetailSections(set)) {
            if (label != null) Text(label, style = MaterialTheme.typography.titleSmall)
            FactSheet(factRows(facts))
        }
    }
}
