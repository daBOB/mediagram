package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import data.CatalogRepository
import io.mockk.coEvery
import io.mockk.mockk
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import playback.SummarySource
import player.CONTROLS_LINGER_MS
import ui.tv.catalog.set
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A title of [kind] with notes long enough to need paging: a heading, then forty numbered paragraphs. */
internal fun notesFixture(kind: Kind): TvPlayerFixture {
    val titled =
        set("set-one", kind, "Pilot", show = "A Show", addedAt = 1, episode = 4, durationSecs = 600)
            .copy(hasSummary = true)
    val catalog = mockk<CatalogRepository>(relaxed = true)
    coEvery { catalog.mediaSet("set-one") } returns titled
    val text = "### What this covers\n\n" + (1..40).joinToString("\n\n") { "Paragraph $it of the notes, with [a link](https://example.com) in it." }
    val summary =
        object : SummarySource {
            override suspend fun load(setId: String): String? = text.takeIf { setId == "set-one" }
        }
    return TvPlayerFixture(catalog = catalog, summary = summary)
}

/**
 * [TvPlayerScreen] on a film with notes, which wait for the Notes button:
 * the column opens beside the picture with the remote in it, pages by
 * D-pad, and Back closes it before anything else of the player's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerNotesTest : TvPlayerScreenHarness() {
    override fun makeFixture() = notesFixture(Kind.EPISODE)

    /** The Notes button, as against the column's own "Notes" head. */
    private fun notesButton() = compose.onNode(hasText("Notes") and hasClickAction())

    /** From play/pause, past the forward skip, to Notes, and pressed. */
    private fun openNotes() {
        compose.onNodeWithTag(TvNotesTag).assertDoesNotExist()
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        notesButton().assertIsFocused()
        press(Key.DirectionCenter)
    }

    @Test
    fun theNotesButtonOpensTheColumnBesideThePictureWithTheRemoteInIt() {
        openNotes()
        compose.onNodeWithTag(TvNotesTag).assertIsFocused()
        compose.onNodeWithText("What this covers").assertExists()
        // Beside, not over: the column starts where the picture's stage ends.
        val column = compose.onNodeWithTag(TvNotesTag).getBoundsInRoot()
        val seekBar = compose.onNodeWithTag(TvSeekBarTag).getBoundsInRoot()
        assertTrue(seekBar.right <= column.left, "the controls end at ${seekBar.right}, the notes start at ${column.left}")
    }

    @Test
    fun aLinkKeepsItsWordsWithNothingToPress() {
        openNotes()
        compose.onNodeWithText("Paragraph 1 of the notes, with a link in it.").assertExists()
    }

    @Test
    fun downAndUpPageThroughTheNotesAndLeaveTheFilmAlone() {
        openNotes()
        val before = fixture.positionMs
        val top = compose.onNodeWithText("What this covers").getBoundsInRoot().top
        press(Key.DirectionDown)
        val paged = compose.onNodeWithText("What this covers").getBoundsInRoot().top
        assertTrue(paged < top, "a page down moves the notes up: $top to $paged")
        press(Key.DirectionUp)
        assertEquals(top, compose.onNodeWithText("What this covers").getBoundsInRoot().top)
        compose.onNodeWithTag(TvNotesTag).assertIsFocused()
        assertEquals(before, fixture.positionMs)
    }

    @Test
    fun backClosesTheNotesFirstAndHandsTheRemoteBackToTheButton() {
        openNotes()
        back()
        compose.onNodeWithTag(TvNotesTag).assertDoesNotExist()
        notesButton().assertIsFocused()
        back()
        compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()
        compose.onNodeWithText("Library").assertDoesNotExist()
    }

    @Test
    fun theButtonClosesTheNotesAgain() {
        openNotes()
        press(Key.DirectionLeft)
        notesButton().assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvNotesTag).assertDoesNotExist()
        notesButton().assertIsFocused()
    }

    @Test
    fun withTheControlsAwayTheNotesKeepTheRemote() {
        openNotes()
        compose.mainClock.advanceTimeBy(CONTROLS_LINGER_MS + 500)
        compose.waitForIdle()
        compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()
        compose.onNodeWithTag(TvNotesTag).assertIsFocused()
        // Down pages rather than raising the seek bar; Centre still pauses.
        press(Key.DirectionDown)
        compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()
        press(Key.DirectionCenter)
        compose.onNodeWithContentDescription("Play").assertIsFocused()
    }
}
