package ui.tv.profile

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import catalog.profile.ProfileUiState
import model.Profile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ui.tv.TvTheme
import ui.tv.setup.TvTextQuestionFieldTag

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
    fun dPadRightRepeatedlyReachesAndFocusesTheAddTileWithSixProfiles() {
        val profiles = (1..6).map { Profile(id = "profile-$it", name = "Profile $it") }
        show(profiles = profiles)

        repeat(profiles.size) {
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        }

        compose.onNodeWithTag(TvProfilePickerAddTileTag).assertIsFocused()
    }

    @Test
    fun centreChoosesWhicheverTileIsFocused() {
        var chosen: String? = null
        show(onChoose = { chosen = it })

        compose.onNodeWithTag(TvProfilePickerFirstTileTag).performKeyInput { pressKey(Key.Enter) }

        assertEquals(ada.id, chosen)
    }

    @Test
    fun theAddTileOpensTheNameQuestionWithTheFieldFocused() {
        show()

        compose.onNodeWithTag(TvProfilePickerAddTileTag).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag(TvProfilePickerAddTileTag).performKeyInput { pressKey(Key.Enter) }

        compose.onNodeWithTag(TvTextQuestionFieldTag).assertIsFocused()
    }

    /**
     * The kids-choice step reached past the name question: focused on
     * arrival, centre flips it, D-pad down reaches the Add row beneath it,
     * and centre there adds with whatever the toggle was last left at.
     */
    @Test
    fun theKidsStepIsFocusedFlipsAndAddsWithTheChosenValue() {
        var added: Pair<String, Boolean>? = null
        show(onAdd = { name, kids -> added = name to kids })

        compose.onNodeWithTag(TvProfilePickerAddTileTag).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag(TvProfilePickerAddTileTag).performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput("Cara")
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()

        compose.onNodeWithTag(TvProfilePickerKidsToggleTag).assertIsFocused()
        compose.onNodeWithTag(TvProfilePickerKidsToggleTag).assertTextContains("Off")

        compose.onNodeWithTag(TvProfilePickerKidsToggleTag).performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag(TvProfilePickerKidsToggleTag).assertTextContains("On")

        compose.onNodeWithTag(TvProfilePickerKidsToggleTag).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(TvProfilePickerAddConfirmTag).assertIsFocused()
        compose.onNodeWithTag(TvProfilePickerAddConfirmTag).performKeyInput { pressKey(Key.Enter) }

        assertEquals("Cara" to true, added)
    }

    private fun show(
        onChoose: (String) -> Unit = {},
        onAdd: (String, Boolean) -> Unit = { _, _ -> },
        profiles: List<Profile> = listOf(ada, bea),
    ) {
        compose.setContent {
            TvTheme {
                TvProfilePicker(
                    state = ProfileUiState.Picking(profiles = profiles, canStay = false),
                    onChoose = onChoose,
                    onAdd = onAdd,
                    onStay = {},
                    onRetry = {},
                )
            }
        }
    }
}
