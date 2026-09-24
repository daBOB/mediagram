package ui.tv

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The bug this closes: a screen that took [TvFocus.cardBorder] but built
 * its own [androidx.tv.material3.CardShape] (or fell back to tv-material's
 * own rounded default) got a square focus border wrapped around a rounded
 * card. [TvFocus.cardShape] and [TvFocus.cardBorder] now share one
 * private `Shape` constant, so a call site cannot let the container and
 * its focus border drift apart the way that finding described.
 *
 * tv-material doesn't expose that internal shape/border state for a test
 * to compare directly — `CardShape` and `CardBorder`'s fields are
 * `internal` to that library, invisible outside it even to a test in this
 * module. What this checks instead is the thing a real screen actually
 * does: compose a `Card` from exactly the four pieces
 * [TvFocus.cardShape], [TvFocus.cardScale], [TvFocus.cardBorder] and
 * [TvFocus.cardGlow] together, the combination this fix exists to let a
 * call site take as one unit, and confirm that combination composes and
 * still carries the card's click affordance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvFocusTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After
    fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    @Test
    fun cardBuiltFromTvFocusShapeScaleBorderAndGlowComposesAndStaysClickable() {
        show {
            Card(
                onClick = {},
                modifier = Modifier.testTag(CardTag),
                shape = TvFocus.cardShape(),
                scale = TvFocus.cardScale(),
                border = TvFocus.cardBorder(),
                glow = TvFocus.cardGlow(),
            ) {
                Box(Modifier.size(10.dp))
            }
        }
        compose.onNodeWithTag(CardTag).assertExists().assertHasClickAction()
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { TvTheme { content() } }
        }
        compose.waitForIdle()
    }

    private companion object {
        const val CardTag = "tv-focus-card"
    }
}
