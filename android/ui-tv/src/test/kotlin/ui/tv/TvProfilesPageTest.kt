package ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.profile.TvManageProfilesTag
import ui.tv.profile.tvManagerTileTag
import kotlin.test.assertEquals

/**
 * The one Profiles page, by remote alone ([TvProfilesHarness]): in Settings,
 * who is watching, then the Manage profiles row, which asks Who's managing?;
 * over the picker, Who's managing? on its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvProfilesPageTest : TvProfilesHarness() {
    /** One Profiles row, none for a Profile beside it; its line says who is watching and how many are here. Its page lists nobody: Who's managing? asks. */
    @Test
    fun settingsHasOneProfilesRowAndNoEveryoneList() {
        launch(listOf(andre, bo, cy), chosen = "b")
        openSettingsAt(rowOf("Profiles"))
        compose.onAllNodes(rowOf("Profile")).assertCountEquals(0)
        compose.onNodeWithText("Bo · 3 profiles").assertExists()
        compose.onAllNodesWithText("Everyone here").assertCountEquals(0)
        compose.onAllNodesWithText("Kid · Bo · FSK 6").assertCountEquals(0)
        compose.onNode(rowOf("Manage profiles")).assertExists()
    }

    @Test
    fun aKidSeesOnlyWhoIsWatching() {
        launch(listOf(andre, bo, cy), chosen = "c", ready = "Nothing rated FSK 6 or under yet.")
        openSettingsAt(rowOf("Profiles"))
        press(Key.DirectionCenter)
        compose.onNode(hasText("Who is watching")).assertIsFocused()
        compose.onNodeWithText("Cy · Kids · FSK 6").assertExists()
        compose.onNode(rowOf("Switch profile")).assertExists()
        compose.onAllNodesWithText("Everyone here").assertCountEquals(0)
        compose.onAllNodesWithText("Manage profiles").assertCountEquals(0)
    }

    @Test
    fun aGrownUpWalksFromWhoIsWatchingToManageProfiles() {
        launch(listOf(andre, bo, cy), chosen = "b")
        openSettingsAt(rowOf("Profiles"))
        press(Key.DirectionCenter)
        compose.onNode(hasText("Who is watching")).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNode(rowOf("Switch profile")).assertIsFocused()
        walkTo(rowOf("Manage profiles"), Key.DirectionDown)
    }

    /** Switch profile is the picker itself, never Manage standing in its place; "Stay as I am" comes back to the page, the remote on it. */
    @Test
    fun switchProfileOpensThePicker() {
        launch(listOf(andre, bo, cy), chosen = "b")
        openSettingsAt(rowOf("Profiles"))
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onAllNodesWithText("Who are you?").assertCountEquals(0)
        back()
        compose.onNode(hasText("Who is watching")).assertIsFocused()
    }

    @Test
    fun theRemoteReachesManageFromThePage() {
        launch(listOf(andre, bo, cy), chosen = "b")
        manageFromSettingsAsBo()
        compose.onNode(rowOf("Bo")).assertIsFocused()
        compose.onNode(rowOf("Cy")).assertExists()
    }

    /** Back out of Bo's view is Who's managing?, the remote on his tile; Back again ends the visit on the row that opened it, the PIN given gone. */
    @Test
    fun leavingManageForSettingsForgetsThePin() {
        launch(listOf(andre, bo, cy), chosen = "b")
        manageFromSettingsAsBo()

        back()
        compose.onNodeWithTag(tvManagerTileTag("b")).assertIsFocused()
        back()
        compose.onNode(rowOf("Manage profiles")).assertIsFocused()
        assertThePinIsGone()
    }

    /** The app left with Manage open in Settings: back in it, the panel asks Who's managing? again rather than keeping Bo's PIN. */
    @Test
    fun comingBackToTheAppAsksWhoIsManagingAgainInSettings() {
        launch(listOf(andre, bo, cy), chosen = "b")
        manageFromSettingsAsBo()

        leaveAndReturn()
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertExists()
        compose.onNodeWithTag(tvManagerTileTag("b")).assertIsFocused()
        compose.onAllNodesWithText("As Bo").assertCountEquals(0)
    }

    /** The picker's Manage profiles is Who's managing? alone; Back from Manage comes back to it, Back from it to the picker, and the PIN stays behind. */
    @Test
    fun thePickersManageProfilesAsksWhoIsManaging() {
        launch(listOf(andre, bo, cy), chosen = null)
        openManageAs("b", "5678")
        compose.onAllNodesWithText("Who is watching").assertCountEquals(0)
        compose.onAllNodesWithText("Everyone here").assertCountEquals(0)
        back()
        compose.onNodeWithTag(tvManagerTileTag("b")).assertIsFocused()
        back()
        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onNodeWithTag(TvManageProfilesTag).assertIsFocused()
        assertThePinIsGone()
    }

    private fun assertThePinIsGone() {
        compose.runOnUiThread { fixture.manage.setKidsAge("c", 12) }
        compose.waitForIdle()
        assertEquals(6, fixture.core.profiles.single { it.id == "c" }.kidsAge?.toInt(), "a change went through on a PIN nobody gave again")
    }

    /** Settings › Profiles, entered and walked down to its Manage profiles row → who you are → Bo → Bo's PIN, by remote alone. */
    private fun manageFromSettingsAsBo() {
        openSettingsAt(rowOf("Profiles"))
        press(Key.DirectionCenter)
        walkTo(rowOf("Manage profiles"), Key.DirectionDown)
        openManageAs("b", "5678", fromPicker = false)
        compose.onNodeWithText("As Bo").assertExists()
    }
}
