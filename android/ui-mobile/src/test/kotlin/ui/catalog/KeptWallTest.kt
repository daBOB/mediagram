package ui.catalog

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import catalog.KeptKind
import data.settings.ShelfView
import model.MediaSet
import model.Progress
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Continue and My List offer the Movies shelf's List · Grid, as the web's `keptWall` does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class KeptWallTest : BrowsePageTest() {
    private val watch = WatchSnapshot.Empty.copy(progress = listOf(Progress("a", at = 600.0, duration = 6_000.0, updatedAt = 1)))

    private fun render(
        view: ShelfView,
        sets: List<MediaSet> = listOf(film("a")),
        onChoose: (ShelfView) -> Unit = {},
        onFinish: (String) -> Unit = {},
    ) = show { KeptWall(KeptKind.CONTINUE, sets, watch, columns = 3, ShelfViewChoice(view, onChoose), onOpenTitle = {}, onFinish = onFinish) }

    @Test fun theListIsARowAcrossThePageWithItsFactsAndMarkFinished() {
        var finished: String? = null
        render(ShelfView.LIST, onFinish = { finished = it })
        // The year is a row's fact; a plate's caption has no room for it.
        compose.onNodeWithText("2000", substring = true).assertExists()
        val row = compose.onNode(hasText("Film a") and hasClickAction())
        assertTrue(row.getUnclippedBoundsInRoot().width >= 350.dp, "expected a full-width row, got ${row.getUnclippedBoundsInRoot().width}")
        compose.onNodeWithText("Mark finished").performClick()
        assertEquals("a", finished)
    }

    @Test fun theGridIsPlatesAndTheSwitchChoosesTheList() {
        var chosen: ShelfView? = null
        render(ShelfView.GRID, onChoose = { chosen = it })
        compose.onNodeWithText("2000", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Mark finished").assertExists()
        compose.onNodeWithText("List").performClick()
        assertEquals(ShelfView.LIST, chosen)
    }

    @Test fun anEmptyWallOffersNoChoice() {
        render(ShelfView.GRID, sets = emptyList())
        compose.onNodeWithText("Nothing started yet.").assertExists()
        compose.onNodeWithText("List").assertDoesNotExist()
    }
}
