package ui.tv.profile

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import catalog.profile.DONE
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** The owner's view walked and changed by remote ([TvOwnerViewHarness]): every household's rows, a header's actions, a person's choices. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvManageHouseholdsStateTest : TvOwnerViewHarness() {
    @Test
    fun everyHouseholdsHeaderPeopleAndAddsAreReachedWalkingDown() {
        val walk =
            listOf(
                "Home · Admin andre", "andre · Owner", "Lea · Kid", ADD_A_GROWN_UP, ADD_A_KID,
                "Loft · Admin Ida", "Ida · Admin", ADD_A_GROWN_UP, ADD_A_KID,
                "Flat · Admin Maja", "Maja · Admin", "Tim · Kid", ADD_A_GROWN_UP, ADD_A_KID,
                HouseholdWords.CREATE_A_HOUSEHOLD, DONE,
            )
        walk.forEachIndexed { index, row ->
            if (index > 0) press(Key.DirectionDown)
            focusedOn(row)
        }
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        assertEquals(TvHouseholdStep.CreateName(), went.last())
    }

    @Test
    fun aHeadersOkOpensItsHouseholdsActions() {
        walkTo(hasText("Loft · Admin Ida", substring = true))
        press(Key.DirectionCenter)
        assertEquals(TvHouseholdStep.Actions("loft"), went.last())
    }

    @Test
    fun anotherHouseholdsRowsAreChangedByRemoteAndAKidsRowKeepsIt() {
        walkTo(hasText("Tim · Kid", substring = true))
        press(Key.DirectionCenter)
        // The current limit holds the remote; the other is one step down.
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals(listOf("age tim 12"), calls)
        focusedOn("Tim · Kid · Maja · FSK 12")
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        assertEquals(listOf("age tim 12", "pin maja"), calls)
    }

    /** Ida removed: the Loft keeps its header and a way to add a grown-up, no kid, and the remote is on its header. */
    @Test
    fun aHouseholdLeftWithNobodyKeepsItsHeaderToAddAGrownUp() {
        walkTo(hasText("Ida · Admin", substring = true))
        press(Key.DirectionCenter)
        // Reset PIN -> Sections -> Remove, then past Cancel.
        press(Key.DirectionDown, 2)
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertEquals(listOf("remove ida"), calls)
        compose.onAllNodesWithText("Ida", substring = true).assertCountEquals(1)
        compose.onAllNodesWithText(ADD_A_GROWN_UP).assertCountEquals(3)
        compose.onAllNodesWithText(ADD_A_KID).assertCountEquals(2)
        focusedOn("Loft · Admin Ida")
    }
}
