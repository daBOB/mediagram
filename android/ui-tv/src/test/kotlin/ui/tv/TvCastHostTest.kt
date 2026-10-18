package ui.tv

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import kotlinx.coroutines.runBlocking
import model.DailyLimitWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.player.TvPlayerScreenTag
import ui.tv.profile.TvProfilePickerFirstTileTag
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A cast the TV accepts, through the real library: the player opens for it
 * from wherever the TV is — Home, a title page, the profile picker — and in
 * place of a title already playing. Once the cast is over the TV comes back
 * to its own profile's Home, or to the picker it had, and its own profile
 * stays unchosen. The marks are gone while a cast plays: they would file the
 * title under the TV's own profile.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvCastHostTest : TvCastHarness() {

    @Test
    fun aCastOpensThePlayerFromHome() {
        launch()
        compose.onNode(WatchNow).assertExists()

        cast("film-2")

        playing("Film 2")
        assertNull(fixture.casts.takeCast(), "the player took the cast")
    }

    /** The title the TV was playing is left as any title is: its place kept for the TV's own profile. */
    @Test
    fun aCastReplacesAnOpenPlayer() {
        launch()
        playFromItsPage("Film 1")

        cast("film-2", replace = true)
        playing("Film 2")
        lose()

        open(hasContentDescription("Film 1") and hasClickAction())
        compose.onNodeWithText("▶ Resume from 0:42").assertExists()
    }

    /** The very title the TV is playing, cast to it: opened again, as the caster's. */
    @Test
    fun aCastOfTheTitlePlayingIsTakenToo() {
        launch()
        playFromItsPage("Film 1")

        cast("film-1", replace = true)

        playing("Film 1")
        assertNull(fixture.casts.takeCast(), "the player took the cast")
        compose.onNodeWithText("Add to list").assertDoesNotExist()
    }

    @Test
    fun aCastOverReturnsToHome() {
        launch()
        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNode(hasText("Movies") and isSelected()).assertExists()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText("▶ Play").assertExists()

        cast("film-2")
        playing("Film 2")
        lose()

        compose.onNodeWithTag(TvPlayerScreenTag).assertDoesNotExist()
        compose.onNodeWithText("▶ Play").assertDoesNotExist()
        compose.onNode(hasText("Home") and isSelected()).assertExists()
        compose.onNode(WatchNow).assertIsFocused()
        // Where the cast stopped is the caster's place, not the TV's own profile's.
        val placed = { profile: String -> runBlocking { fixture.core.snapshot(profile) }.progress.map { it.setId } }
        assertEquals(listOf("film-2"), placed("bo"))
        assertEquals(emptyList(), placed("ada"))
    }

    /** Back out of the player ends the cast here, and the TV goes Home as for any other end. */
    @Test
    fun aCastLeftWithBackReturnsToHomeToo() {
        launch()
        open(hasContentDescription("Film 1") and hasClickAction())
        compose.onNodeWithText("▶ Play").assertExists()

        cast("film-2")
        playing("Film 2")
        back() // the controls
        back() // the cast

        compose.onNodeWithTag(TvPlayerScreenTag).assertDoesNotExist()
        compose.onNodeWithText("▶ Play").assertDoesNotExist()
        compose.onNode(WatchNow).assertExists()
    }

    @Test
    fun aCastWithNoProfileOpensOverThePicker() {
        launch(chosen = null)
        compose.onNodeWithTag(TvProfilePickerFirstTileTag).assertExists()

        cast("film-2")
        playing("Film 2")
        compose.onNodeWithTag(TvProfilePickerFirstTileTag).assertDoesNotExist()
        lose()

        compose.onNodeWithTag(TvPlayerScreenTag).assertDoesNotExist()
        compose.onNodeWithTag(TvProfilePickerFirstTileTag).assertExists()
        assertNull(fixture.core.chosen, "a cast chooses no profile for the TV")
    }

    @Test
    fun marksAreHiddenDuringACast() {
        launch()
        playFromItsPage("Film 1")
        compose.onNodeWithText("Add to list").assertExists()

        cast("film-2", replace = true)

        playing("Film 2")
        compose.onNodeWithText("Add to list").assertDoesNotExist()
        compose.onNodeWithText("My List").assertDoesNotExist()
    }

    /** A kid caster's day runs out at Next, and the cast is over while that notice is up: it is read to its end before the TV goes Home. */
    @Test
    fun aTimesUpNoticeAtTheCastsEndIsNotCutShort() {
        launch()
        cast("e1", profile = "kim")
        playing("Pilot")
        fixture.core.watchedTodayByProfile["kim"] = 1_800.0
        key(KeyEvent.KEYCODE_MEDIA_NEXT)
        compose.onNodeWithText(DailyLimitWords.OUT).assertExists()

        lose()
        wait(Duration.ofSeconds(5))
        compose.onNodeWithText(DailyLimitWords.OUT).assertExists()
        compose.onNodeWithTag(TvPlayerScreenTag).assertExists()

        // Past the notice's eight seconds.
        wait(Duration.ofSeconds(4))
        compose.onNodeWithTag(TvPlayerScreenTag).assertDoesNotExist()
        compose.onNode(WatchNow).assertExists()
    }
}
