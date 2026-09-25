package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import designsystem.Spacing
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
 * wall, a set's id in the list — so coming back lands on it.
 */
@Composable
fun TvCollection(
    collection: Entry.Collection,
    info: TitleInfo?,
    watch: WatchSnapshot,
    posterPath: suspend (key: String) -> String?,
    onOpenTitle: (setId: String) -> Unit,
    onOpenSeason: (Division) -> Unit,
    restoreKey: String? = null,
) {
    val watchedIds = rememberWatchMarks(watch).watchedIds
    val seasons = remember(collection, watchedIds) { seasonPlatesOf(collection, watchedIds) }
    val header: @Composable () -> Unit = { CollectionHeader(collection, info) }
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
        TvCollectionRows(rows, watch, onOpenTitle, restoreKey, header)
    }
}

/**
 * The show's or course's name, then its art and facts. A show is rated as
 * a show, so its first episode speaks for all of it, as `series-header.js`
 * asks; it has no one year and no one runtime, so its rating is the only
 * fact. A course has no rating.
 */
@Composable
private fun CollectionHeader(
    collection: Entry.Collection,
    info: TitleInfo?,
) {
    val age = remember(collection) { firstItemOf(collection.divisions)?.ageLabel() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(text = collection.name, style = TvTypeScale.title)
        if (info != null || collection.posterPath != null || age != null) {
            TvTitleHeader(posterPath = collection.posterPath, title = collection.name, facts = age, info = info)
        }
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
