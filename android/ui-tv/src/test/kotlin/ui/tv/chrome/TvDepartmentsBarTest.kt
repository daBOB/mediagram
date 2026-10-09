package ui.tv.chrome

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import catalog.ChromeCounts
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ui.tv.TvFocus
import ui.tv.catalog.TvScreenStateTest
import ui.tv.fontSizeOf
import ui.tv.profile.TvChosenProfile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The departments bar at the web's sizes (`shell.css:39-60`): pills at 14px
 * with no count beside them — the rail's footer carries the totals — and
 * ⋮ folded into the avatar, so seven departments, search and the avatar fit
 * the box's 960dp. Where the row still overflows it scrolls, and only an end
 * that actually clips something is faded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvDepartmentsBarTest : TvScreenStateTest() {
    @Test
    fun theSevenDepartmentsFitTheBoxsBarWithNoEdgeFaded() {
        show { chrome() }

        assertEquals(EdgeFades(start = false, end = false), fades())
    }

    @Test
    @Config(qualifiers = "w640dp-h540dp")
    fun anOverflowingRowFadesOnlyTheEndItClips() {
        show { chrome() }
        assertEquals(EdgeFades(start = false, end = true), fades(), "at rest: more to the right, nothing to the left")

        compose.onNodeWithText("Collections").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()

        assertEquals(EdgeFades(start = true, end = false), fades(), "scrolled to its end: the start is what clips now")
    }

    /**
     * The fade masks through an offscreen layer, which clips to its own
     * bounds where the scroll alone let a focused pill's grown ring spill
     * above and below the row: on the box the ring was cut top and bottom.
     */
    @Test
    fun theFadeLayerLeavesAFocusedPillsRingRoomAboveAndBelow() {
        show { chrome() }
        val pill = compose.onNodeWithText("Series").getUnclippedBoundsInRoot()
        val row = compose.onNode(SemanticsMatcher.keyIsDefined(EdgeFadesKey)).getUnclippedBoundsInRoot()

        val grown = (pill.bottom - pill.top) * (TvFocus.Scale - 1f) / 2f + TvFocus.BorderWidth
        assertTrue(row.top <= pill.top - grown && row.bottom >= pill.bottom + grown, "the ring fits the layer: pill $pill, layer $row")
    }

    @Test
    fun aPillIsItsNameAtTheWebsTabSize() {
        show { chrome() }

        compose.onNodeWithText("Movies").assertExists()
        assertEquals(14f, compose.fontSizeOf("Movies").value, "`.departments a`, 0.875rem")
    }

    @Test
    fun theAvatarOpensWhatTheMenuButtonDidAndThereIsNoMenuButton() {
        var opened = 0
        show { chrome(onMenu = { opened++ }) }

        compose.onAllNodesWithContentDescription("Menu").assertCountEquals(0)
        compose.onNodeWithContentDescription(AVATAR).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(1, opened)
    }

    @Test
    fun theAvatarIsTheBarsLastStop() {
        show { chrome() }
        val search = compose.onNodeWithContentDescription("Search")
        val avatar = compose.onNodeWithContentDescription(AVATAR)
        search.performSemanticsAction(SemanticsActions.RequestFocus)

        search.performKeyInput { pressKey(Key.DirectionRight) }
        avatar.assertIsFocused()

        avatar.performKeyInput { pressKey(Key.DirectionRight) }
        avatar.assertIsFocused()

        avatar.performKeyInput { pressKey(Key.DirectionLeft) }
        search.assertIsFocused()

        search.performKeyInput { pressKey(Key.DirectionRight) }
        avatar.performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(CONTENT).assertIsFocused()
    }

    private fun fades(): EdgeFades = compose.onNode(SemanticsMatcher.keyIsDefined(EdgeFadesKey)).fetchSemanticsNode().config[EdgeFadesKey]

    @Composable
    private fun chrome(onMenu: () -> Unit = {}) =
        TvLibraryChrome(
            pills = DEPARTMENTS,
            selectedPill = 0,
            onSelectPill = {},
            railActive = null,
            counts = ChromeCounts(myList = 0, continueWatching = 0, perShelf = emptyMap(), collections = 0),
            tally = emptyList(),
            onRailSelect = {},
            onSearch = {},
            profile = TvChosenProfile(name = "Ada", onChoose = {}),
            onMenu = onMenu,
            focus = rememberTvChromeFocus(),
        ) { Box(Modifier.size(200.dp).testTag(CONTENT).focusable()) }

    private companion object {
        val DEPARTMENTS = listOf("Home", "Movies", "Series", "Anime", "Documentaries", "Tutorials", "Collections")
        const val AVATAR = "Menu, watching as Ada"
        const val CONTENT = "content"
    }
}
