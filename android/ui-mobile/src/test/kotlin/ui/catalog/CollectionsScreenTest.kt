package ui.catalog

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import catalog.Franchise
import model.ListOfSets
import model.MediaSet
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Collections as `renderCollectionsPage` draws it: franchises and lists as the same 4:3 cards, then a "New list" pill. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class CollectionsScreenTest : BrowsePageTest() {
    private val dune = listOf(film("dune", year = 2021, backdrop = "dune.jpg", collectionId = 5), film("dune2", year = 2024, collectionId = 5))

    private fun render(
        lists: List<ListOfSets> = listOf(ListOfSets("l1", "Rainy days", listOf("dune", "dune2", "gone"))),
        onOpenFranchise: (Long) -> Unit = {},
        onOpenList: (String) -> Unit = {},
    ) = show {
        CollectionsScreen(
            franchises = listOf(Franchise(5, "Dune", dune, "dune.jpg")),
            lists = lists,
            setsById = dune.associateBy(MediaSet::setId),
            onOpenFranchise = onOpenFranchise,
            onOpenList = onOpenList,
            onCreateList = {},
            onOpenTitle = {},
        )
    }

    private fun card(name: String) = compose.onNode(hasText(name) and hasClickAction())

    @Test fun aFranchiseAndAListAreFourByThreeCardsNamedAndCountedInWords() {
        render()
        for ((name, meta) in listOf("DUNE" to "two films", "RAINY DAYS" to "three titles")) {
            val bounds = card(name).getUnclippedBoundsInRoot()
            assertTrue(abs(bounds.width / bounds.height - 4f / 3f) < 0.02f, "$name: expected 4:3, got ${bounds.width} x ${bounds.height}")
            assertTrue(bounds.contains(boundsOf(meta)), "$name: expected \"$meta\" on the card")
        }
    }

    @Test fun eachCardOpensWhatItNames() {
        var franchise: Long? = null
        var list: String? = null
        render(onOpenFranchise = { franchise = it }, onOpenList = { list = it })
        card("DUNE").performClick()
        card("RAINY DAYS").performClick()
        assertEquals(5L to "l1", franchise to list)
    }

    /**
     * The section on its own, not the whole screen: the name dialog the
     * screen opens next holds a text field that never lets Compose go idle
     * under Robolectric, and what is new here is the pill, not the dialog.
     */
    @Test fun newListIsAPillUnderAnEmptySection() {
        var asked = false
        show { LazyColumn { listsSection(lists = emptyList(), setsById = emptyMap(), onOpen = {}, onNewList = { asked = true }) } }
        assertTrue(boundsOf("No lists yet.").bottom <= boundsOf("＋ New list").top)
        compose.onNodeWithText("＋ New list").performClick()
        assertTrue(asked)
    }
}
