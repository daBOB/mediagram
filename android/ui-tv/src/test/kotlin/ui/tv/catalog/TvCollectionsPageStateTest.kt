package ui.tv.catalog

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import catalog.Franchise
import model.Kind
import model.ListOfSets
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [TvCollectionsPage]: franchises and lists as the web's large cards, three
 * to a line, "＋ New list" as a pill under them — where arrival lands, and
 * where the remote goes from there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvCollectionsPageStateTest : TvScreenStateTest() {
    private val lists = listOf(ListOfSets("a", "Sunday", listOf("x")), ListOfSets("b", "Later", listOf("x", "y")))

    /**
     * A real regression, the same one [TvWallStateTest] pins for the grid and
     * `TvCoverSlideStateTest` for Home's own cover: with franchises on the
     * library, arrival scrolls this page's own list so the "Franchises"
     * heading is the topmost item — the same landing spot Down from the pill
     * reaches — and nothing cleared that heading from the bar the way
     * `TvHome`'s own vertical list and `TvWall`'s own grid already do.
     */
    @Test
    fun theFranchisesHeadingClearsTheBarOnceItsOwnRowIsScrolledToTheTop() {
        showPage(franchises = listOf(saga(1L, "A Saga", 2)), lists = emptyList())

        val heading = compose.onNodeWithText("Franchises").fetchSemanticsNode()
        val clearancePx = with(compose.density) { TvBarClearance.toPx() }
        assertTrue(
            heading.boundsInRoot.top >= clearancePx - 1f,
            "heading top at ${heading.boundsInRoot.top}px, short of its own ${clearancePx}px bar clearance",
        )
    }

    @Test
    fun franchisesAndListsAreCardsNamedInCapitalsWithTheirCountsSpelled() {
        showPage(franchises = listOf(saga(1L, "Adventure Saga", 2)), lists = lists)

        compose.onNodeWithText("ADVENTURE SAGA").assertExists()
        compose.onNodeWithText("two films", substring = false).assertExists()
        scrollTo("＋ New list")
        compose.onNodeWithText("SUNDAY").assertExists()
        compose.onNodeWithText("one title").assertExists()
        compose.onNodeWithText("LATER").assertExists()
        compose.onNodeWithText("two titles").assertExists()
    }

    @Test
    fun arrivalLandsOnTheFirstFranchiseAndComingBackOnTheCardThatWasOpened() {
        showPage(franchises = listOf(saga(1L, "Adventure Saga", 2), saga(2L, "Other Saga", 2)), lists = lists)
        compose.onNodeWithText("ADVENTURE SAGA").assertIsFocused()
        close()

        showPage(franchises = listOf(saga(1L, "Adventure Saga", 2), saga(2L, "Other Saga", 2)), lists = lists, restoreKey = "2")
        compose.onNodeWithText("OTHER SAGA").assertIsFocused()
        close()

        showPage(franchises = listOf(saga(1L, "Adventure Saga", 2)), lists = lists, restoreKey = "b")
        compose.onNodeWithText("LATER").assertIsFocused()
    }

    /** Three across: Right walks a line, Down keeps the column onto the next line, then into Your lists, then onto the pill. */
    @Test
    fun theRemoteWalksTheCardsByLineThenReachesYourListsAndTheNewListPill() {
        val franchises = (1L..4L).map { saga(it, "Saga $it", 2) }
        showPage(franchises = franchises, lists = lists)

        press(Key.DirectionRight)
        compose.onNodeWithText("SAGA 2").assertIsFocused()
        press(Key.DirectionLeft)
        press(Key.DirectionDown)
        compose.onNodeWithText("SAGA 4").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("SUNDAY").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("＋ New list").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithText("SUNDAY").assertIsFocused()
    }

    @Test
    fun aCardOpensWhatItNames() {
        var franchise: Long? = null
        var list: String? = null
        showPage(franchises = listOf(saga(7L, "Adventure Saga", 2)), lists = lists, onOpenFranchise = { franchise = it }, onOpenList = { list = it })

        compose.onNodeWithText("ADVENTURE SAGA").performSemanticsAction(SemanticsActions.OnClick)
        scrollTo("LATER")
        compose.onNodeWithText("LATER").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(7L, franchise)
        assertEquals("b", list)
    }

    @Test
    fun noFranchisesAndNoListsSaysSoAndLandsOnTheNewListPill() {
        showPage(franchises = emptyList(), lists = emptyList())

        compose.onNodeWithText("No lists yet.").assertExists()
        compose.onNodeWithText("＋ New list").assertIsFocused()

        compose.onNodeWithText("＋ New list").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Name for the list").assertExists()
    }

    private fun showPage(
        franchises: List<Franchise>,
        lists: List<ListOfSets>,
        restoreKey: String? = null,
        onOpenFranchise: (Long) -> Unit = {},
        onOpenList: (String) -> Unit = {},
    ) {
        show {
            TvCollectionsPage(
                franchises = franchises,
                lists = lists,
                setsById = emptyMap(),
                onOpenFranchise = onOpenFranchise,
                onOpenList = onOpenList,
                onCreateList = {},
                restoreKey = restoreKey,
            )
        }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasTestTag(TvCollectionsPageTestTag)).performScrollToNode(hasText(text))
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun saga(
        id: Long,
        name: String,
        films: Int,
    ) = Franchise(id = id, name = name, films = (0 until films).map { set("$id-$it", Kind.MOVIE, "Film $it", addedAt = 0) }, art = null)
}
