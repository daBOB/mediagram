package ui.tv.profile

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import catalog.profile.ADD_A_GROWN_UP
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
                "Home · Admin andre", "andre", "Lea", "${HouseholdWords.ADD_SOMEONE_TO} Home",
                "Loft · Admin Ida", "Ida", "${HouseholdWords.ADD_SOMEONE_TO} Loft",
                "Flat · Admin Maja", "Maja", "Tim", "${HouseholdWords.ADD_SOMEONE_TO} Flat",
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
        walkTo(hasText("Tim"))
        press(Key.DirectionCenter)
        // The current limit holds the remote; the other is one step down.
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals(listOf("age tim 12"), calls)
        focusedOn("Kid · Maja · FSK 12")
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        assertEquals(listOf("age tim 12", "pin maja"), calls)
    }

    /** Ida moved to the Flat on another device while the remote is on her row: it lands on the Loft's header, not nowhere. */
    @Test
    fun aRowMovedToAnotherHouseholdHandsTheRemoteToItsHouseholdsHeader() {
        walkTo(hasText("Ida"))
        change { it.copy(grownUps = it.grownUps.map { one -> if (one.id == "ida") one.copy(household = "flat") else one }) }
        focusedOn("Loft · Admin Ida")
    }

    /** Ida removed: the Loft keeps its header and Add someone, which asks no Grown-up or Kid — a kid needs a grown-up — and the remote is on its header. */
    @Test
    fun aHouseholdLeftWithNobodyKeepsItsHeaderToAddAGrownUp() {
        walkTo(hasText("Ida"))
        press(Key.DirectionCenter)
        // Reset PIN -> Sections -> Remove, then past Cancel.
        press(Key.DirectionDown, 2)
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertEquals(listOf("remove ida"), calls)
        compose.onAllNodesWithText("Ida", substring = true).assertCountEquals(1)
        compose.onAllNodes(ownersAddSomeone).assertCountEquals(3)
        focusedOn("Loft · Admin Ida")
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText("$ADD_A_GROWN_UP · Loft").assertExists()
        compose.onAllNodesWithText(HouseholdWords.KID).assertCountEquals(0)
    }
}
