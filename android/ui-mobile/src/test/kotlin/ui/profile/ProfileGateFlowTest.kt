package ui.profile

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The way in: a first profile, the household's admin, a grown-up's PIN, a kid's free tile, and Back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileGateFlowTest : ProfileGateHarness() {
    @Test fun aFirstRunMakesTheAdminWithAPinTypedTwiceThenEntersByItsTile() {
        open()
        named("Create").performTextInput("Ann")
        compose.onNodeWithText("Create").performClick()
        inDialog("A PIN for Ann").assertExists()
        pin("1234")
        inDialog("The new PIN again").assertExists()
        pin("1234")

        val ann = household.core.profiles.single()
        assertTrue(ann.admin, "the first profile runs the household")
        assertEquals("1234", household.core.roles.pins[ann.id])
        compose.onNodeWithText("Create the first profile — it runs this household").assertDoesNotExist()
        compose.onNodeWithText("Manage profiles").assertExists()

        compose.onNodeWithText("Ann").performClick()
        pin("1234")
        compose.onNodeWithText("Watching as Ann").assertExists()
    }

    @Test fun twoDifferentEntriesOfANewPinAreRefusedAndAskedAgain() {
        open()
        named("Create").performTextInput("Ann")
        compose.onNodeWithText("Create").performClick()
        pin("1234")
        pin("4321")
        inDialog("The two PINs are not the same.").assertExists()
        assertTrue(household.core.profiles.isEmpty())
    }

    @Test fun aHouseholdWithNobodyRunningItIsClaimedWithAGrownUpsPin() {
        open(bea, tom, pins = mapOf("b" to "2222"))
        compose.onNodeWithText("Who runs this household?").assertExists()
        compose.onNode(hasText("Bea") and hasAnyAncestor(hasTestTag(WhoRunsTag))).performClick()
        inDialog("Bea’s PIN").assertExists()
        pin("2222")

        assertTrue(profile("b").admin)
        compose.onNodeWithText("Who runs this household?").assertDoesNotExist()
        compose.onNodeWithText("Watching as", substring = true).assertDoesNotExist()
    }

    @Test fun aGrownUpEntersWithTheirPinAndAWrongOneIsSaid() {
        open(andre, pins = mapOf("a" to "1111"))
        compose.onNodeWithText("andre").performClick()
        pin("9999")
        inDialog("Wrong PIN.").assertExists()
        compose.onNodeWithText("Watching as andre").assertDoesNotExist()
        pin("1111")
        compose.onNodeWithText("Watching as andre").assertExists()
    }

    @Test fun fiveWrongPinsInARowMakeThatProfileWait() {
        open(andre, pins = mapOf("a" to "1111"))
        compose.onNodeWithText("andre").performClick()
        repeat(5) { pin("9999") }
        pin("1111")
        inDialog("Too many wrong PINs. Try again in 60 s.").assertExists()
        compose.onNodeWithText("Watching as andre").assertDoesNotExist()
    }

    @Test fun aGrownUpFromBeforePinsChoosesOneOnTheWayIn() {
        open(andre, bea, pins = mapOf("a" to "1111"))
        compose.onNodeWithText("Bea").performClick()
        inDialog("Choose a PIN for Bea").assertExists()
        pin("2222")
        pin("2222")
        compose.onNodeWithText("Watching as Bea").assertExists()
        assertEquals("2222", household.core.roles.pins["b"])
    }

    @Test fun aKidOpensWithoutAPin() {
        open(andre, bea, tom, pins = mapOf("a" to "1111", "b" to "2222"))
        compose.onNodeWithText("Tom").performClick()
        compose.onNodeWithText("Watching as Tom").assertExists()
    }

    @Test fun backOnThePinCancelsItAndBackOnTheReopenedPickerStays() {
        open(andre, bea, pins = mapOf("a" to "1111", "b" to "2222"), chosen = "a")
        compose.onNodeWithText("Change").performClick()
        compose.onNodeWithText("Bea").performClick()
        inDialog("Bea’s PIN").assertExists()

        backOnTheDialog()
        inDialog("Bea’s PIN").assertDoesNotExist()
        compose.onNodeWithText("Stay as I am").assertExists()

        backOnTheScreen()
        compose.onNodeWithText("Watching as andre").assertExists()
        assertFalse(isFinishing(), "Back left the app")
    }
}
