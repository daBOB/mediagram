package ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import catalog.profile.MANAGE_PROFILES
import catalog.profile.SWITCH_PROFILE
import catalog.profile.managingAs
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.profile.TvManageProfilesTag

/**
 * The owner's view by remote alone ([TvProfilesHarness]): open at once
 * for the owner watching, and over the picker shown again after them — which
 * is anybody's — only by the owner's PIN, as the web's page over its picker is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvOwnersPartOverPickerTest : TvProfilesHarness() {
    private val flat = "Flat · Admin Dan · 1 profile"

    @Test
    fun theOwnerWatchingSeesThePartAndThePickerAfterThemIsAskedThePin() {
        launch(emptyList(), chosen = "a", seed = twoHouseholds())
        openSettingsAt(rowOf("Profiles"))
        press(Key.DirectionCenter)
        walkTo(rowOf(MANAGE_PROFILES), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNode(rowOf(flat)).assertExists()
        back()

        walkTo(rowOf(SWITCH_PROFILE), Key.DirectionUp)
        press(Key.DirectionCenter)
        walkTo(hasTestTag(TvManageProfilesTag), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText(HouseholdWords.OWNER_NOTE).assertExists()
        walkTo(rowOf(HouseholdWords.OWNER), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText(HouseholdWords.OWNERS_PIN).assertExists()
        compose.onAllNodes(rowOf(flat)).assertCountEquals(0)
    }

    /** Over the picker, the Owner row's PIN puts the owner's view in Manage's place; Back from it is back on the page. */
    @Test
    fun theOwnerRowOverThePickerOpensTheOwnersView() {
        launch(emptyList(), chosen = null, seed = twoHouseholds())
        walkTo(hasTestTag(TvManageProfilesTag), Key.DirectionDown)
        press(Key.DirectionCenter)
        walkTo(rowOf(HouseholdWords.OWNER), Key.DirectionDown)
        press(Key.DirectionCenter)
        pin("1234")
        compose.onNodeWithText(managingAs("andre")).assertExists()
        compose.onNode(rowOf("Home · Admin andre · 2 profiles")).assertIsFocused()
        compose.onNode(rowOf(flat)).assertExists()

        back()
        compose.onNodeWithText(HouseholdWords.OWNER_NOTE).assertExists()
        compose.onNode(rowOf(MANAGE_PROFILES)).assertIsFocused()
    }
}
