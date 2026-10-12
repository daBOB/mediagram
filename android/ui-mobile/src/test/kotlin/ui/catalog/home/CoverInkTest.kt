package ui.catalog.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import designsystem.Appearance
import designsystem.LocalIndicationTone
import designsystem.MediagramTheme
import designsystem.ThemeChoice
import kotlin.test.assertEquals
import kotlinx.coroutines.launch
import model.Kind
import model.MediaSet
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The cover's artwork is dark in either theme, so a press on it tints in the
 * artwork's light ink: the light theme's page ink is near-black and would
 * vanish there. Tints are drawn and nothing here captures pixels
 * (PageGroundColorTest), so a stand-in indication records the tone a press
 * resolves at the control, where the catalogue's own would draw it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1164dp-h777dp")
class CoverInkTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private val film =
        MediaSet(
            setId = "the-film", kind = Kind.MOVIE, title = "A film", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2020, durationSecs = 6_600, posterPath = null,
            totalBytes = 0, backdropPath = "backdrop.jpg", genres = listOf("drama"), rating = 7.5,
        )

    /** Records the ink [LocalIndicationTone] gives at each press. */
    private class InkProbe : IndicationNodeFactory {
        val pressedIn = mutableListOf<Color>()

        override fun create(interactionSource: InteractionSource): DelegatableNode =
            object : Modifier.Node(), CompositionLocalConsumerModifierNode {
                override fun onAttach() {
                    coroutineScope.launch { interactionSource.interactions.collect { if (it is PressInteraction.Press) record() } }
                }

                fun record() {
                    pressedIn += currentValueOf(LocalIndicationTone).ink
                }
            }

        override fun equals(other: Any?) = other === this

        override fun hashCode() = System.identityHashCode(this)
    }

    @Test
    fun aPressOnACoverControlTintsInTheArtworksInkInTheLightTheme() {
        val probe = InkProbe()
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MediagramTheme(appearance = Appearance(theme = ThemeChoice.LIGHT)) {
                    CompositionLocalProvider(LocalIndication provides probe) {
                        HomeCover(
                            films = listOf(film), watchlist = emptySet(), width = 1164.dp,
                            onPlay = {}, onOpenTitle = {}, onToggleWatchlist = { _, _ -> },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Add to My List").performTouchInput { down(center) }
        compose.waitForIdle()
        assertEquals(listOf(OnImage), probe.pressedIn)
    }
}
