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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import catalog.Franchise
import catalog.Shelf
import catalog.factsLine
import catalog.filmsOf
import catalog.franchisesIn
import catalog.humanDuration
import catalog.languageName
import catalog.ratingLabel
import catalog.similarTo
import data.ProgressPoint
import data.ResumePoint
import designsystem.Spacing
import model.MediaSet
import model.TitleCredits
import model.WatchSnapshot
import model.ageLabel
import model.clockTime
import model.humanSize
import player.bitrateLabel
import player.hdrLabel
import uniffi.mediagram_core.TitleInfo

/** Wide enough to recognise a poster by, narrow enough to leave the facts a column. */
private val POSTER_WIDTH = 120.dp

/**
 * A film's own page, laid out as a feature article: the opening spread
 * ([TitleSpread]), the pills that start it, then tabs — Overview, Cast (once
 * credits name somebody), Similar, Details. A Compose port of `film-page.js`.
 *
 * [info] being null is ordinary rather than a failure — a course has no
 * provider entry, and a library assembled without a TMDB key has no rows at
 * all. Every block it would fill is left out instead of being shown empty.
 *
 * Every parameter beyond [set]/[info]/[onPlay]/[onOpenGenre] defaults to
 * something inert, so a caller not yet wired for credits, franchises or a
 * person page keeps compiling — a real `titleCredits`/`fetchPortrait`/
 * watchlist-toggle wiring is what turns the Cast tab and "My List" pill on.
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
    shouldRequestPortrait: (Long) -> Boolean = { false },
) {
    val resumeAt =
        remember(set.setId, watch) {
            watch.progress.find { it.setId == set.setId }?.let { ResumePoint.resumeAt(ProgressPoint(it.at, it.duration)) }
        }
    val credits = rememberTitleCredits(set.posterKey, titleCredits)
    val franchise =
        remember(set.setId, set.collectionId, shelves) {
            set.collectionId?.let { id -> franchisesIn(filmsOf(shelves)).find { it.id == id } }
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
            facts = factsLine(set.year, set.durationSecs, set.ageLabel()),
            overview = info?.overview,
            tagline = info?.tagline,
        )
        TitlePills(
            playLabel = resumeAt?.let { "Resume from ${clockTime(it)}" } ?: "Play",
            onPlay = onPlay,
            watchlisted = set.setId in watch.watchlist,
            onToggleWatchlist = onToggleWatchlist,
            editorsChoicePinned = editorsChoice == set.setId,
            onToggleEditorsChoice = onToggleEditorsChoice,
            modifier = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.small),
        )
        TitleTabs(labels, modifier = Modifier.padding(top = Spacing.small)) { tab ->
            Box(modifier = Modifier.padding(Spacing.large)) {
                when (tab) {
                    "Cast" -> CastPanel(credits, onOpenPerson, fetchPortrait, shouldRequestPortrait)
                    "Similar" -> FilmSimilarTab(set, watch, shelves, onOpenTitle)
                    "Details" -> FactSheet(filmDetailsRows(set))
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
        FactSheet(
            listOf(
                "Released" to textFact(set.year?.takeIf { it > 0 }?.toString()),
                "Runtime" to textFact(humanDuration(set.durationSecs)),
                "Rated" to textFact(set.ageLabel()),
                "Score" to textFact(ratingLabel(info?.rating)),
                "Genres" to genreFact(set.genres, onOpenGenre),
                "Part of" to franchise?.let { linkFact(it.name) { onOpenFranchise(it.id) } },
            ),
        )
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
            similarTo(set, filmsOf(shelves), seen = { it.setId in watchedIds })
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

/** What the file is: the questions a viewer asks when a title will not play — a Compose port of `film-page.js#details`. */
private fun filmDetailsRows(set: MediaSet): List<Pair<String, (@Composable () -> Unit)?>> =
    listOf(
        "Quality" to textFact(listOfNotNull(set.quality, hdrLabel(set.hdr)).joinToString(" · ").takeIf(String::isNotEmpty)),
        "Video" to textFact(set.vcodec?.takeIf(String::isNotEmpty)),
        "Audio" to textFact(set.acodec?.takeIf(String::isNotEmpty)),
        "Subtitles" to textFact(set.subtitleLanguages.takeIf { it.isNotEmpty() }?.joinToString(", ", transform = ::languageName)),
        "Container" to textFact(set.container.takeIf(String::isNotEmpty)),
        "Size" to textFact(set.totalBytes.takeIf { it > 0 }?.let(::humanSize)),
        "Bitrate" to textFact(bitrateLabel(set.totalBytes, set.durationSecs)),
        "Parts" to textFact(set.partCount.takeIf { it > 1 }?.toString()),
    )

/**
 * The block that describes something: its artwork beside its facts, then
 * what a provider said about it.
 *
 * Shared by a course's own screen (the one collection kind this phase does
 * not give a feature-article page) — a course and a film's overview are
 * described the same way and only differ in what they can say: [facts] is a
 * file's year and runtime, and a course has neither, so it is null there.
 */
@Composable
internal fun TitleHeader(
    posterPath: String?,
    title: String,
    facts: String?,
    info: TitleInfo?,
    modifier: Modifier = Modifier,
    genres: List<String> = emptyList(),
    onOpenGenre: (String) -> Unit = {},
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            PosterArt(posterPath = posterPath, title = title, modifier = Modifier.width(POSTER_WIDTH))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                facts?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
                ratingLabel(info?.rating)?.let { rating ->
                    Text(text = rating, style = MaterialTheme.typography.bodyMedium)
                }
                GenreLinks(genres, onOpenGenre)
            }
        }
        info?.tagline?.takeIf(String::isNotBlank)?.let { tagline ->
            Text(
                text = "“$tagline”",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        info?.overview?.takeIf(String::isNotBlank)?.let { overview ->
            Text(text = overview, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
