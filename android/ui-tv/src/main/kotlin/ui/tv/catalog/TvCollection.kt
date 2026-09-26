package ui.tv.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import catalog.Division
import catalog.Entry
import catalog.ResumeVerb
import catalog.SeasonPlate
import catalog.SeriesResumePick
import catalog.episodeShort
import catalog.firstItemOf
import catalog.ratingLabel
import catalog.rowsOf
import catalog.seasonPlatesOf
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import java.io.File
import model.TitleCredits
import model.WatchSnapshot
import model.ageLabel
import ui.catalog.rememberPosterPath
import ui.tv.TvTextRow
import uniffi.mediagram_core.TitleInfo

/**
 * What is inside one show or course — the television twin of the phone's
 * `CollectionScreen` and the web's series page: Episodes (a season wall,
 * [seasonPlatesOf]'s rule, or [TvCollectionRows]' flat list — a course, and
 * a show of just one season), About, Cast (only once [credits] names
 * somebody) and Similar (only once [similar] holds a show), the same split
 * [TvTitlePage] makes for a film.
 *
 * The header — name, art, facts, genre links, overview, and the
 * [SeriesResumePick] pill — sits on the Episodes tab, above the wall or the
 * rows, the same place the film page's own header sits on its Overview tab:
 * neither repeats it on the tabs beside it, which show only their own body.
 *
 * [restoreKey] names what was opened from here — a season's title on the
 * wall, a set's id in the list, or a genre from the header's links
 * ([onOpenGenre]) — so coming back lands on it, and also lands the viewer
 * back on the Episodes tab, the only one any of those keys name a stop on.
 *
 * [selected] survives the page's own state updates ([rememberSaveable],
 * keyed to [collection]'s own identity rather than to [credits]/[similar]
 * arriving a moment after the page does): a body that refetches must not
 * reset which tab is showing, the same rule [TvTitlePage]'s own tabs follow.
 */
@Composable
fun TvCollection(
    collection: Entry.Collection,
    info: TitleInfo?,
    watch: WatchSnapshot,
    posterPath: suspend (key: String) -> String?,
    onOpenTitle: (setId: String) -> Unit,
    onOpenSeason: (Division) -> Unit,
    onOpenGenre: (String) -> Unit = {},
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
    credits: TitleCredits = TitleCredits.Empty,
    onOpenPerson: (personId: Long) -> Unit = {},
    shouldRequestPortrait: (Long) -> Boolean = { false },
    fetchPortrait: suspend (Long) -> String? = { null },
    similar: List<Entry.Collection> = emptyList(),
    onOpenCollection: (key: String) -> Unit = {},
    resume: SeriesResumePick? = null,
    onResume: (setId: String) -> Unit = {},
) {
    val watchedIds = rememberWatchMarks(watch).watchedIds
    val seasons = remember(collection, watchedIds) { seasonPlatesOf(collection, watchedIds) }
    val genres = remember(collection) { firstItemOf(collection.divisions)?.genres.orEmpty() }
    val genreFocus = restoreKey?.takeIf { it in genres }
    val tabs =
        remember(credits, similar) {
            buildList {
                add("Episodes")
                add("About")
                if (credits.cast.isNotEmpty()) add("Cast")
                if (similar.isNotEmpty()) add("Similar")
            }
        }
    var selected by rememberSaveable(collection.key) { mutableIntStateOf(0) }
    if (selected >= tabs.size) selected = 0

    val header: @Composable () -> Unit = {
        Column {
            CollectionHeader(collection, info, onOpenGenre, genreFocus)
            resume?.let { pick -> SeriesResumeRow(pick, onResume) }
        }
    }
    TvPage(takesArrivalFocus = selected == 0 && genreFocus == null) {
        // No overscan padding of its own on this outer Column: `TvWall` and
        // `TvCollectionRows` already carry their own top/bottom overscan as
        // `contentPadding`, unchanged from before this phase — adding it here
        // too would double the gap above the Episodes tab's own content.
        // Only the tab row, which now sits above that content rather than
        // inside it, needs its own top inset.
        Column(modifier = Modifier.fillMaxSize()) {
            TvSectionTabs(
                titles = tabs,
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.padding(horizontal = Overscan.horizontal).padding(top = Overscan.vertical),
            )
            when (tabs[selected]) {
                "About" -> TvCollectionTabBody { TvSeriesAbout(info) }

                "Cast" -> TvCollectionTabBody { TvCastRow(credits, onOpenPerson, shouldRequestPortrait, fetchPortrait) }

                "Similar" -> TvCollectionTabBody { TvSimilarShows(similar, onOpenCollection) }

                else ->
                    if (seasons != null) {
                        TvWall(
                            items = seasons,
                            key = SeasonPlate::title,
                            restoreKey = restoreKey,
                            onOpen = { plate -> onOpenSeason(plate.division) },
                            header = header,
                            plate = { plate, modifier, onOpen -> TvSeasonPlate(collection, plate, posterPath, onOpen, modifier) },
                        )
                    } else {
                        val rows = remember(collection) { rowsOf(collection.divisions) }
                        TvCollectionRows(rows, watch, onOpenTitle, restoreKey, header, heldIds)
                    }
            }
        }
    }
}

/** A tab's own body, scrolled the way the phone's screens and the film page's own tabs already are. */
@Composable
private fun TvCollectionTabBody(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Overscan.horizontal, vertical = Spacing.medium),
    ) {
        content()
    }
}

/** About: the provider's rating, network and status — a show's counterpart to the film page's Details tab, minus the editor's-choice toggle, which is a film's own pin. */
@Composable
private fun TvSeriesAbout(info: TitleInfo?) {
    Column {
        ratingLabel(info?.rating)?.let { Text(text = it, style = TvTypeScale.body) }
        info?.network?.takeIf(String::isNotBlank)?.let { Text(text = it, style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small)) }
        info?.status?.takeIf(String::isNotBlank)?.let { Text(text = it, style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small)) }
    }
}

/**
 * One season's episodes — the television twin of the phone's
 * `SeasonScreen`, in the same rows [TvCollectionRows] draws for a whole
 * show or course: a season is just the one division the wall's plate stood
 * for, so it is shown the same way.
 *
 * Headed with the season's title at the size every other page's name
 * takes, as the phone's bar names it. The rows' own heading for the season
 * is left out under it: the same words twice, one above the other, with
 * nothing between them.
 */
@Composable
fun TvSeason(
    division: Division,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
) {
    val rows = remember(division) { rowsOf(listOf(division)).drop(1) }
    TvPage {
        TvCollectionRows(rows, watch, onOpenTitle, restoreKey, header = { Text(text = division.title, style = TvTypeScale.title) }, heldIds)
    }
}

/**
 * The show's or course's name, then its art and facts. A show is rated as
 * a show, so its first episode speaks for all of it, as `series-header.js`
 * asks; it has no one year and no one runtime, so its rating is the only
 * fact. A course has no rating.
 *
 * Art and an overview stand taller than the screen leaves above the first
 * season, so arriving scrolls the name away. The name and the overview are
 * both stops the remote can rest on, then — as on the title page, where a
 * stop is the only way a remote scrolls — so Up from the first season reads
 * the overview and Up again brings the name back. A course with nothing to
 * show but its name stays short enough that nothing scrolls it away.
 */
@Composable
private fun CollectionHeader(
    collection: Entry.Collection,
    info: TitleInfo?,
    onOpenGenre: (String) -> Unit,
    genreFocus: String?,
) {
    // A show is rated and tagged as a show, so its first episode speaks for
    // all of it, as the phone's header asks.
    val firstEpisode = remember(collection) { firstItemOf(collection.divisions) }
    val age = firstEpisode?.ageLabel()
    val genres = firstEpisode?.genres.orEmpty()
    if (info != null || collection.posterPath != null || age != null || genres.isNotEmpty()) {
        TvTitleHeader(
            posterPath = collection.posterPath,
            title = collection.name,
            facts = age,
            info = info,
            genres = genres,
            onOpenGenre = onOpenGenre,
            genreFocus = genreFocus,
            readableOverview = true,
            readableTitle = true,
        )
    } else {
        Text(text = collection.name, style = TvTypeScale.title)
    }
}

/**
 * "Resume"/"Continue"/"Play" on a show's own page — [catalog.seriesResume]'s
 * own pick, worded the way the phone's Continue wall words its own resume
 * caption, with [ResumeVerb] naming which of the three it is.
 */
@Composable
private fun SeriesResumeRow(
    pick: SeriesResumePick,
    onResume: (setId: String) -> Unit,
) {
    val verb =
        when (pick.verb) {
            ResumeVerb.RESUME -> "Resume"
            ResumeVerb.CONTINUE -> "Continue"
            ResumeVerb.PLAY -> "Play"
        }
    TvTextRow(
        text = "▶ $verb · ${episodeShort(pick.set)}",
        onClick = { onResume(pick.set.setId) },
        modifier = Modifier.padding(top = Spacing.small),
    )
}

/**
 * A season's plate, whose artwork falls back in three steps — its own
 * poster, then the show's, then the initials [TvPlate] draws on its own —
 * so the season's is the only one looked up here.
 */
@Composable
private fun TvSeasonPlate(
    collection: Entry.Collection,
    plate: SeasonPlate,
    posterPath: suspend (key: String) -> String?,
    onOpen: () -> Unit,
    modifier: Modifier,
) {
    val seasonPoster = rememberPosterPath(plate.posterKey, posterPath)
    TvPlate(
        title = plate.title,
        posterPath = (seasonPoster ?: collection.posterPath)?.let(::File),
        onOpen = onOpen,
        modifier = modifier,
        caption = plate.caption,
        watched = plate.watched,
    )
}
