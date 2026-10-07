package ui.tv.catalog

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import catalog.EditorialPicks
import catalog.Entry
import catalog.Latest
import catalog.MagazineHome
import catalog.SetCard
import model.Kind
import model.WatchSnapshot
import org.junit.Assert.assertEquals
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
        val series = latestSeries("A Show")
        show {
            TvHome(
                magazine = emptyMagazine(),
                latest = series,
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
        val series = latestSeries("A Show", "Another Show")
        show {
            TvHome(
                magazine = emptyMagazine(),
                latest = series,
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
        val series = latestSeries("A Show")
        val magazine = mutableStateOf(emptyMagazine())
        show {
            TvHome(
                magazine = magazine.value,
                latest = series,
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
        val series = latestSeries("A Show")
        val magazine = mutableStateOf(continueMagazine(listOf(b, a)))
        show {
            TvHome(
                magazine = magazine.value,
                latest = series,
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

    /**
     * What the box showed after leaving the player: the played card's own
     * position write lands with Home already on screen and moves it to the
     * front of Continue. The remote must go with the title, not stay on the
     * slot it used to occupy, which now draws a different one — and the row
     * must scroll with it, or the card sits past the row's own edge.
     */
    @Test
    fun aRestoredCardKeepsTheRemoteWhenItsRowReordersUnderIt() {
        val a = SetCard(set = film("a", "Card A"), caption = "", progress = 0.3f, watched = false)
        val others = (1..5).map { SetCard(set = film("o$it", "Card $it"), caption = "", progress = 0.3f, watched = false) }
        val magazine = mutableStateOf(continueMagazine(others + a))
        show {
            TvHome(
                magazine = magazine.value,
                latest = latestSeries("A Show"),
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

        compose.runOnUiThread { magazine.value = continueMagazine(listOf(a) + others) }
        compose.waitForIdle()

        compose.onNodeWithText("Card A").assertIsFocused()
        val node = compose.onNodeWithText("Card A").fetchSemanticsNode()
        assertEquals(node.size.width.toFloat(), node.boundsInRoot.width, 1f)
    }

    /**
     * The box's own shape: the *second* card played, not the last — its own
     * row still carries a `focusRequester` conditionally attached by index
     * at the moment this grants (`TvContinueBand`'s own doc on
     * `TvResumeCard`'s `ownRequester`), so a card that is not yet the
     * restored stop must already look, structurally, exactly like one that
     * is. Passed before the fix in this harness too — not because the
     * mechanism this guards against does not exist, but because Robolectric
     * does not model the native `FocusTargetNode.onReset` a real device's
     * own lazy-layout reuse pool fires when a modifier's own presence
     * toggles; verified by hand-reverting the fix and re-running this
     * unchanged, still green. The box is what actually proves this one.
     */
    @Test
    fun aRestoredSecondCardKeepsTheRemoteWhenItMovesToTheFront() {
        val b = SetCard(set = film("b", "Card B"), caption = "", progress = 0.4f, watched = false)
        val others = listOf(SetCard(set = film("a", "Card A"), caption = "", progress = 0.3f, watched = false))
        val more = (2..4).map { SetCard(set = film("o$it", "Card $it"), caption = "", progress = 0.3f, watched = false) }
        val magazine = mutableStateOf(continueMagazine(others + b + more))
        show {
            TvHome(
                magazine = magazine.value,
                latest = latestSeries("A Show"),
                watch = WatchSnapshot.Empty,
                listState = rememberLazyListState(),
                onPlay = {},
                onOpenTitle = {},
                onOpenCollection = {},
                onToggleWatchlist = { _, _ -> },
                onSeeAll = {},
                upExit = remember { FocusRequester() },
                restoreKey = "b",
            )
        }
        compose.onNodeWithText("Card B").assertIsFocused()

        compose.runOnUiThread { magazine.value = continueMagazine(listOf(b) + others + more) }
        compose.waitForIdle()

        compose.onNodeWithText("Card B").assertIsFocused()
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
            recentlyAddedTotal = 0,
        )

    private fun emptyMagazine() = continueMagazine(emptyList())

    private fun latestSeries(vararg names: String) =
        Latest(
            movies = emptyList(),
            series =
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
            courses = emptyList(),
            moviesTotal = 0,
            seriesTotal = names.size,
            coursesTotal = 0,
        )
}
