package ui.tv.profile

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
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

    private fun show(
        onChoose: (String) -> Unit = {},
        onAdd: (String, Boolean) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            TvTheme {
                TvProfilePicker(
                    state = ProfileUiState.Picking(profiles = listOf(ada, bea), canStay = false),
                    onChoose = onChoose,
                    onAdd = onAdd,
                    onStay = {},
                    onRetry = {},
                )
            }
        }
    }
}
