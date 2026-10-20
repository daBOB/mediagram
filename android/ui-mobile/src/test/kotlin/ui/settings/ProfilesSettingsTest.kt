package ui.settings

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import catalog.profile.ManageUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.common.settings.SettingsSection
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Settings › Profiles on the phone, the web's Profiles tab: shown only while a grown-up is
 * chosen, everyone listed with their role, and Manage inline over the real view model and a
 * fake core — its PIN held for the visit and dropped once the section is left.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfilesSettingsTest : ProfilesSettingsHarness() {
    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h800dp")
    fun aGrownUpSeesProfilesInSettings() {
        open(chosen = bea)
        compose.onNodeWithText("Profiles").assertExists()
        compose.onNodeWithText("3 profiles").assertExists()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h800dp")
    fun aKidSeesNoProfilesSection() {
        open(chosen = tom)
        compose.onNodeWithText("Profile").assertExists()
        compose.onNodeWithText("Profiles").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h2400dp")
    fun everyoneIsListedWithTheirRole() {
        open(chosen = bea, pins = mapOf("a" to "1234"))
        compose.onNodeWithText("Profiles").performClick()
        compose.onNodeWithText("Everyone here").assertExists()
        val admin = compose.onNodeWithText("Admin · PIN set").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("Grown-up · No PIN").assertExists()
        val kid = compose.onNodeWithText("Kid · Bea · FSK 12 · 1 h").fetchSemanticsNode().boundsInRoot.top
        assertTrue(admin < kid, "expected the grown-ups listed before the kids")
        // Changing anything still asks who you are first.
        compose.onNodeWithText("Who are you?").assertExists()
    }

    // Default window for the PIN tests, where a text-field dialog cannot relayout forever as in a
    // taller one; the index's last rows sit below its fold there, so they open the section directly.
    @Test
    fun leavingTheSectionForgetsThePin() {
        open(chosen = bea, pins = mapOf("b" to "2468"), initial = SettingsSection.PROFILES)
        actAsBea()
        compose.onNodeWithText("As Bea").assertExists()
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("SETTINGS").assertExists()
        assertEquals(ManageUiState.Closed, manage.state.value)
        compose.runOnUiThread { manage.setKidsAge("t", 6) }
        compose.waitForIdle()
        assertEquals(12, tomsAge(), "a change went through on a PIN nobody gave again")
    }

    @Test
    fun returningToTheAppAsksWhoYouAreAgain() {
        open(chosen = bea, pins = mapOf("b" to "2468"), initial = SettingsSection.PROFILES)
        actAsBea()
        compose.runOnUiThread { controller.pause().stop() }
        compose.waitForIdle()
        assertTrue(manage.state.value !is ManageUiState.Managing, "still managing with the app left")
        compose.runOnUiThread { controller.start().resume() }
        compose.waitForIdle()

        compose.onNodeWithText("Who are you?").assertExists()
        compose.onNodeWithText("As Bea").assertDoesNotExist()
        compose.runOnUiThread { manage.setKidsAge("t", 6) }
        compose.waitForIdle()
        assertEquals(12, tomsAge(), "a change went through on a PIN nobody gave again")
    }

    /** A theme, locale or font-scale change rebuilds the gate around Settings; Profiles must still ask who you are. */
    @Test
    fun profilesStillAsksWhoYouAreOnceTheActivityIsRecreated() {
        open(chosen = bea, pins = mapOf("b" to "2468"), initial = SettingsSection.PROFILES)
        actAsBea()
        recreate(initial = SettingsSection.PROFILES)
        compose.onNodeWithText("Who are you?").assertExists()
    }
}
