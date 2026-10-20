package ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.profile.TvManageProfilesTag
import kotlin.test.assertEquals

/**
 * The one Profiles page, by remote alone ([TvProfilesHarness]): in Settings,
 * who is watching, then everyone here, then the Manage profiles row; over the
 * picker, the same page with nobody chosen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvProfilesPageTest : TvProfilesHarness() {
    /** One Profiles row, none for a Profile beside it; its line says who is watching and how many are here, its page lists them all before anyone says who they are. */
    @Test
    fun settingsHasOneProfilesRowThatListsEveryone() {
        launch(listOf(andre, bo, cy), chosen = "b")
        openSettingsAt(rowOf("Profiles"))
        compose.onAllNodes(rowOf("Profile")).assertCountEquals(0)
        compose.onNodeWithText("Bo · 3 profiles").assertExists()
        compose.onNodeWithText("Everyone here").assertExists()
        compose.onNodeWithText("Admin · PIN set").assertExists()
        compose.onNodeWithText("Grown-up · PIN set").assertExists()
        compose.onNodeWithText("Kid · Bo · FSK 6").assertExists()
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
        walkTo(hasText("Everyone here"), Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNode(rowOf("Manage profiles")).assertIsFocused()
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
        compose.onNode(rowOf("Cy · FSK 6 · No limit")).assertIsFocused()
    }

    /** Back out of Manage is the end of the visit: the remote is on the row that opened it, and the PIN given is gone. */
    @Test
    fun leavingManageForSettingsForgetsThePin() {
        launch(listOf(andre, bo, cy), chosen = "b")
        manageFromSettingsAsBo()

        back()
        compose.onNode(rowOf("Manage profiles")).assertIsFocused()
        assertThePinIsGone()
    }

    /** The app left with Manage open in Settings: back in it, the panel asks who you are again rather than keeping Bo's PIN. */
    @Test
    fun comingBackToTheAppAsksWhoYouAreAgainInSettings() {
        launch(listOf(andre, bo, cy), chosen = "b")
        manageFromSettingsAsBo()

        leaveAndReturn()
        compose.onNodeWithText("Who are you?").assertExists()
        compose.onNode(rowOf("andre")).assertIsFocused()
        compose.onAllNodesWithText("As Bo").assertCountEquals(0)
    }

    /** The picker's Manage profiles is this page with nobody chosen; Back from Manage comes back to it, Back from it to the picker, and the PIN stays behind. */
    @Test
    fun thePickersManageProfilesOpensThePageWithNobodyChosen() {
        launch(listOf(andre, bo, cy), chosen = null)
        walkTo(hasTestTag(TvManageProfilesTag), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Profiles").assertExists()
        compose.onNodeWithText("Everyone here").assertExists()
        compose.onAllNodesWithText("Who is watching").assertCountEquals(0)
        compose.onNode(rowOf("Manage profiles")).assertIsFocused()

        openManageAs(rowOf("Bo"), "5678", fromPicker = false)
        back()
        compose.onNode(rowOf("Manage profiles")).assertIsFocused()
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
        openManageAs(rowOf("Bo"), "5678", fromPicker = false)
        compose.onNodeWithText("As Bo").assertExists()
    }
}
