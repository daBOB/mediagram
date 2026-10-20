package ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import catalog.profile.FIRST_PROFILE
import catalog.profile.WAITING_FOR_HOUSEHOLD
import model.HOME_HOUSEHOLD
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testing.FakeCore
import kotlin.test.assertEquals
import uniffi.mediagram_core.Profile as CoreProfile

/**
 * Joining and switching household, and the owner's Households, by remote alone ([TvProfilesHarness]):
 * andre, the owner, runs Home with Bo; Dan runs Flat and has no PIN, so the
 * owner's opens it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvHouseholdsFlowTest : TvProfilesHarness() {
    private fun households(joined: String? = HOME_HOUSEHOLD): FakeCore.() -> Unit =
        {
            profiles =
                listOf(
                    CoreProfile("a", "andre", admin = true, owner = true),
                    CoreProfile("b", "Bo"),
                    CoreProfile("d", "Dan", admin = true, household = "flat"),
                )
            homes.names["flat"] = "Flat"
            homes.joined = joined
        }

    /** Who's watching, once the household joined has been read again. */
    private fun awaitPicker() = compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Who's watching?")).fetchSemanticsNodes().isNotEmpty() }

    /** Settings › Profiles, entered and walked down to This device, pressed. */
    private fun switchFromSettings() {
        openSettingsAt(rowOf("Profiles"))
        press(Key.DirectionCenter)
        walkTo(rowOf("${HouseholdWords.THIS_DEVICE} · Home"), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText(HouseholdWords.JOIN_A_HOUSEHOLD).assertExists()
    }

    /** Nothing but Join a household until one is joined; then that household's picker. */
    @Test
    fun aBoxWithTwoHouseholdsAndNoneJoinedAsksWhichToJoin() {
        launch(emptyList(), chosen = null, ready = HouseholdWords.JOIN_A_HOUSEHOLD, seed = households(joined = null))
        compose.onAllNodesWithText("Who's watching?").assertCountEquals(0)
        compose.onNode(rowOf("Flat")).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Dan’s PIN, or the owner’s").assertExists()
        pin("1234")

        awaitPicker()
        compose.onNode(rowOf("Dan")).assertExists()
        compose.onAllNodes(rowOf("andre")).assertCountEquals(0)
        assertEquals("flat", fixture.core.homes.joined)
    }

    /** A box whose household has no grown-up left is offered the others, never "Create the first profile". */
    @Test
    fun anEmptyHouseholdOffersTheOthersInsteadOfAFirstProfile() {
        val homeIsEmpty: FakeCore.() -> Unit = {
            profiles = listOf(CoreProfile("a", "andre", admin = true, owner = true, household = "flat"))
            homes.names["flat"] = "Flat"
        }
        launch(emptyList(), chosen = null, ready = HouseholdWords.JOIN_A_HOUSEHOLD, seed = homeIsEmpty)
        compose.onAllNodesWithText(FIRST_PROFILE).assertCountEquals(0)
        compose.onNode(rowOf("Flat")).assertIsFocused()
    }

    /** No grown-up here and the households not read: the picker waits, and Try again reads them, before a first profile is offered. */
    @Test
    fun noFirstProfileUntilTheHouseholdsAreRead() {
        // No live household at all reads as none read yet: the view is never published.
        launch(emptyList(), chosen = null, pins = emptyMap(), ready = WAITING_FOR_HOUSEHOLD, seed = { homes.names.clear() })
        compose.onAllNodesWithText(FIRST_PROFILE).assertCountEquals(0)
        compose.runOnUiThread { fixture.core.homes.names[HOME_HOUSEHOLD] = "Home" }
        compose.onNode(rowOf("Try again")).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNode(rowOf(FIRST_PROFILE)).assertIsFocused()
    }

    /** This device leaves the profile chosen here behind: the picker shows the other household's profiles. */
    @Test
    fun switchingFromSettingsShowsTheOtherHouseholdsPicker() {
        launch(emptyList(), chosen = "a", seed = households())
        switchFromSettings()
        compose.onNode(rowOf("Flat")).assertIsFocused()
        compose.onAllNodes(rowOf("Home")).assertCountEquals(0)
        press(Key.DirectionCenter)
        pin("1234")

        awaitPicker()
        compose.onNode(rowOf("Dan")).assertExists()
        compose.onAllNodes(rowOf("andre")).assertCountEquals(0)
    }

    @Test
    fun backFromTheJoinPanelReturnsToTheRow() {
        launch(emptyList(), chosen = "a", seed = households())
        switchFromSettings()
        back()
        compose.onNode(rowOf("${HouseholdWords.THIS_DEVICE} · Home")).assertIsFocused()
    }

    /** The owner watching opens Households with no PIN; the first change asks it, and the remote is back on the household. */
    @Test
    fun theOwnerMovesAGrownUpWithTheRemote() {
        launch(emptyList(), chosen = "a", seed = households())
        openSettingsAt(rowOf("Profiles"))
        press(Key.DirectionCenter)
        walkTo(rowOf(HouseholdWords.HOUSEHOLDS), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNode(rowOf("Flat · Admin Dan · 1 profile")).assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        walkTo(rowOf(HouseholdWords.MOVE_HERE), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNode(rowOf("Dan")).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText(HouseholdWords.OWNERS_PIN).assertExists()
        pin("1234")

        compose.onNode(rowOf("Home · Admin andre · 3 profiles")).assertIsFocused()
        assertEquals(HOME_HOUSEHOLD, fixture.core.profiles.single { it.id == "d" }.household)
    }
}
