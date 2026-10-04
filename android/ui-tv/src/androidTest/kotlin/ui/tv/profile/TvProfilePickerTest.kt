package ui.tv.profile

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import catalog.profile.ProfileUiState
import model.Profile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ui.tv.LeavesTouchModeRule
import ui.tv.TvTheme

/**
 * The three behaviours [TvProfilePicker] exists for on television — the
 * first tile already holds the remote when the picker appears, D-pad right
 * walks the row, and centre chooses whatever it lands on — only run true on
 * a real window manager. [TvProfilePickerStateTest] covers everything else
 * (which text a given [ProfileUiState] shows) without one.
 *
 * A fixture-free `ComponentActivity` hosts the composable directly: no
 * [catalog.profile.ProfileViewModel], no Hilt, no Telegram account —
 * [TvProfilePicker] takes its state and callbacks as plain parameters, the
 * same shape `ui.profile.ProfilePickerScreen` does on the phone.
 */
@RunWith(AndroidJUnit4::class)
class TvProfilePickerTest {
    @get:Rule val touchMode = LeavesTouchModeRule()

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val ada = Profile(id = "ada", name = "Ada")
    private val bea = Profile(id = "bea", name = "Bea")

    @Test
    fun theFirstTileIsFocusedAsSoonAsThePickerAppears() {
        show()

        compose.onNodeWithTag(TvProfilePickerFirstTileTag).assertIsFocused()
    }

    @Test
    fun dPadRightMovesFocusFromTheFirstTileToTheNext() {
        show()

        compose.onNodeWithTag(TvProfilePickerFirstTileTag).performKeyInput { pressKey(Key.DirectionRight) }

        compose.onNodeWithTag(TvProfilePickerFirstTileTag).assertIsNotFocused()
        compose.onNodeWithTag("tv-profile-tile-bea").assertIsFocused()
    }

    /**
     * Fixed-width tiles in a plain, non-scrolling row run out of the
     * screen's own safe width once there are enough of them — the row this
     * replaces squeezed a later tile down to zero width rather than letting
     * the D-pad scroll to reach it. Six profiles is comfortably past that
     * point; the row now scrolls with the D-pad instead of running out of
     * room.
     */
    @Test
    fun dPadRightRepeatedlyReachesAndFocusesTheLastOfSixProfiles() {
        val profiles = (1..6).map { Profile(id = "profile-$it", name = "Profile $it") }
        show(profiles = profiles)

        repeat(profiles.size - 1) {
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        }

        compose.onNodeWithTag("tv-profile-tile-profile-6").assertIsFocused()
    }

    @Test
    fun centreChoosesWhicheverTileIsFocused() {
        var chosen: String? = null
        show(onChoose = { chosen = it })

        compose.onNodeWithTag(TvProfilePickerFirstTileTag).performKeyInput { pressKey(Key.Enter) }

        assertEquals(ada.id, chosen)
    }

    /** With no grown-up there is nobody to pick a PIN for: making the first profile is what the remote lands on. */
    @Test
    fun theFirstProfileRowTakesTheRemoteOnADeviceWithNoGrownUp() {
        show(profiles = listOf(Profile(id = "k", name = "TV kids", kids = true)))

        compose.onNodeWithText("Create the first profile — it runs this household").assertIsFocused()
    }

    private fun show(
        onChoose: (String) -> Unit = {},
        profiles: List<Profile> = listOf(ada, bea),
    ) {
        compose.setContent {
            TvTheme {
                TvProfilePicker(
                    state = ProfileUiState.Picking(profiles = profiles, canStay = false),
                    onChoose = onChoose,
                    onStay = {},
                    onRetry = {},
                )
            }
        }
    }
}
