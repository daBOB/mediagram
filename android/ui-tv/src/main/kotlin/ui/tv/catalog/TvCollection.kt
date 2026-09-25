package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import catalog.Division
import catalog.Entry
import catalog.SeasonPlate
import catalog.firstItemOf
import catalog.rowsOf
import catalog.seasonPlatesOf
import designsystem.TvTypeScale
import java.io.File
import model.WatchSnapshot
import model.ageLabel
import ui.catalog.rememberPosterPath
import uniffi.mediagram_core.TitleInfo

/**
 * What is inside one show or course — the television twin of the phone's
 * `CollectionScreen`, deciding the same way what that is.
 *
 * A show with more than one season is a wall of season plates
 * ([seasonPlatesOf]'s rule, the web's and the phone's): a show drills into
 * seasons and only a season has artwork of its own to put on a plate.
 * Everything else — a course, and a show with just one season — is the
 * flat, indented list of [TvCollectionRows].
 *
 * [info] describes the show or the course itself. A course has neither a
 * provider entry nor artwork, so its header is left out entirely rather
 * than drawn empty: an empty block would claim the library looked and
 * found nothing, when nobody recorded anything.
 *
 * [restoreKey] names what was opened from here — a season's title on the
 * wall, a set's id in the list, or a genre from the header's links
 * ([onOpenGenre]) — so coming back lands on it. A genre sits above the wall
 * or the rows, so they leave the remote to it rather than taking it first.
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
) {
    val watchedIds = rememberWatchMarks(watch).watchedIds
    val seasons = remember(collection, watchedIds) { seasonPlatesOf(collection, watchedIds) }
    val genres = remember(collection) { firstItemOf(collection.divisions)?.genres.orEmpty() }
    val genreFocus = restoreKey?.takeIf { it in genres }
    val header: @Composable () -> Unit = { CollectionHeader(collection, info, onOpenGenre, genreFocus) }
    TvPage(takesArrivalFocus = genreFocus == null) {
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
