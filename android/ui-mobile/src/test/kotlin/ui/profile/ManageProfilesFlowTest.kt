package ui.profile

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Manage profiles from the picker, each change answered by the core's rules. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManageProfilesFlowTest : ProfileGateHarness() {
    private fun limitOf(
        name: String,
        age: Int,
    ) = compose.onNode(hasText("FSK $age") and hasAnyAncestor(hasContentDescription("Age limit for $name")))

    @Test fun backInManageIsDoneAndForgetsWhoWasManaging() {
        open(andre, pins = mapOf("a" to "1111"))
        manageAs("andre", "1111")
        backOnTheScreen()
        compose.onNodeWithText("As andre").assertDoesNotExist()
        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onNodeWithText("Manage profiles").performClick()
        compose.onNodeWithText("Who are you?").assertExists()
    }

    @Test fun theAdminAddsAGrownUpWithAPinAndAKidThatStartsAtSix() {
        open(andre, pins = mapOf("a" to "1111"))
        manageAs("andre", "1111")

        named("Add a grown-up").performScrollTo().performTextInput("Carl")
        compose.onNodeWithText("Add a grown-up").performScrollTo().performClick()
        inDialog("A PIN for Carl").assertExists()
        pin("3333")
        pin("3333")
        val carl = household.core.profiles.single { it.name == "Carl" }
        assertEquals("3333", household.core.roles.pins[carl.id])
        assertFalse(carl.kids)

        named("Add a kid").performScrollTo().performTextInput("Lina")
        compose.onNodeWithText("Add a kid").performScrollTo().performClick()
        val lina = household.core.profiles.single { it.name == "Lina" }
        assertTrue(lina.kids)
        assertEquals(6, lina.kidsAge?.toInt())
        assertEquals("a", lina.parentId)
        limitOf("Lina", 6).assertIsSelected()
    }

    @Test fun aParentRaisesTheirKidsLimit() {
        open(andre, bea, tom, pins = mapOf("a" to "1111", "b" to "2222"))
        manageAs("Bea", "2222")
        compose.onNodeWithText("Grown-ups").assertDoesNotExist()
        limitOf("Tom", 6).performScrollTo().performClick()
        assertEquals(6, profile("t").kidsAge?.toInt())
        limitOf("Tom", 6).assertIsSelected()
    }

    @Test fun theAdminResetsAnotherGrownUpsPin() {
        open(andre, bea, pins = mapOf("a" to "1111", "b" to "2222"))
        manageAs("andre", "1111")
        compose.onNodeWithText("Reset PIN").performScrollTo().performClick()
        inDialog("A new PIN for Bea").assertExists()
        pin("4444")
        pin("4444")
        assertEquals("4444", household.core.roles.pins["b"])
    }

    @Test fun removingAGrownUpTakesTheirKidsWithThem() {
        open(andre, bea, tom, pins = mapOf("a" to "1111", "b" to "2222"))
        manageAs("andre", "1111")
        compose.onNode(hasContentDescription("Remove Bea")).performScrollTo().performClick()
        inDialog("Remove Bea, their kids, and everything they have watched?").assertExists()
        inDialog("Remove").performClick()
        compose.waitForIdle()
        assertEquals(listOf("a"), household.core.profiles.map { it.id })
        compose.onNodeWithText("Bea").assertDoesNotExist()
    }

    @Test fun aNameAlreadyTakenIsSaid() {
        open(andre, bea, pins = mapOf("a" to "1111"))
        manageAs("andre", "1111")
        named("Add a kid").performScrollTo().performTextInput("bea")
        compose.onNodeWithText("Add a kid").performScrollTo().performClick()
        compose.onNodeWithText("A profile with that name already exists.").assertExists()
        assertEquals(2, household.core.profiles.size)
    }
}
