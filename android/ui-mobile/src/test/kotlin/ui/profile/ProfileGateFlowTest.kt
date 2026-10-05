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

    /**
     * A parent opens Manage from a kid's library and the tablet times out.
     * It comes back to the kid's library; the kid's next tap on its own name
     * must show the picker, not the parent's Manage — and the PIN Manage held
     * must be gone, so nothing protected can be done with it.
     */
    @Test fun leavingTheAppClosesManageAndForgetsTheParentsPin() {
        open(andre, bea, tom, pins = mapOf("a" to "1111", "b" to "2222"), chosen = "t")
        compose.onNodeWithText("Change").performClick()
        manageAs("Bea", "2222")

        leaveAndReturn()
        compose.onNodeWithText("Watching as Tom").assertExists()
        compose.onNodeWithText("Change").performClick()

        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onNodeWithText("As Bea").assertDoesNotExist()
        compose.runOnUiThread { manage.setKidsAge("t", 6) }
        compose.waitForIdle()
        assertEquals(12, profile("t").kidsAge?.toInt(), "a change went through on a PIN nobody gave again")
        compose.onNodeWithText("Manage profiles").performClick()
        compose.onNodeWithText("Who are you?").assertExists()
    }

    /** A first entry of a new PIN is not kept for whoever opens the app next. */
    @Test fun leavingTheAppDropsAHalfTypedNewPin() {
        open(andre, bea, pins = mapOf("a" to "1111"))
        compose.onNodeWithText("Bea").performClick()
        pin("2222")
        inDialog("The new PIN again").assertExists()

        leaveAndReturn()
        inDialog("The new PIN again").assertDoesNotExist()
        compose.onNodeWithText("Bea").performClick()
        inDialog("Choose a PIN for Bea").assertExists()
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
