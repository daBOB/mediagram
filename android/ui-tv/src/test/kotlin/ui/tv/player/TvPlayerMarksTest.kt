package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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
import player.KIDS_CHOICES
import testing.WatchStateFixture
import kotlin.test.assertEquals

/**
 * The player's marks rail, list dialog, statistics and notices, driven by
 * the remote for a grown-up viewer with one list: where Up from the
 * seek bar lands, what each mark writes, and what the controls do while a
 * list is being chosen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerMarksTest : TvPlayerScreenHarness() {
    override fun makeFixture() = TvPlayerFixture(WatchStateFixture(seed = { createList("Favourites") }))

    @Test
    fun upFromTheSeekBarReachesTheMarksAndDownComesBack() {
        repeat(3) { press(Key.DirectionUp) }
        compose.onNodeWithText("My List").assertIsFocused()

        press(Key.DirectionDown)
        compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()
    }

    @Test
    fun theRailCarriesTheThreeMarksAndWatchlistWrites() {
        compose.onNodeWithText("Not for kids").assertExists()
        compose.onNodeWithText("Add to list").assertExists()

        toTopBar(hasText("My List"))
        press(Key.DirectionCenter)

        assertEquals(listOf("set-one"), fixture.repository.snapshot.value.watchlist)
    }

    /** The web's select as a dialog: the three answers, the current one holding the remote; choosing closes it onto the mark. */
    @Test
    fun theKidsMarkOpensTheChoiceAndFromSixMarksFromSix() {
        toTopBar(hasText("Not for kids"))
        compose.onNodeWithText("Not for kids").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("For kids").assertExists()
        compose.onNode(hasText("Not for kids") and hasAnyAncestor(isDialog())).assertIsFocused()
        compose.onNode(hasText("From 12") and hasAnyAncestor(isDialog())).assertExists()

        pressInDialog(Key.DirectionDown)
        pressInDialog(Key.DirectionCenter)

        assertEquals(mapOf("set-one" to 6), fixture.repository.snapshot.value.kidsMarks)
        compose.onNodeWithText("For kids").assertDoesNotExist()
        compose.onNodeWithText("From 6").assertIsFocused()
    }

    /** Open, it holds the controls as the list dialog does; Cancel leaves the mark as it was. */
    @Test
    fun theKidsChoiceHoldsTheControlsAndCancelChangesNothing() {
        toTopBar(hasText("Not for kids"))
        press(Key.DirectionCenter)

        compose.mainClock.advanceTimeBy(CONTROLS_LINGER_MS + 500)
        compose.waitForIdle()
        compose.onNodeWithTag(TvSeekBarTag).assertExists()

        repeat(KIDS_CHOICES.size) { pressInDialog(Key.DirectionDown) }
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).assertIsFocused()
        pressInDialog(Key.DirectionCenter)
        assertEquals(emptyMap(), fixture.repository.snapshot.value.kidsMarks)
        compose.onNodeWithText("Not for kids").assertIsFocused()
    }

    @Test
    fun theStatsButtonTogglesTheOverlay() {
        compose.onNodeWithTag(TvStatsOverlayTag).assertDoesNotExist()
        toTransport(hasContentDescription("Stats"))

        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvStatsOverlayTag).assertExists()
        compose.onNodeWithText("buffer").assertExists()
        compose.onNodeWithContentDescription("Stats").assertIsFocused()

        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvStatsOverlayTag).assertDoesNotExist()
    }

    @Test
    fun addToListFilesTheTitleAndHoldsTheControlsUntilItCloses() {
        toTopBar(hasText("Add to list"))
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
        toTopBar(hasText("Add to list"))
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

        toTopBar(hasText("My List"))
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
