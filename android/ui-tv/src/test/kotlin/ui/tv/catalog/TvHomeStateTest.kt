package ui.tv.catalog

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import catalog.Entry
import catalog.HomeRow
import catalog.RowContent
import designsystem.Overscan
import designsystem.Spacing
import model.Kind
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [TvHome]'s focus: it lands on arrival, and then it stays wherever the
 * viewer moved it — rows arriving above while the viewer browses, as
 * Continue does the moment a fetch or a play finishes, move the plates but
 * never the remote.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvHomeStateTest : TvScreenStateTest() {
    private val latest = row("Latest films", films(3).reversed())

    @Test
    fun aRowArrivingAboveDoesNotPullTheRemoteBackToTheRestoredPlate() {
        val rows = mutableStateOf(listOf(latest))
        show { TvHome(rows = rows.value, watch = WatchSnapshot.Empty, onOpenTitle = {}, onOpenCollection = {}, onSeeAll = {}, restoreKey = "film-0") }
        compose.onNodeWithText("Film 0").assertIsFocused()

        compose.onNodeWithText("Film 1").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnUiThread { rows.value = listOf(row("Continue", listOf(set("other", Kind.MOVIE, "Other", addedAt = 9))), latest) }
        compose.waitForIdle()

        compose.onNodeWithText("Film 1").assertIsFocused()
    }

    /**
     * "See all" stands in the heading, not in a seventh slot beside the
     * plates, so a Home plate is as wide as a shelf wall's six-across plate
     * — room for a caption's year and runtime together.
     */
    @Test
    fun seeAllTakesNoWidthFromTheSixPlates() {
        val full = row("Latest films", films(6).reversed()).copy(seeAll = "Movies", total = 10)
        show { TvHome(rows = listOf(full), watch = WatchSnapshot.Empty, onOpenTitle = {}, onOpenCollection = {}, onSeeAll = {}) }

        val plate = compose.onNode(hasText("Film 5") and hasClickAction()).fetchSemanticsNode()
        val seeAll = compose.onNodeWithText("See all").fetchSemanticsNode()
        val root = compose.onRoot().fetchSemanticsNode().size.width
        val wallPlate = with(compose.density) { (root - 2 * Overscan.horizontal.toPx() - 5 * Spacing.medium.toPx()) / 6 }
        assertEquals(wallPlate, plate.size.width.toFloat(), 1f)
        assertTrue(seeAll.boundsInRoot.bottom <= plate.boundsInRoot.top, "See all sits on the heading line, above the plates")
    }

    private fun row(
        title: String,
        sets: List<model.MediaSet>,
    ) = HomeRow(title = title, seeAll = null, total = sets.size, content = RowContent.Entries(sets.map(Entry::Film)))
}
