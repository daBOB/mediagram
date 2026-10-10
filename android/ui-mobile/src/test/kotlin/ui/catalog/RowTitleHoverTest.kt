package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.text.TextLayoutResult
import catalog.CollectionKind
import catalog.Entry
import catalog.SearchRow
import model.Kind
import model.MediaSet
import designsystem.MediagramTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A card's name fades to accent while a pointer is over it (`catalog.css:44,48`);
 * a `.row`'s title has no transition and snaps (`catalog.css:74-79`).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RowTitleHoverTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private var accent = Color.Unspecified
    private var rest = Color.Unspecified

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MediagramTheme {
                    accent = MaterialTheme.colorScheme.primary
                    rest = LocalContentColor.current
                    content()
                }
            }
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun colourOf(text: String): Color {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first().layoutInput.style.color
    }

    @Test fun aShelfCardsNameFadesToAccentUnderAPointerAndBackOutAgain() {
        show {
            ShelfList(
                entries = listOf(Entry.Collection(key = "k", kind = CollectionKind.SHOW, name = "Dune Saga", posterPath = null, posterKey = null, count = 1, chapters = 1, divisions = emptyList())),
                positions = emptyMap(),
                watchedIds = emptySet(),
                heldIds = emptySet(),
                onOpenTitle = {},
                onOpenCollection = {},
            )
        }
        val resting = colourOf("Dune Saga")
        assertNotEquals(accent, resting, "the two states must differ for this check to mean anything")
        compose.onNodeWithText("Dune Saga").performMouseInput { enter(center) }
        compose.mainClock.advanceTimeBy(64)
        val mid = colourOf("Dune Saga")
        assertNotEquals(resting, mid, "the title has started to fade")
        assertNotEquals(accent, mid, "the title has not snapped")
        compose.mainClock.advanceTimeBy(250)
        assertEquals(accent, colourOf("Dune Saga"))
        compose.onNodeWithText("Dune Saga").performMouseInput { exit(Offset(-10f, -10f)) }
        compose.mainClock.advanceTimeBy(400)
        assertEquals(resting, colourOf("Dune Saga"))
    }

    @Test fun aSearchRowsTitleSnapsToAccentOnTheFirstFrameUnderAPointer() {
        val set = MediaSet(
            setId = "s", kind = Kind.MOVIE, title = "Arrival", show = null, chapter = null, path = null,
            season = null, episodeFirst = null, episodeLast = null, year = 2016, durationSecs = 6_000,
            posterPath = null, totalBytes = 0,
        )
        show { SearchResultRow(SearchRow(set, matched = "title", excerpt = null), progress = null, watched = false, onPlay = {}) }
        val resting = colourOf("Arrival")
        assertNotEquals(accent, resting, "the two states must differ for this check to mean anything")
        compose.onNodeWithText("Arrival").performMouseInput { enter(center) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(accent, colourOf("Arrival"))
        compose.onNodeWithText("Arrival").performMouseInput { exit(Offset(-10f, -10f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(resting, colourOf("Arrival"))
    }
}
