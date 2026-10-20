package ui.profile

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
 * The owner's Households part on the Profiles page the picker's Manage
 * profiles opens, with nobody chosen: the owner's PIN given there is dropped
 * on the way out, so whoever comes to the picker next is asked it again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HouseholdsOverPickerTest : ProfileGateHarness() {
    private fun ownerButton() = compose.onNode(hasText(HouseholdWords.OWNER) and hasClickAction())

    @Test fun doneOverThePickerDropsTheOwnersPin() {
        open(andre.copy(owner = true), bea, pins = mapOf("a" to "1234"))
        compose.onNodeWithText("Manage profiles").performClick()
        ownerButton().performScrollTo().performClick()
        pin("1234")
        compose.onNodeWithText(HouseholdWords.OWNER_NOTE).assertDoesNotExist()
        compose.onNodeWithText(DONE).performScrollTo().performClick()

        compose.onNodeWithText("Manage profiles").performClick()
        compose.onNodeWithText(HouseholdWords.OWNER_NOTE).performScrollTo().assertExists()
        ownerButton().performScrollTo().performClick()
        inDialog(HouseholdWords.OWNERS_PIN).assertExists()
    }
}
