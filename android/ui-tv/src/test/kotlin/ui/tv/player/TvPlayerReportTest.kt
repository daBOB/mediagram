package ui.tv.player

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import model.Kind
import model.ReportReason
import model.ReportWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import player.PlayerUiState
import player.ReportStage
import ui.tv.setup.TvTextQuestionFieldTag
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Report, driven by the remote: the last stop along the top bar, with Down
 * from it back to the seek bar, and beside Retry once a title has failed.
 * Its dialog opens on the first reason, brings the keyboard up only when OK
 * is pressed on the note, and Back leaves it without sending.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerReportTest : TvPlayerScreenHarness() {
    @Test
    fun rightFromTheLastMarkReachesReportWhichEndsTheBar() {
        toTopBar(hasText("Add to list"))
        press(Key.DirectionRight)
        compose.onNode(reportButton).assertIsFocused()

        press(Key.DirectionRight)
        compose.onNode(reportButton).assertIsFocused()

        press(Key.DirectionDown)
        compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()
    }

    @Test
    fun theDialogOpensOnTheFirstReasonAndTheDpadNeverBringsUpAField() {
        openReport()
        compose.onNode(inDialog(ReportReason.entries.first().words)).assertIsFocused()
        compose.onNode(inDialog(ReportWords.SEND)).assertIsNotEnabled()

        // Down through the reasons and the note to the buttons, then along them.
        repeat(ReportReason.entries.size + 2) {
            pressInDialog(Key.DirectionDown)
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        }
        pressInDialog(Key.DirectionRight)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun okOnTheNoteAsksForItAndTheDialogComesBackOnTheNote() {
        openReport()
        alongInDialog(inDialog(ReportWords.NOTE), listOf(Key.DirectionDown))
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)

        pressInDialog(Key.DirectionCenter)
        compose.onNodeWithTag(TvTextQuestionFieldTag).assertIsFocused()
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput("stutters at the start")
        backInDialog()

        compose.onNodeWithText(ReportWords.HEADING).assertExists()
        compose.onNode(inDialog("stutters at the start")).assertIsFocused()
    }

    @Test
    fun aPickedReasonSendsAndReportedStillTakesTheRemote() {
        openReport()
        pressInDialog(Key.DirectionCenter)
        compose.onNode(inDialog(ReportReason.entries.first().words)).assertIsSelected()
        alongInDialog(inDialog(ReportWords.SEND), listOf(Key.DirectionDown, Key.DirectionRight))
        pressInDialog(Key.DirectionCenter)

        assertEquals(1, fixture.reports.size)
        assertTrue(fixture.reports.single().contains(ReportReason.WONT_PLAY.words), fixture.reports.single())
        compose.onNodeWithText(ReportWords.HEADING).assertDoesNotExist()
        compose.onNode(sentButton).assertIsFocused()
        compose.onNode(sentButton).assertIsNotEnabled()

        // Sent, it is still a stop that hands the remote on, not a dead end.
        press(Key.DirectionLeft)
        compose.onNodeWithText("Add to list").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNode(sentButton).assertIsFocused()
        press(Key.DirectionRight)
        compose.onNode(sentButton).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()
    }

    @Test
    fun aFailedSendKeepsTheDialogOpenAndSaysSo() {
        fixture.reportFails = true
        openReport()
        pressInDialog(Key.DirectionCenter)
        alongInDialog(inDialog(ReportWords.SEND), listOf(Key.DirectionDown, Key.DirectionRight))
        pressInDialog(Key.DirectionCenter)

        assertEquals(ReportStage.FAILED, controller.get().playerViewModel.report.value.stage)
        compose.onNodeWithText(ReportWords.HEADING).assertExists()
        compose.onNode(inDialog(ReportWords.FAILED)).assertExists()
        compose.onNode(inDialog(ReportWords.SEND)).assertIsEnabled()
    }

    @Test
    fun backLeavesTheDialogWithoutSending() {
        openReport()
        pressInDialog(Key.DirectionCenter)
        backInDialog()

        compose.onNodeWithText(ReportWords.HEADING).assertDoesNotExist()
        assertEquals(emptyList(), fixture.reports)
        assertEquals(ReportStage.IDLE, controller.get().playerViewModel.report.value.stage)
        compose.onNode(reportButton).assertIsFocused()
        compose.onNode(reportButton).assertIsEnabled()
    }

    @Test
    fun aFailureOffersReportBesideRetryOpenOnWontPlay() {
        compose.runOnUiThread { fixture.fail() }
        compose.waitForIdle()
        assertIs<PlayerUiState.Failed>(controller.get().playerViewModel.state.value)
        compose.onNodeWithText("Retry").assertIsFocused()

        press(Key.DirectionRight)
        compose.onNode(reportButton).assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithText("Retry").assertIsFocused()

        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.onNode(inDialog(ReportReason.WONT_PLAY.words)).assertIsSelected()

        // The top bar is gone over a failure, so the row itself says it was sent — and is still no dead end.
        alongInDialog(inDialog(ReportWords.SEND), listOf(Key.DirectionDown, Key.DirectionRight))
        pressInDialog(Key.DirectionCenter)
        assertEquals(1, fixture.reports.size)
        compose.onNodeWithText(ReportWords.HEADING).assertDoesNotExist()
        compose.onAllNodes(reportButton).assertCountEquals(0)
        compose.onNode(sentButton).assertIsNotEnabled()
        compose.onNode(sentButton).assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithText("Retry").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNode(sentButton).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText(ReportWords.HEADING).assertDoesNotExist()
    }

    /** From play/pause up to Report along the top, and pressed. */
    private fun openReport() {
        toTopBar(reportButton)
        press(Key.DirectionCenter)
        compose.onNodeWithText(ReportWords.HEADING).assertExists()
    }

    /** [keys] in turn, each pressed in the dialog until [target] holds the remote. */
    private fun alongInDialog(
        target: SemanticsMatcher,
        keys: List<Key>,
    ) {
        for (key in keys) {
            repeat(MAX_STEPS) {
                if (compose.onAllNodes(target and isFocused()).fetchSemanticsNodes().isNotEmpty()) return
                pressInDialog(key)
            }
        }
        compose.onNode(target).assertIsFocused()
    }

    /** Back as a remote sends it to the window on top — the dialog's, which the player underneath never hears. */
    private fun backInDialog() {
        compose.runOnUiThread {
            val dialog = ShadowDialog.getLatestDialog()
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        compose.waitForIdle()
    }

    private fun inDialog(text: String) = hasText(text) and hasAnyAncestor(isDialog())

    private companion object {
        const val MAX_STEPS = 8
        val reportButton = hasText(ReportWords.BUTTON) and hasClickAction()
        val sentButton = hasText(ReportWords.SENT) and hasClickAction()
    }
}

/** With notes, Report follows Notes at the end of the bar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerNotesReportTest : TvPlayerScreenHarness() {
    override fun makeFixture() = notesFixture(Kind.EPISODE)

    @Test
    fun rightFromNotesReachesReportAndDownFromItTheSeekBar() {
        val notes = hasText("Notes") and hasClickAction()
        val report = hasText(ReportWords.BUTTON) and hasClickAction()
        toTopBar(notes)
        press(Key.DirectionRight)
        compose.onNode(report).assertIsFocused()

        press(Key.DirectionRight)
        compose.onNode(report).assertIsFocused()

        press(Key.DirectionDown)
        compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()
    }
}
