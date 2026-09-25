package ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.Card
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bug this closes: a screen that took [TvFocus.cardBorder] but built
 * its own `CardShape` (or fell back to tv-material's own rounded default)
 * got a square focus border wrapped around a rounded card. [TvFocus.cardShape]
 * and [TvFocus.cardBorder] now share one private `Shape` constant, so a
 * call site cannot let the container and its focus border drift apart the
 * way that finding described.
 *
 * The Robolectric version this replaces only proved a `Card` built from the
 * four [TvFocus] pieces composes and stays clickable — it never proved
 * focus itself moves, which is the entire point of a focus treatment.
 * D-pad traversal is real window-manager behaviour (tv-material dispatches
 * direction keys through the focus system, not through anything this
 * module owns), so it belongs in instrumentation rather than Robolectric —
 * the same reasoning behind every other test in this set.
 */
@RunWith(AndroidJUnit4::class)
class TvFocusTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aCardBuiltFromTheFourTvFocusPiecesTakesFocusWhenRequested() {
        compose.setContent { TvTheme { TwoFocusCards() } }

        compose.onNodeWithTag(FirstCardTag).performSemanticsAction(SemanticsActions.RequestFocus)

        compose.onNodeWithTag(FirstCardTag).assertIsFocused()
    }

    @Test
    fun dPadRightMovesFocusFromOneCardToTheNext() {
        compose.setContent { TvTheme { TwoFocusCards() } }

        compose.onNodeWithTag(FirstCardTag).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag(FirstCardTag).performKeyInput { pressKey(Key.DirectionRight) }

        compose.onNodeWithTag(FirstCardTag).assertIsNotFocused()
        compose.onNodeWithTag(SecondCardTag).assertIsFocused()
    }

    @Composable
    private fun TwoFocusCards() {
        Row {
            FocusCard(FirstCardTag)
            FocusCard(SecondCardTag)
        }
    }

    @Composable
    private fun FocusCard(tag: String) {
        Card(
            onClick = {},
            modifier = Modifier.testTag(tag),
            shape = TvFocus.cardShape(),
            scale = TvFocus.cardScale(),
            border = TvFocus.cardBorder(),
            glow = TvFocus.cardGlow(),
        ) {
            Box(Modifier.size(80.dp))
        }
    }

    private companion object {
        const val FirstCardTag = "tv-focus-card-first"
        const val SecondCardTag = "tv-focus-card-second"
    }
}
