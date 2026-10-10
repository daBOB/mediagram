package ui.tv.catalog.home

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import catalog.SetCard
import designsystem.TvScale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ui.tv.catalog.TvOfflineBadgeTag
import ui.tv.catalog.TvScreenStateTest
import ui.tv.catalog.set
import ui.tv.fontSizeOf

/**
 * The Continue card's caption as the web sets it (`home.css:248-273`): the
 * episode line capped at 42% of the caption, the progress bar on that same
 * line at the far end, so the two never meet — on the box the bar ran
 * straight through "S1E2 · Der Fall Charly" — and the offline badge on a
 * line of its own below, never sharing the bar's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvResumeCardTest : TvScreenStateTest() {
    @Test
    fun theEpisodeLineStopsAtTheWebsCapBeforeTheBar() {
        show { TvResumeCard(card = card(held = false), onOpen = {}) }

        val card = compose.onNodeWithTag(TvResumeCardTag).getUnclippedBoundsInRoot()
        val sub = subBounds()
        val bar = compose.onNodeWithTag(TvResumeProgressTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val caption = (card.right - card.left) - CaptionInset * 2

        assertTrue(sub.right - sub.left <= caption * 0.42f + Slack, "the line is capped at 42% of the caption: $sub in $card")
        assertTrue(sub.right <= bar.left, "the line ends before the bar starts: $sub, $bar")
        assertTrue(bar.top >= sub.top && bar.bottom <= sub.bottom, "the bar sits on the line it belongs to: $bar against $sub")
    }

    @Test
    fun theOfflineBadgeTakesALineOfItsOwnBelowTheBar() {
        show { TvResumeCard(card = card(held = true), onOpen = {}) }

        val sub = subBounds()
        val bar = compose.onNodeWithTag(TvResumeProgressTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val badge = compose.onNodeWithTag(TvOfflineBadgeTag, useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertTrue(bar.top >= sub.top && bar.bottom <= sub.bottom, "the badge does not push the bar off the episode line: $bar against $sub")
        assertTrue(badge.top >= bar.bottom, "the badge is below the bar, not beside it: $badge, $bar")
    }

    @Test
    fun theCaptionIsSetAtTheWebsSizes() {
        show { TvResumeCard(card = card(held = true), onOpen = {}) }

        assertEquals(14 * TvScale, compose.fontSizeOf("Tatort").value, "`.resume-name`, 0.875rem")
        assertEquals(12 * TvScale, compose.fontSizeOf(SUB).value, "`.resume-sub`, 0.75rem")
        assertEquals(12 * TvScale, compose.fontSizeOf("offline").value, "`.badge`, 0.75rem")
    }

    private fun subBounds(): DpRect = compose.onNodeWithText(SUB, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun card(held: Boolean) =
        SetCard(
            set = set("ep-2", Kind.EPISODE, "Der Fall Charly und die lange Nacht", show = "Tatort", addedAt = 0, episode = 2).copy(season = 1),
            caption = "34 min left",
            progress = 0.4f,
            watched = false,
            held = held,
        )

    private companion object {
        const val SUB = "S1E2 · Der Fall Charly und die lange Nacht"

        /** `.resume-text{padding: … 12px …}` on either side. */
        val CaptionInset = androidx.compose.ui.unit.Dp(12f)

        /** Text measures to fractions of a dp. */
        val Slack = androidx.compose.ui.unit.Dp(1f)
    }
}
