package ui.tv.catalog

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import catalog.Entry
import catalog.EditorialPicks
import catalog.HomeRow
import catalog.MagazineHome
import catalog.RowContent
import model.WatchSnapshot
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

/**
 * [TvHome]'s focus: it lands on the cover's own Watch now when there is a
 * cover, on the first section with anything in it when there is not, and
 * then it stays wherever the viewer moved it — a section's own content
 * arriving or reordering while the viewer is elsewhere never pulls the
 * remote back to a restored stop a second time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvHomeStateTest : TvScreenStateTest() {
    @Test
    fun withNoCoverArrivalLandsOnTheFirstSectionWithAnythingInIt() {
        val series = seriesRow("A Show")
        show {
            TvHome(
                magazine = emptyMagazine(),
                rows = listOf(series),
                watch = WatchSnapshot.Empty,
                listState = rememberLazyListState(),
                onPlay = {},
                onOpenTitle = {},
                onOpenCollection = {},
                onToggleWatchlist = { _, _ -> },
                onSeeAll = {},
            )
        }

        compose.onNodeWithText("A Show").assertIsFocused()
    }

    @Test
    fun aRestoreKeyLandsOnThatSeriesPosterRatherThanTheDefault() {
        val series = seriesRow("A Show", "Another Show")
        show {
            TvHome(
                magazine = emptyMagazine(),
                rows = listOf(series),
                watch = WatchSnapshot.Empty,
                listState = rememberLazyListState(),
                onPlay = {},
                onOpenTitle = {},
                onOpenCollection = {},
                onToggleWatchlist = { _, _ -> },
                onSeeAll = {},
                restoreKey = "show-another-show",
            )
        }

        compose.onNodeWithText("Another Show").assertIsFocused()
    }

    @Test
    fun aSectionArrivingAboveTheRestoredOneDoesNotPullTheRemoteBackToIt() {
        val series = seriesRow("A Show")
        val magazine = mutableStateOf(emptyMagazine())
        show {
            TvHome(
                magazine = magazine.value,
                rows = listOf(series),
                watch = WatchSnapshot.Empty,
                listState = rememberLazyListState(),
                onPlay = {},
                onOpenTitle = {},
                onOpenCollection = {},
                onToggleWatchlist = { _, _ -> },
                onSeeAll = {},
                restoreKey = "show-a-show",
            )
        }
        compose.onNodeWithText("A Show").assertIsFocused()

        // Focus moved on, then a fresh Continue card appears above "Latest
        // series" — as it does the moment something is played elsewhere —
        // which must not steal the remote back to the restored poster.
        compose.onNodeWithText("A Show").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnUiThread { magazine.value = emptyMagazine() }
        compose.waitForIdle()

        compose.onNodeWithText("A Show").assertIsFocused()
    }

    private fun emptyMagazine() =
        MagazineHome(
            editorial = EditorialPicks(cover = emptyList(), features = emptyList(), quote = null, thisMonth = emptyList()),
            resumeCards = emptyList(),
            recentlyAdded = emptyList(),
            recentlyAddedRow = HomeRow(title = "Recently added", seeAll = "Movies", total = 0, content = RowContent.Entries(emptyList())),
        )

    private fun seriesRow(vararg names: String) =
        HomeRow(
            title = "Latest series",
            seeAll = "Series",
            total = names.size,
            content =
                RowContent.Entries(
                    names.map { name ->
                        Entry.Collection(
                            key = "show-${name.lowercase().replace(" ", "-")}",
                            kind = catalog.CollectionKind.SHOW,
                            name = name,
                            posterPath = null,
                            posterKey = null,
                            count = 1,
                            chapters = 1,
                            divisions = emptyList(),
                        )
                    },
                ),
        )
}
