package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import model.Profile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import player.CONTROLS_LINGER_MS
import testing.WatchStateFixture
import kotlin.test.assertEquals

/**
 * The player's marks rail, list dialog, statistics and notices, driven by
 * the remote for a grown-up viewer with one list: where Down from the
 * transport lands, what each mark writes, and what the controls do while a
 * list is being chosen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerMarksTest : TvPlayerScreenHarness() {
    override fun makeFixture() = TvPlayerFixture(WatchStateFixture(seed = { createList("Favourites") }))

    @Test
    fun downFromTheTransportReachesTheRailAndUpComesBack() {
        press(Key.DirectionDown)
        compose.onNodeWithText("My List").assertIsFocused()

        press(Key.DirectionUp)
        compose.onNodeWithContentDescription("Pause").assertIsFocused()
    }

    @Test
    fun theRailCarriesTheThreeMarksAndWatchlistWrites() {
        compose.onNodeWithText("Not for kids").assertExists()
        compose.onNodeWithText("Add to list").assertExists()

        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        assertEquals(listOf("set-one"), fixture.repository.snapshot.value.watchlist)
    }

    @Test
    fun theStatisticsToggleEndsTheRowOfTools() {
        compose.onNodeWithTag(TvStatsOverlayTag).assertDoesNotExist()
        toTool(hasContentDescription("Show playback statistics"))

        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvStatsOverlayTag).assertExists()
        compose.onNodeWithText("buffer").assertExists()
        compose.onNodeWithContentDescription("Hide playback statistics").assertIsFocused()

        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvStatsOverlayTag).assertDoesNotExist()
    }

    @Test
    fun addToListFilesTheTitleAndHoldsTheControlsUntilItCloses() {
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithText("Add to list").assertIsFocused()

        press(Key.DirectionCenter)
        compose.onNodeWithText("☐ Favourites").assertIsFocused()
        pressInDialog(Key.DirectionCenter)
        assertEquals(listOf("set-one"), fixture.repository.snapshot.value.collections.single().items)

        compose.mainClock.advanceTimeBy(CONTROLS_LINGER_MS + 500)
        compose.waitForIdle()
        compose.onNodeWithText("＋ New list").assertExists()
        compose.onNodeWithTag(TvSeekBarTag).assertExists()

        pressInDialog(Key.DirectionDown)
        pressInDialog(Key.DirectionDown)
        compose.onNodeWithText("Done").assertIsFocused()
        pressInDialog(Key.DirectionCenter)
        compose.onNodeWithText("＋ New list").assertDoesNotExist()
        compose.onNodeWithText("Add to list").assertIsFocused()
    }

    @Test
    fun aWriteThatCannotBeConfirmedIsSaidAndThenGoesByItself() {
        // The provider cannot hand the core out, so no list write is confirmed.
        fixture.watchState.provider.beforeCore = { error("keystore unavailable") }
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        pressInDialog(Key.DirectionCenter)
        compose.onNodeWithTag(TvActionNoticeTag).assertExists()

        compose.mainClock.advanceTimeBy(ACTION_NOTICE_MS + 500)
        compose.waitForIdle()
        compose.onNodeWithTag(TvActionNoticeTag).assertDoesNotExist()
    }

    /** A key to the dialog's own window, whose focus is separate from the player's underneath it. */
    private fun pressInDialog(key: Key) {
        compose.onNode(isFocused() and hasAnyAncestor(isDialog())).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
}

/** The same player for a kids profile: a child does not approve titles for itself, so there is no Kids mark to press. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerKidsProfileMarksTest : TvPlayerScreenHarness() {
    override fun makeFixture() = TvPlayerFixture(WatchStateFixture(listOf(Profile("k1", "TV kids", kids = true))))

    @Test
    fun aKidsProfileHasNoKidsMark() {
        compose.onNodeWithText("My List").assertExists()
        compose.onNodeWithText("Add to list").assertExists()
        compose.onNodeWithText("Not for kids").assertDoesNotExist()

        press(Key.DirectionDown)
        press(Key.DirectionRight)
        compose.onNodeWithText("Add to list").assertIsFocused()
    }
}

/** A title already on the list: the button says so, in the web player's words. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerListedMarksTest : TvPlayerScreenHarness() {
    override fun makeFixture() = TvPlayerFixture(WatchStateFixture(seed = { setWatchlisted("set-one", true) }))

    @Test
    fun aListedTitleSaysItIsOnMyList() {
        compose.onNodeWithText("On My List").assertExists()
        compose.onNodeWithText("My List").assertDoesNotExist()
    }
}
