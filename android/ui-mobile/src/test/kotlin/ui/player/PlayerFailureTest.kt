package ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import model.ReportWords
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import player.ReportStage
import player.ReportUi

/** The failure screen's Report, which is the only one on screen while a title has failed: it says how the send stands. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class PlayerFailureTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun reportIsShutWhileSendingAndReadsReportedOnceSent() {
        var report by mutableStateOf(ReportUi())
        compose.setContent { PlayerFailure("Playback failed", onRetry = {}, onReport = {}, report = report) }
        compose.onNodeWithText(ReportWords.BUTTON).assertIsEnabled()

        report = ReportUi(ReportStage.SENDING)
        compose.onNodeWithText(ReportWords.BUTTON).assertIsNotEnabled()

        report = ReportUi(ReportStage.SENT)
        compose.onNodeWithText(ReportWords.BUTTON).assertDoesNotExist()
        compose.onNodeWithText(ReportWords.SENT).assertIsNotEnabled()
        compose.onNodeWithText("Retry").assertIsEnabled()
    }
}
