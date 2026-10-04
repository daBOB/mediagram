package ui.catalog

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import catalog.GenreIndexEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Genres page as `renderGenres` draws it: the page head, then 16:9 tiles named over their art, as many across as fit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class GenresIndexScreenTest : BrowsePageTest() {
    private val genres = listOf(GenreIndexEntry("Drama", 3, "drama.jpg"), GenreIndexEntry("Western", 1, null))

    private fun render(onOpenGenre: (String) -> Unit = {}) = show { GenresIndexScreen(genres, onOpenGenre) }

    private fun tile(name: String) = compose.onNode(hasText(name) and hasClickAction())

    @Test fun theHeadSpellsHowManyGenresThereAre() {
        render()
        compose.onNodeWithText("Genres").assertExists()
        compose.onNodeWithText("TWO GENRES").assertExists()
    }

    @Test fun aTileIsSixteenByNineWithItsNameAndSpelledCountInside() {
        render()
        val bounds = tile("Drama").getUnclippedBoundsInRoot()
        assertTrue(abs(bounds.width / bounds.height - 16f / 9f) < 0.02f, "expected 16:9, got ${bounds.width} x ${bounds.height}")
        assertTrue(bounds.contains(boundsOf("Drama")) && bounds.contains(boundsOf("three titles")))
        // A tile with no art still names itself, on the page's own ground.
        compose.onNodeWithText("one title").assertExists()
    }

    @Test fun onAPhoneTheTilesStackOneAcross() {
        render()
        assertEquals(tile("Drama").getUnclippedBoundsInRoot().left, tile("Western").getUnclippedBoundsInRoot().left)
    }

    @Config(qualifiers = "w800dp-h1280dp")
    @Test fun onATabletTheTilesSitSideBySide() {
        render()
        assertEquals(tile("Drama").getUnclippedBoundsInRoot().top, tile("Western").getUnclippedBoundsInRoot().top)
    }

    @Test fun aTileOpensItsGenre() {
        var opened: String? = null
        render(onOpenGenre = { opened = it })
        tile("Western").performClick()
        assertEquals("Western", opened)
    }
}
