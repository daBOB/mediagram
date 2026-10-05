package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import data.CatalogRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import player.CONTROLS_LINGER_MS
import testing.WatchStateFixture
import ui.tv.catalog.set
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [THREE_TITLE_RUN] over two seasons — set-zero alone in the first, already
 * watched; the open set-one and a 45-minute set-two in the second — listed
 * in full by the catalogue, as the episode list reads it.
 */
private fun seasonsFixture(): TvPlayerFixture {
    val sets =
        listOf(
            set("set-zero", Kind.EPISODE, "Before", show = "A Show", addedAt = 1, episode = 3, durationSecs = 600).copy(season = 1),
            set("set-one", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 1, episode = 1, durationSecs = 600).copy(season = 2),
            set("set-two", Kind.EPISODE, "After", show = "A Show", addedAt = 1, episode = 2, durationSecs = 2_700).copy(season = 2),
        )
    val catalog = mockk<CatalogRepository>(relaxed = true)
    coEvery { catalog.sets() } returns sets
    for (one in sets) coEvery { catalog.mediaSet(one.setId) } returns one
    return TvPlayerFixture(WatchStateFixture(seed = { setWatched("set-zero", true) }), catalog = catalog)
}

/**
 * The episode list ☰ opens down the right of the picture: on the season
 * playing, with the remote on the row that reads "Now playing"; a season
 * switcher over it; a pick plays and closes it; and Back, a Back key and
 * the controls' fade each treat it as the thing in front.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerEpisodesTest : TvPlayerScreenHarness() {
    override val run = THREE_TITLE_RUN

    override fun makeFixture() = seasonsFixture()

    @Test
    fun theListOpensOnThePlayingSeasonWithTheRemoteOnNowPlaying() {
        openEpisodes()

        compose.onNodeWithTag(TvEpisodeSidebarTag).assertExists()
        compose.onNodeWithText("Season 2").assertExists()
        compose.onNodeWithText(NOW_PLAYING, substring = true).assertIsFocused()
        compose.onNodeWithText("45m", substring = true).assertExists()
    }

    @Test
    fun theSwitcherShowsTheOtherSeasonWithItsFinishedTitleTicked() {
        openEpisodes()

        compose.onNodeWithContentDescription("Previous season").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.onNodeWithText("Season 1").assertExists()
        compose.onNodeWithText("Before", substring = true).assertExists()
        compose.onNodeWithText("✓", substring = true).assertExists()
        compose.onNodeWithContentDescription("Previous season").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next season").assertIsEnabled()
    }

    @Test
    fun pickingARowPlaysItAndClosesTheList() {
        openEpisodes()

        press(Key.DirectionDown)
        compose.onNodeWithText("After", substring = true).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(listOf("set-two"), TvPlayerTestActivity.switches)
        compose.onNodeWithTag(TvEpisodeSidebarTag).assertDoesNotExist()
    }

    @Test
    fun theNowPlayingRowIsNotAPick() {
        openEpisodes()

        press(Key.DirectionCenter)

        assertEquals(emptyList(), TvPlayerTestActivity.switches)
        compose.onNodeWithTag(TvEpisodeSidebarTag).assertExists()
    }

    @Test
    fun leftAndRightNeitherSkipNorLeaveTheList() {
        openEpisodes()

        press(Key.DirectionLeft)
        press(Key.DirectionRight)

        assertEquals(42_000L, fixture.positionMs)
        compose.onNodeWithText(NOW_PLAYING, substring = true).assertIsFocused()
    }

    @Test
    fun backClosesTheListOntoEpisodesThenPutsTheControlsAway() {
        openEpisodes()

        back()
        compose.onNodeWithTag(TvEpisodeSidebarTag).assertDoesNotExist()
        compose.onNodeWithContentDescription("Episodes").assertIsFocused()
        verify(exactly = 0) { fixture.media.stop() }

        back()
        compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()
    }

    @Test
    fun aBackKeyClosesTheListToo() {
        openEpisodes()

        pressBackKey()

        compose.onNodeWithTag(TvEpisodeSidebarTag).assertDoesNotExist()
        compose.onNodeWithContentDescription("Episodes").assertIsFocused()
    }

    @Test
    fun theControlsStayUpWhileTheListIsOpen() {
        openEpisodes()

        compose.mainClock.advanceTimeBy(CONTROLS_LINGER_MS + 500)
        compose.waitForIdle()

        compose.onNodeWithTag(TvSeekBarTag).assertExists()
        compose.onNodeWithTag(TvEpisodeSidebarTag).assertExists()
    }

    @Test
    fun theCardStandsClearOfTheListAndComesBackWhenItCloses() {
        val normal = compose.onNodeWithTag(TvBottomBandTag).getBoundsInRoot()

        openEpisodes()

        val card = compose.onNodeWithTag(TvBottomBandTag).getBoundsInRoot()
        val list = compose.onNodeWithTag(TvEpisodeSidebarTag).getBoundsInRoot()
        // The 32 dp the card keeps from every edge: a zero gap would pass a plain "not overlapping" check.
        assertTrue(kotlin.math.abs((list.left - card.right).value - 32f) < 0.5f, "the card ends at ${card.right}, the list starts at ${list.left}")
        compose.onNodeWithContentDescription("Pause").assertExists()

        back()

        assertEquals(normal, compose.onNodeWithTag(TvBottomBandTag).getBoundsInRoot())
    }

    private fun openEpisodes() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription("Episodes").fetchSemanticsNodes().isNotEmpty()
        }
        toTransport(hasContentDescription("Episodes"))
        press(Key.DirectionCenter)
    }
}

/** A title opened on its own has no run, and so no list to open. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerNoRunEpisodesTest : TvPlayerScreenHarness() {
    @Test
    fun thereIsNoEpisodesButton() {
        compose.onNodeWithContentDescription("Episodes").assertDoesNotExist()
    }
}
