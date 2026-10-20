package ui.settings

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testing.FakeCore
import ui.common.settings.SettingsSection
import ui.profile.PinFieldTag
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uniffi.mediagram_core.Profile as CoreProfile

/**
 * This device on the Profiles page, the web's `drawThisDevice`: below
 * Manage, the household this device shows and Switch household — only while
 * there are two, and never for a kid. andre, Bea and Tom are in Home; Dan
 * runs Flat, with the PIN 5678.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HouseholdSwitchTest : ProfilesSettingsHarness() {
    private val flat: FakeCore.() -> Unit = {
        profiles = listOf(CoreProfile("d", "Dan", admin = true, household = "flat"))
        homes.names["flat"] = "Flat"
    }

    private fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h2400dp")
    fun theProfilesPageSaysThisDevicesHouseholdOnlyWithTwo() {
        open(chosen = bea, initial = SettingsSection.PROFILES)
        compose.onNodeWithText("Manage profiles").assertExists()
        compose.onNodeWithText(HouseholdWords.THIS_DEVICE).assertDoesNotExist()

        // A second household, made on another device.
        household.core.homes.names["flat"] = "Flat"
        compose.runOnUiThread { runBlocking { homes.reload() } }
        compose.waitForIdle()

        compose.onNodeWithText(HouseholdWords.THIS_DEVICE).assertExists()
        compose.onNodeWithText("Home").assertExists()
        compose.onNodeWithText(HouseholdWords.SWITCH_HOUSEHOLD).assertExists()
        assertTrue(top("Manage profiles") < top(HouseholdWords.THIS_DEVICE), "expected This device below Manage")
    }

    // Default window: a PIN dialog in a taller one can relayout forever under Robolectric.
    @Test
    fun switchingAwayFromTheChosenProfileShowsThePicker() {
        open(chosen = bea, pins = mapOf("d" to "5678"), initial = SettingsSection.PROFILES, seed = flat)
        compose.onNodeWithText(HouseholdWords.SWITCH_HOUSEHOLD).performScrollTo().performClick()
        compose.onNodeWithText(HouseholdWords.JOIN_A_HOUSEHOLD).assertExists()
        // The household this device shows is named, not offered again.
        compose.onNode(hasText("Home") and hasClickAction()).assertDoesNotExist()
        compose.onNode(hasText("Flat") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Dan’s PIN, or the owner’s").assertExists()
        compose.onNodeWithTag(PinFieldTag).performTextInput("5678")
        compose.waitForIdle()

        assertEquals("flat", household.core.homes.joined)
        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onNode(hasText("Dan") and hasClickAction()).assertExists()
        // Bea is not here to stay as.
        compose.onNodeWithText("Stay as I am").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h2400dp")
    fun aKidSeesNoHouseholdRow() {
        open(chosen = tom, initial = SettingsSection.PROFILES, seed = flat)
        assertEquals(2, homes.view.value.list.size)
        compose.onNodeWithText("Who is watching").assertExists()
        compose.onNodeWithText(HouseholdWords.THIS_DEVICE).assertDoesNotExist()
        compose.onNodeWithText(HouseholdWords.SWITCH_HOUSEHOLD).assertDoesNotExist()
    }
}
