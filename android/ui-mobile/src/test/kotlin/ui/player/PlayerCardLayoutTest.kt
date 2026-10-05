package ui.player

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/** A phone held sideways: 360dp of height for the card, its menu and the top bar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h360dp")
class PlayerCardLandscapeTest : PlayerCardScreenBase() {
    @Test
    fun aMenuStaysAboveItsButtonAndBelowTheTopBar() {
        open()
        compose.onNodeWithContentDescription("Speed").performClick()

        val menu = compose.onNodeWithTag(CardMenuTag).getBoundsInRoot()
        val button = compose.onNodeWithContentDescription("Speed").getBoundsInRoot()
        val marks = compose.onNodeWithText("Add to list").getBoundsInRoot()
        assertTrue(menu.bottom <= button.top, "the menu (${menu.top}..${menu.bottom}) covers its button at ${button.top}")
        assertTrue(menu.top >= marks.bottom, "the menu reaches up over the top bar's marks")
    }
}

/** A portrait phone: the sidebar takes the whole width, so nothing of the top bar may show through or take its taps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class PlayerCardNarrowSidebarTest : PlayerCardScreenBase() {
    @Test
    fun theFullWidthSidebarHasTheTopBarOutOfItsWay() {
        open(run = listOf("set-one", "set-two"))
        compose.onNodeWithContentDescription("Episodes").performClick()

        compose.onNodeWithText("Add to list").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close episodes").performClick()

        compose.onNodeWithTag(EpisodeSidebarTag).assertDoesNotExist()
        compose.onNodeWithText("Add to list").assertExists()
    }
}

/** A tablet: the sidebar is narrower than the screen, so the card moves over to clear it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
class PlayerCardSidebarClearanceTest : PlayerCardScreenBase() {
    @Test
    fun theCardStopsShortOfAnOpenSidebarAndComesBackWhenItCloses() {
        open(run = listOf("set-one", "set-two"))
        compose.onNodeWithContentDescription("Episodes").performClick()

        val sidebar = compose.onNodeWithTag(EpisodeSidebarTag).getBoundsInRoot()
        val card = compose.onNodeWithTag(PlayerCardTag).getBoundsInRoot()
        assertTrue(card.right <= sidebar.left - 11.5.dp, "the card ends at ${card.right}, the sidebar starts at ${sidebar.left}")

        compose.onNodeWithContentDescription("Close episodes").performClick()
        val after = compose.onNodeWithTag(PlayerCardTag).getBoundsInRoot()
        assertTrue(after.right > sidebar.left, "the card is back at its usual place")
    }

}
