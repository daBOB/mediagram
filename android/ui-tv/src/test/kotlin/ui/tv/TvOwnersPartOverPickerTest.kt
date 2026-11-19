package ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import catalog.profile.DONE
import catalog.profile.MANAGE_PROFILES
import catalog.profile.SWITCH_PROFILE
import model.HouseholdWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.profile.TvManageProfilesTag
import ui.tv.profile.tvManagerTileTag

/**
 * The owner's view by remote alone ([TvProfilesHarness]): open at once for
 * the owner watching, and over the picker shown again after them — which is
 * anybody's — only by the owner's tile and PIN, as the web's page over its
 * picker is. The way in over the picker, and Back out of it, are
 * [TvWhoIsManagingTest]'s.
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
        compose.onNodeWithText(HouseholdWords.WHOS_MANAGING).assertExists()
        compose.onNodeWithTag(tvManagerTileTag("a")).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText(HouseholdWords.OWNERS_PIN).assertExists()
        compose.onAllNodes(rowOf(flat)).assertCountEquals(0)
    }

    /**
     * Over the picker the owner's view ends as the web's and the tablet's page does there: This device, the
     * household it shows and Switch household — Done after it, as their Done follows the page. OK opens Join
     * a household; Back is that line again. Who's managing? has none, nor does the owner's view in Settings,
     * whose page has its own row.
     */
    @Test
    fun theOwnersViewOverThePickerEndsWithThisDevice() {
        launch(emptyList(), chosen = null, seed = twoHouseholds())
        walkTo(hasTestTag(TvManageProfilesTag), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onAllNodesWithText(HouseholdWords.THIS_DEVICE, substring = true).assertCountEquals(0)
        press(Key.DirectionCenter)
        pin("1234")

        val device = rowOf("${HouseholdWords.THIS_DEVICE} · Home")
        walkTo(device, Key.DirectionDown)
        compose.onNode(device).assert(hasText(HouseholdWords.SWITCH_HOUSEHOLD))
        press(Key.DirectionUp)
        compose.onNode(rowOf(HouseholdWords.CREATE_A_HOUSEHOLD)).assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNode(rowOf(DONE)).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNode(rowOf(DONE)).assertIsFocused()

        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        compose.onNodeWithText(HouseholdWords.JOIN_A_HOUSEHOLD).assertExists()
        back()
        compose.onNode(device).assertIsFocused()
    }
}
