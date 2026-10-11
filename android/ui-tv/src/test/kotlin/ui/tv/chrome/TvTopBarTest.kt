package ui.tv.chrome

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import catalog.ChromeCounts
import designsystem.TvStyle
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.common.RailItem
import ui.tv.catalog.TvScreenStateTest
import ui.tv.profile.TvChosenProfile

/** Google TV style's bar: the avatar and search first, the pills, then a gear that opens Settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvTopBarTest : TvScreenStateTest() {
    private var chosen: RailItem? = null

    @Test
    fun theAvatarAndSearchLeadThePillsAndTheGearEndsTheBar() {
        show(TvStyle.GOOGLE) { chrome() }
        val avatar = compose.onNodeWithContentDescription(AVATAR).getUnclippedBoundsInRoot()
        val search = compose.onNodeWithContentDescription("Search").getUnclippedBoundsInRoot()
        val home = compose.onNodeWithText("Home").getUnclippedBoundsInRoot()
        val gear = compose.onNodeWithContentDescription("Settings").getUnclippedBoundsInRoot()
        assertTrue(avatar.right <= search.left && search.right <= home.left && home.right <= gear.left)
    }

    @Test
    fun theGearOpensSettingsTheWayTheRailDid() {
        show(TvStyle.GOOGLE) { chrome() }
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(RailItem.SETTINGS, chosen)
    }

    @Test
    fun backFromTheGearLeavesTheAppRatherThanBeingSwallowed() {
        show(TvStyle.GOOGLE) { chrome() }
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Settings").assertIsFocused()
        back()
        assertFalse(pageTakesBack(), "no rail: Back from the bar closes the app")
    }

    @Composable
    private fun chrome() =
        TvLibraryChrome(
            pills = listOf("Home", "Movies", "Series", "Anime", "Documentaries", "Tutorials", "Collections"),
            selectedPill = 0,
            onSelectPill = {},
            railActive = null,
            counts = ChromeCounts(myList = 0, continueWatching = 0, perShelf = emptyMap(), collections = 0),
            tally = emptyList(),
            onRailSelect = { chosen = it },
            onSearch = {},
            profile = TvChosenProfile(name = "Ada", onChoose = {}),
            onMenu = {},
            focus = rememberTvChromeFocus(),
        ) {}

    private companion object {
        const val AVATAR = "Menu, watching as Ada"
    }
}
