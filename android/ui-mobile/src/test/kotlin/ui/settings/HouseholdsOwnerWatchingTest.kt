package ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import catalog.profile.MANAGE_PROFILES
import catalog.profile.SWITCH_PROFILE
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.profile.tile

/**
 * Who the owner's part opens for ([HouseholdsOwnerHarness]): the owner
 * watching, at once; over the picker shown again after them — which is
 * anybody's — only by the owner's tile and PIN, as the web's page over its
 * picker does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HouseholdsOwnerWatchingTest : HouseholdsOwnerHarness() {
    private val home = "Admin andre · 3 profiles"

    @Test
    fun openForTheOwnerWatchingAndClosedOverThePickerAfterThem() {
        openAs(owner)
        compose.onNodeWithText(home).assertExists()
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertDoesNotExist()

        compose.onNode(hasText(SWITCH_PROFILE) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText(MANAGE_PROFILES).performClick()
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertExists()
        compose.onNode(tile("andre") and hasText(HouseholdWords.OWNER)).performScrollTo().assertExists()
        compose.onAllNodes(hasText(home)).assertCountEquals(0)
    }
}
