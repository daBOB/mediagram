package ui.tv.catalog

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import catalog.Feature
import catalog.FeatureKind
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.catalog.home.TvHomeFeatures
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Home's feature cards in the web's three-column grid at the television's
 * 1280px: side by side, equal widths, one height for the row — never the
 * tablet's full-width first card over a pair.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvHomeFeaturesLayoutTest : TvScreenStateTest() {
    private fun feature(
        kind: FeatureKind,
        id: String,
        title: String,
    ) = Feature(kind, set(id, Kind.MOVIE, title, addedAt = 0).copy(tagline = "A tagline"))

    private fun cardOf(title: String): Rect {
        val bounds = compose.onNode(hasText(title, substring = true)).getBoundsInRoot()
        return Rect(bounds.left.value, bounds.top.value, bounds.right.value, bounds.bottom.value)
    }

    @Test
    fun threeFeaturesSitSideBySideAtOneHeight() {
        val long = "A Title Long Enough To Run Over Several Lines Of Its Card"
        show {
            TvHomeFeatures(
                listOf(feature(FeatureKind.EDITOR, "a", "First"), feature(FeatureKind.TRENDING, "b", long), feature(FeatureKind.STAFF, "c", "Third")),
                onOpenTitle = {},
            )
        }
        val (first, second, third) = listOf(cardOf("FIRST"), cardOf(long), cardOf("THIRD"))

        assertEquals(first.top, second.top)
        assertEquals(first.top, third.top)
        assertTrue(first.right <= second.left && second.right <= third.left, "left to right: $first $second $third")
        assertEquals(first.width, second.width, 0.5f)
        assertEquals(first.width, third.width, 0.5f)
        // The long title grows its card, and the grid stretches the other two to it.
        assertEquals(second.height, first.height, 0.5f)
        assertEquals(second.height, third.height, 0.5f)
    }

    @Test
    fun aSingleFeatureKeepsItsOneColumn() {
        show { TvHomeFeatures(listOf(feature(FeatureKind.EDITOR, "a", "Only")), onOpenTitle = {}) }
        val only = cardOf("ONLY")
        assertTrue(only.width < 960f / 2, "one third of the row, not all of it: ${only.width}")
    }
}
