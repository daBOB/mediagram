package ui.tv.catalog

import androidx.compose.ui.test.onNodeWithText
import catalog.Franchise
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * A real regression, the same one [TvWallStateTest] pins for the grid and
 * `TvCoverSlideStateTest` for Home's own cover: with franchises on the
 * library, arrival scrolls this page's own list so "Franchises · N" is the
 * topmost item — the same landing spot Down from the pill reaches — and
 * nothing cleared that heading from the bar the way `TvHome`'s own vertical
 * list and `TvWall`'s own grid already do for themselves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvCollectionsPageStateTest : TvScreenStateTest() {
    @Test
    fun theFranchisesHeadingClearsTheBarOnceItsOwnRowIsScrolledToTheTop() {
        val franchise = Franchise(id = 1L, name = "A Saga", films = emptyList(), art = null)
        show {
            TvCollectionsPage(
                franchises = listOf(franchise),
                lists = emptyList(),
                onOpenFranchise = {},
                onOpenList = {},
                onCreateList = {},
            )
        }

        val heading = compose.onNodeWithText("Franchises", substring = true).fetchSemanticsNode()
        val clearancePx = with(compose.density) { TvBarClearance.toPx() }
        assertTrue(
            heading.boundsInRoot.top >= clearancePx - 1f,
            "heading top at ${heading.boundsInRoot.top}px, short of its own ${clearancePx}px bar clearance",
        )
    }
}
