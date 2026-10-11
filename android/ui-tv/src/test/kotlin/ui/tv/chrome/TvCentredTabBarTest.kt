package ui.tv.chrome

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import catalog.ChromeCounts
import designsystem.TvStyle
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ui.tv.catalog.TvScreenStateTest
import ui.tv.profile.TvChosenProfile

/** Apple TV style's bar: search, the pills and the avatar in one pill, centred; on a narrow screen the pills scroll inside it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvCentredTabBarTest : TvScreenStateTest() {
    @Test
    fun searchThePillsAndTheAvatarSitCentredInOneRow() {
        show(TvStyle.APPLE) { chrome() }
        val search = compose.onNodeWithContentDescription("Search").getUnclippedBoundsInRoot()
        val avatar = compose.onNodeWithContentDescription(AVATAR).getUnclippedBoundsInRoot()
        val home = compose.onNodeWithText("Home").getUnclippedBoundsInRoot()
        val width = compose.onRoot().getUnclippedBoundsInRoot().right
        assertTrue(search.right <= home.left && home.right <= avatar.left, "search, then the pills, then the avatar")
        assertTrue(abs(((search.left + avatar.right) / 2 - width / 2).value) < 2f, "centred: $search … $avatar in $width")
    }

    @Test
    fun theEndsOfTheRowReachThePillsAndDownReachesThePage() {
        show(TvStyle.APPLE) { chrome() }
        compose.onNodeWithText("Home").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Home").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithContentDescription("Search").assertIsFocused()
        compose.onNodeWithContentDescription("Search").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Home").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w640dp-h540dp")
    fun onANarrowScreenEveryPillAndBothEndsStayReachable() {
        show(TvStyle.APPLE) { chrome() }
        compose.onNodeWithText("Collections").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Collections").assertIsFocused()
        compose.onNodeWithText("Collections").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithContentDescription(AVATAR).assertIsFocused()
        val fades = compose.onNode(SemanticsMatcher.keyIsDefined(EdgeFadesKey)).fetchSemanticsNode().config[EdgeFadesKey]
        assertEquals(EdgeFades(start = true, end = false), fades)
        val bar = compose.onNodeWithContentDescription(AVATAR).getUnclippedBoundsInRoot()
        assertTrue(bar.right <= 640.dp, "the avatar stays on screen: $bar")
    }

    @Composable
    private fun chrome() =
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
            onMenu = {},
            focus = rememberTvChromeFocus(),
        ) {}

    private companion object {
        val DEPARTMENTS = listOf("Home", "Movies", "Series", "Anime", "Documentaries", "Tutorials", "Collections")
        const val AVATAR = "Menu, watching as Ada"
    }
}
