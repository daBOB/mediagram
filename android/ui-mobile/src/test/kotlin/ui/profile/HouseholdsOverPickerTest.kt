package ui.profile

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import catalog.profile.DONE
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The owner's view on the Profiles page the picker's Manage profiles opens,
 * with nobody chosen: the owner's PIN given to their tile there is dropped on
 * the way out, so whoever comes to the picker next is asked it again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HouseholdsOverPickerTest : ProfileGateHarness() {
    private fun ownersTile() = compose.onNode(tile("andre"))

    @Test fun doneOverThePickerDropsTheOwnersPin() {
        open(andre.copy(owner = true), bea, pins = mapOf("a" to "1234"))
        compose.onNodeWithText("Manage profiles").performClick()
        ownersTile().performScrollTo().performClick()
        pin("1234")
        compose.onNodeWithText("As andre").assertExists()
        compose.onNodeWithText(DONE).performScrollTo().performClick()

        compose.onNodeWithText("Manage profiles").performClick()
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertExists()
        ownersTile().performScrollTo().performClick()
        inDialog(HouseholdWords.OWNERS_PIN).assertExists()
    }
}
