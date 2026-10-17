package player

import data.ReportChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import model.ReportReason
import model.ReportFacts
import model.reportText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a report does between a viewer's tap and the channel: one send at a time, and never for the wrong title. */
class PlayerReportTest {
    private class FakeChannel : ReportChannel {
        val sent = mutableListOf<String>()
        var failWith: Throwable? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun send(text: String) {
            sent += text
            gate?.await()
            failWith?.let { throw it }
        }
    }

    private val facts = ReportFacts(
        reason = ReportReason.WONT_PLAY, title = "Dune", note = null, profile = "Anna", screen = "TV",
        device = "Acme X", app = "Android 0.126.0", positionSeconds = 12.0, played = "direct", held = false,
        error = null, setId = "set1",
    )

    private fun TestScope.report(channel: ReportChannel) = PlayerReport(channel, this)

    @Test
    fun aSendHandsTheReportTextToTheChannelAndEndsSent() = runTest {
        val channel = FakeChannel()
        val report = report(channel)
        report.send(facts)
        runCurrent()
        assertEquals(listOf(reportText(facts)), channel.sent)
        assertEquals(ReportStage.SENT, report.ui.value.stage)
        assertFalse(report.ui.value.open)
    }

    @Test
    fun aSecondSendWhileSendingOrAfterSentSendsNothing() = runTest {
        val channel = FakeChannel().apply { gate = CompletableDeferred() }
        val report = report(channel)
        report.send(facts)
        runCurrent()
        assertEquals(ReportStage.SENDING, report.ui.value.stage)
        report.send(facts)
        runCurrent()
        assertEquals(1, channel.sent.size)
        channel.gate!!.complete(Unit)
        runCurrent()
        report.send(facts)
        runCurrent()
        assertEquals(1, channel.sent.size)
    }

    @Test
    fun aThrowingChannelEndsFailedAndARetrySends() = runTest {
        val channel = FakeChannel().apply { failWith = IllegalStateException("offline") }
        val report = report(channel)
        report.openReport(fromFailure = false)
        report.send(facts)
        runCurrent()
        assertEquals(ReportStage.FAILED, report.ui.value.stage)
        assertTrue(report.ui.value.open) // the sheet stays, showing why
        channel.failWith = null
        report.send(facts)
        runCurrent()
        assertEquals(2, channel.sent.size)
        assertEquals(ReportStage.SENT, report.ui.value.stage)
    }

    @Test
    fun anErrorBelongsToTheTitleThatHadIt() = runTest {
        val channel = FakeChannel()
        val report = report(channel)
        report.failed("ERROR_CODE_X")
        report.send(facts)
        runCurrent()
        assertTrue("Error: ERROR_CODE_X" in channel.sent.single())
        report.titleOpened()
        assertNull(report.lastError)
        report.send(facts)
        runCurrent()
        assertFalse("Error:" in channel.sent.last())
    }

    @Test
    fun aReplyArrivingAfterAnotherTitleOpenedMarksNothing() = runTest {
        val channel = FakeChannel().apply { gate = CompletableDeferred() }
        val report = report(channel)
        report.send(facts)
        runCurrent()
        report.titleOpened()
        channel.gate!!.complete(Unit)
        runCurrent()
        assertEquals(ReportStage.IDLE, report.ui.value.stage)
    }

    @Test
    fun theSheetPresetsWontPlayOnlyFromAFailureAndStaysShutOnceReported() = runTest {
        val report = report(FakeChannel())
        report.openReport(fromFailure = false)
        assertNull(report.ui.value.preset)
        report.closeReport()
        report.openReport(fromFailure = true)
        assertEquals(ReportReason.WONT_PLAY, report.ui.value.preset)
        report.send(facts)
        runCurrent()
        report.openReport(fromFailure = true)
        assertFalse(report.ui.value.open)
    }
}
