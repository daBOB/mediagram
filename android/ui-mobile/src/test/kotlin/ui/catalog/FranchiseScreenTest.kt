package ui.catalog

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import catalog.Franchise
import catalog.FranchisePage
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One franchise's page as `renderFranchise` draws it: the overview inside the hero, then "In release order". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class FranchiseScreenTest : BrowsePageTest() {
    private val overview = "Two films about the same spice."
    private val page =
        FranchisePage(
            Franchise(
                id = 5, name = "Franchise 5", art = "art.jpg",
                films = listOf(film("one", year = 1999, backdrop = "one.jpg", collectionId = 5), film("two", year = 2003, collectionId = 5)),
            ),
            overview = overview,
        )

    private fun render(onOpenTitle: (String) -> Unit = {}) = show { FranchiseScreen(page, WatchSnapshot.Empty, columns = 3, onOpenTitle) }

    private fun assertOverviewSitsInsideTheHero() {
        render()
        val hero = compose.onNodeWithTag(DEPARTMENT_HERO_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(hero.contains(boundsOf(overview)), "expected the overview inside the hero $hero, got ${boundsOf(overview)}")
    }

    @Test fun theOverviewSitsInsideTheHeroOnAPhone() = assertOverviewSitsInsideTheHero()

    @Config(qualifiers = "w1280dp-h900dp")
    @Test fun theOverviewSitsInsideTheHeroOnAWideWindow() = assertOverviewSitsInsideTheHero()

    @Test fun theHeroCountsItsFilmsInWordsWithTheirYears() {
        render()
        compose.onNodeWithText("two films · 1999–2003").assertExists()
    }

    @Test fun theFilmsFollowInReleaseOrderUnderTheirHeading() {
        var opened: String? = null
        render(onOpenTitle = { opened = it })
        val heading = boundsOf("In release order")
        assertTrue(heading.bottom <= boundsOf("Film one").top && boundsOf("Film one").left < boundsOf("Film two").left)
        compose.onNode(hasText("Film two") and hasClickAction()).performClick()
        assertEquals("two", opened)
    }
}
