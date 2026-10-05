package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The ends of each row of the card and of the top bar hold the remote:
 * Left from the first control and Right from the last do nothing, rather
 * than jumping to another row by geometry. Up and Down are what move
 * between rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerRowEndsTest : TvPlayerScreenHarness() {
    @Test
    fun theToolsRowStopsAtCcAndAtFraming() {
        press(Key.DirectionUp)
        compose.onNodeWithContentDescription("Subtitles").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithContentDescription("Subtitles").assertIsFocused()

        along(hasContentDescription("Framing"))
        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("Framing").assertIsFocused()
    }

    @Test
    fun theTransportRowStopsAtRestartAndAtStats() {
        toTransport(hasContentDescription("Restart"), Key.DirectionLeft)
        press(Key.DirectionLeft)
        compose.onNodeWithContentDescription("Restart").assertIsFocused()

        along(hasContentDescription("Stats"))
        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("Stats").assertIsFocused()
    }

    @Test
    fun theTopBarStopsAtMyListAndAtAddToList() {
        toTopBar(hasText("My List"))
        press(Key.DirectionLeft)
        compose.onNodeWithText("My List").assertIsFocused()

        along(hasText("Add to list"))
        press(Key.DirectionRight)
        compose.onNodeWithText("Add to list").assertIsFocused()
    }
}

/** With a run, ☰ ends the transport row. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerRunRowEndsTest : TvPlayerScreenHarness() {
    override val run = THREE_TITLE_RUN

    override fun makeFixture() = runFixture()

    @Test
    fun theTransportRowStopsAtEpisodes() {
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithContentDescription("Episodes").fetchSemanticsNodes().isNotEmpty() }
        toTransport(hasContentDescription("Episodes"))
        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("Episodes").assertIsFocused()
    }
}

/** With notes, Notes ends the top bar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerNotesRowEndsTest : TvPlayerScreenHarness() {
    override fun makeFixture() = notesFixture(Kind.EPISODE)

    @Test
    fun theTopBarStopsAtNotes() {
        toTopBar(hasText("Notes") and hasClickAction())
        press(Key.DirectionRight)
        compose.onNode(hasText("Notes") and hasClickAction()).assertIsFocused()
    }
}
