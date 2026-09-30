package ui.tv.player

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import data.CatalogRepository
import io.mockk.coEvery
import io.mockk.mockk
import model.Kind
import model.SubtitleTrackInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import playback.SubtitleTrackSource
import ui.tv.catalog.set

private fun fixtureWith(tracks: List<SubtitleTrackInfo>): TvPlayerFixture {
    val titled =
        set("set-one", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 1, episode = 4, durationSecs = 600)
            .copy(subtitles = tracks)
    val catalog = mockk<CatalogRepository>(relaxed = true)
    coEvery { catalog.mediaSet("set-one") } returns titled
    return TvPlayerFixture(catalog = catalog, subtitles = mockk<SubtitleTrackSource>(relaxed = true))
}

private fun panelText(text: String) = hasText(text) and hasAnyAncestor(hasTestTag(TvSettingsPanelTag))

/**
 * A title whose only track is forced: the panel offers size and sync for
 * those lines but no language rows, and there is no CC button, since
 * nothing regular is there to turn on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvPlayerForcedOnlySubtitleGateTest : TvPlayerScreenHarness() {
    override fun makeFixture() = fixtureWith(listOf(SubtitleTrackInfo(track = 0, lang = "de", forced = true, sdh = false, label = "")))

    @Test
    fun theStyleSectionShowsAndTheLanguageRowsDoNot() {
        compose.waitUntil(timeoutMillis = 5_000) { controller.get().playerViewModel.choices.value.subtitleStyleVisible }
        openSettings()

        compose.onNode(panelText("Subtitle style")).assertExists()
        compose.onNode(panelText("Sync")).assertExists()
        compose.onNode(panelText("Subtitles")).assertDoesNotExist()
        compose.onNodeWithContentDescription("Subtitles off").assertDoesNotExist()
    }
}

/** A title with no subtitle track: neither subtitle section, no CC button. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvPlayerNoSubtitlesGateTest : TvPlayerScreenHarness() {
    override fun makeFixture() = fixtureWith(emptyList())

    @Test
    fun neitherSectionNorButtonAppears() {
        // The set has loaded once its title line is up; absence before that proves nothing.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("A Show · S1E4 · Pilot").fetchSemanticsNodes().isNotEmpty()
        }
        openSettings()

        compose.onNode(panelText("Subtitles")).assertDoesNotExist()
        compose.onNode(panelText("Subtitle style")).assertDoesNotExist()
        compose.onNodeWithContentDescription("Subtitles off").assertDoesNotExist()
        compose.onNodeWithText("CC ○").assertDoesNotExist()
    }
}

/** A title with a regular track: the CC button starts off and one press turns it on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvPlayerCcButtonTest : TvPlayerScreenHarness() {
    override fun makeFixture() = fixtureWith(listOf(SubtitleTrackInfo(track = 0, lang = "en", forced = false, sdh = false, label = "")))

    @Test
    fun pressingItTurnsTheSubtitlesOn() {
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithContentDescription("Subtitles off").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithContentDescription("Subtitles off").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Subtitles on").assertExists()
    }
}
