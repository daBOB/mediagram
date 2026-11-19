package ui.settings

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import catalog.profile.ManageUiState
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.common.settings.SettingsSection
import ui.profile.PinFieldTag
import ui.profile.tile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Settings › Profiles on the phone, the web's Profiles tab: one page for everyone — who is
 * watching, then Manage inline, asking Who's managing?, over the real view model and a fake
 * core, its PIN held for the visit and dropped once the section is left. A kid sees only who
 * is watching.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfilesSettingsTest : ProfilesSettingsHarness() {
    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h800dp")
    fun aGrownUpHasOneProfilesSectionNamingThemAndTheCount() {
        open(chosen = bea)
        compose.onNodeWithText("Profiles").assertExists()
        compose.onNodeWithText("Bea · 3 profiles").assertExists()
        compose.onNodeWithText("Profile").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h800dp")
    fun aKidHasTheSameOneProfilesSectionNamingOnlyThem() {
        open(chosen = tom)
        compose.onNodeWithText("Profiles").assertExists()
        compose.onNodeWithText("Tom").assertExists()
        compose.onNodeWithText("3 profiles", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Profile").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h2400dp")
    fun aKidSeesOnlyWhoIsWatching() {
        open(chosen = tom, initial = SettingsSection.PROFILES)
        compose.onNodeWithText("Who is watching").assertExists()
        compose.onNodeWithText("Tom · Kids · FSK 12").assertExists()
        compose.onNodeWithText("Subtitles").assertExists()
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertDoesNotExist()
        compose.onNodeWithText("Manage profiles").assertDoesNotExist()
        assertEquals(ManageUiState.Closed, manage.state.value, "a kid's visit asked who manages")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h2400dp")
    fun aGrownUpSeesWhoIsWatchingThenWhoIsManaging() {
        open(chosen = bea, initial = SettingsSection.PROFILES)
        val you = compose.onNodeWithText("Who is watching").fetchSemanticsNode().boundsInRoot.top
        val manage = compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).fetchSemanticsNode().boundsInRoot.top
        assertTrue(you < manage, "expected You, then Who's managing?")
        compose.onNodeWithText("Everyone here").assertDoesNotExist()
        compose.onNodeWithText("Subtitles").assertExists()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h2400dp")
    fun switchProfileGoesStraightToThePicker() {
        open(chosen = bea, initial = SettingsSection.PROFILES)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Switch profile").performScrollTo().performClick()
        // Frame by frame: never the picker's own Profiles page (Who's managing?, no You) on the way.
        repeat(10) {
            compose.mainClock.advanceTimeByFrame()
            val household = compose.onAllNodesWithText(HouseholdWords.WHOS_MANAGING).fetchSemanticsNodes().isNotEmpty()
            val you = compose.onAllNodesWithText("Who is watching").fetchSemanticsNodes().isNotEmpty()
            assertFalse(household && !you, "the picker's Profiles page showed on the way to the picker")
        }
        compose.onNodeWithText("Who's watching?").assertExists()
    }

    // Everyone here is gone, as on the web: the owner's view is where every line still shows.
    // Default window: the owner's PIN is a text-field dialog.
    @Test
    fun everyoneIsListedWithTheirRole() {
        open(chosen = bea, pins = mapOf("a" to "1234"), initial = SettingsSection.PROFILES, people = listOf(tom, andre.copy(owner = true), bea))
        // Seeing anyone still asks who you are first.
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertExists()
        compose.onNode(tile("andre")).performScrollTo().performClick()
        compose.onNodeWithTag(PinFieldTag).performTextInput("1234")
        compose.waitForIdle()
        // Unclipped: the default window scrolls, and a row below its fold has no bounds on screen.
        val admin = compose.onNodeWithText("Admin · PIN set", substring = true).fetchSemanticsNode().positionInRoot.y
        compose.onNodeWithText("Grown-up · No PIN").assertExists()
        val kid = compose.onNodeWithText("Kid · Bea · FSK 12 · 1 h").fetchSemanticsNode().positionInRoot.y
        assertTrue(admin < kid, "expected the grown-ups listed before the kids")
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

        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertExists()
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
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertExists()
    }
}
