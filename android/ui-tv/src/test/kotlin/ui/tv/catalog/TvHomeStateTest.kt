package ui.tv.catalog

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import catalog.Entry
import catalog.EditorialPicks
import catalog.HomeRow
import catalog.MagazineHome
import catalog.RowContent
import catalog.SetCard
import model.Kind
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
                upExit = remember { FocusRequester() },
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
                upExit = remember { FocusRequester() },
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
                upExit = remember { FocusRequester() },
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

    /**
     * The regression a device caught: playing a Continue card writes its
     * own new position, which moves that card to the row's own front — and
     * on the box, that move alone was enough to pull the remote back to it
     * even after the viewer had already left it for something else, only
     * the remote missed every focusable thing on the way and landed on the
     * bar's own selected pill instead. [aSectionArrivingAboveTheRestoredOneDoesNotPullTheRemoteBackToIt]
     * above already proves reordering-after-arrival is supposed to leave a
     * moved-on remote alone; its own magazine swap there is equal to the
     * one it replaces, so `remember`'s own key never actually changes and
     * the effect never re-runs at all — this is the same rule with a
     * magazine that genuinely differs, which does re-run it.
     */
    @Test
    fun aReorderAfterArrivalMovesTheCardNotTheRemote() {
        val a = SetCard(set = film("a", "Card A"), caption = "", progress = 0.3f, watched = false)
        val b = SetCard(set = film("b", "Card B"), caption = "", progress = 0.3f, watched = false)
        val series = seriesRow("A Show")
        val magazine = mutableStateOf(continueMagazine(listOf(b, a)))
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
                upExit = remember { FocusRequester() },
                restoreKey = "a",
            )
        }
        compose.onNodeWithText("Card A").assertIsFocused()

        compose.onNodeWithText("A Show").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("A Show").assertIsFocused()

        // "a"'s own write lands only now, moving its card from second to
        // first in Continue — a real reorder, not the same list handed
        // back again.
        compose.runOnUiThread { magazine.value = continueMagazine(listOf(a, b)) }
        compose.waitForIdle()

        compose.onNodeWithText("A Show").assertIsFocused()
    }

    private fun film(
        id: String,
        title: String,
    ) = set(id, Kind.MOVIE, title, addedAt = 0)

    private fun continueMagazine(cards: List<SetCard>) =
        MagazineHome(
            editorial = EditorialPicks(cover = emptyList(), features = emptyList(), quote = null, thisMonth = emptyList()),
            resumeCards = cards,
            recentlyAdded = emptyList(),
            recentlyAddedRow = HomeRow(title = "Recently added", seeAll = "Movies", total = 0, content = RowContent.Entries(emptyList())),
        )

    private fun emptyMagazine() = continueMagazine(emptyList())

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
