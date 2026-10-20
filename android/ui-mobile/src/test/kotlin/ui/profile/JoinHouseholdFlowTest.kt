package ui.profile

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import catalog.profile.FIRST_PROFILE
import catalog.profile.WAITING_FOR_HOUSEHOLD
import model.HOME_HOUSEHOLD
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uniffi.mediagram_core.Profile as CoreProfile

/**
 * Join a household at the gate, over the real view models and a fake core
 * that keeps the households' rules: andre, the owner, runs Home; Bea runs
 * Flat, with Cy. A device that has joined neither is asked which, before who
 * is watching, and that household's admin's PIN or the owner's lets it in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JoinHouseholdFlowTest : ProfileGateHarness() {
    private fun openUnjoined() =
        open(pins = mapOf("a" to "1234", "b" to "5678")) {
            profiles =
                listOf(
                    CoreProfile("a", "andre", admin = true, owner = true),
                    CoreProfile("b", "Bea", admin = true, household = "flat"),
                    CoreProfile("c", "Cy", household = "flat"),
                )
            homes.names["flat"] = "Flat"
            homes.joined = null
        }

    private fun choice(name: String) = compose.onNode(hasText(name) and hasClickAction())

    @Test fun aDeviceWithTwoHouseholdsAndNoneJoinedAsksWhichToJoin() {
        openUnjoined()
        compose.onNodeWithText(HouseholdWords.JOIN_A_HOUSEHOLD).assertExists()
        compose.onNodeWithText(HouseholdWords.JOIN_NOTE).assertExists()
        choice("Flat").assertExists()
        choice("Home").assertExists()
        // Never the first profile, which the core refuses on an install with grown-ups, nor anyone to manage yet.
        compose.onNodeWithText(FIRST_PROFILE).assertDoesNotExist()
        compose.onNodeWithText("Manage profiles").assertDoesNotExist()
        // Nothing behind it to go back to: Back leaves the app, as on a first run.
        backOnTheScreen()
        assertTrue(isFinishing())
    }

    @Test fun theAdminsPinShowsThatHouseholdsPicker() {
        openUnjoined()
        choice("Flat").performClick()
        inDialog("Bea’s PIN, or the owner’s").assertExists()
        pin("5678")

        assertEquals("flat", household.core.homes.joined)
        compose.onNodeWithText(HouseholdWords.JOIN_A_HOUSEHOLD).assertDoesNotExist()
        compose.onNodeWithText("Who's watching?").assertExists()
        choice("Bea").assertExists()
        choice("Cy").assertExists()
        compose.onNodeWithText("andre").assertDoesNotExist()
    }

    @Test fun aWrongPinStaysOnThePrompt() {
        openUnjoined()
        choice("Flat").performClick()
        pin("0000")

        inDialog("Bea’s PIN, or the owner’s").assertExists()
        inDialog("Wrong PIN.").assertExists()
        assertNull(household.core.homes.joined)
        compose.onNodeWithText(HouseholdWords.JOIN_A_HOUSEHOLD).assertExists()
    }

    /** A prompt left open is not waiting for whoever picks the device up next. */
    @Test fun leavingTheAppDropsThePrompt() {
        openUnjoined()
        choice("Flat").performClick()
        inDialog("Bea’s PIN, or the owner’s").assertExists()
        leaveAndReturn()
        inDialog("Bea’s PIN, or the owner’s").assertDoesNotExist()
        compose.onNodeWithText(HouseholdWords.JOIN_A_HOUSEHOLD).assertExists()
    }

    /** No first profile before the households are read: with grown-ups in another household, the core would refuse it. */
    @Test fun theFirstProfileWaitsUntilTheHouseholdsAreRead() {
        // A core that names no household at all has not been read: Home is never deleted.
        open { homes.names.clear() }
        compose.onNodeWithText(WAITING_FOR_HOUSEHOLD).assertExists()
        compose.onNodeWithText(FIRST_PROFILE).assertDoesNotExist()

        household.core.homes.names[HOME_HOUSEHOLD] = "Home"
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText(FIRST_PROFILE).assertExists()
        compose.onNodeWithText(WAITING_FOR_HOUSEHOLD).assertDoesNotExist()
    }

    /** As the web's `joinElsewhere`: the core makes no first profile while a grown-up is anywhere on the install. */
    @Test fun aHouseholdWithNoGrownUpLeftOffersTheOthersInsteadOfAFirstProfile() {
        open(pins = mapOf("a" to "1234")) {
            profiles = listOf(CoreProfile("a", "andre", admin = true, owner = true), CoreProfile("k", "Kim", kids = true, kidsAge = 6u, household = "barn"))
            homes.names["barn"] = "Barn"
            homes.joined = "barn"
        }
        compose.onNodeWithText(HouseholdWords.JOIN_A_HOUSEHOLD).assertExists()
        compose.onNodeWithText(FIRST_PROFILE).assertDoesNotExist()
        choice("Home").assertExists()
        // Its kids still have their tiles.
        choice("Kim").assertExists()
    }
}
