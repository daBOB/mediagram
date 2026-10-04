package ui.tv.catalog

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import catalog.GenreIndexEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * [TvGenresIndex]: a tile per genre with its name and its count spelled
 * across the art, four to a line under the web's shelf head; where arrival
 * lands and how the remote walks the tiles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvGenresIndexStateTest : TvScreenStateTest() {
    private val genres = listOf(GenreIndexEntry("Drama", 21, null), GenreIndexEntry("Comedy", 12, null)) +
        (1..4).map { GenreIndexEntry("Genre $it", 1, null) }

    @Test
    fun eachGenreIsATileWithItsNameAndCountUnderTheShelfHead() {
        show { TvGenresIndex(genres, onOpenGenre = {}) }

        compose.onNodeWithText("Genres").assertExists()
        compose.onNodeWithText("SIX GENRES").assertExists()
        compose.onNodeWithText("Drama").assertExists()
        compose.onNodeWithText("21 titles").assertExists()
        compose.onNodeWithText("twelve titles").assertExists()
        compose.onNodeWithText("Genre 1").assertExists()
    }

    @Test
    fun arrivalLandsOnTheFirstTileAndTheRemoteWalksFourToALine() {
        var opened: String? = null
        show { TvGenresIndex(genres, onOpenGenre = { opened = it }) }

        compose.onNodeWithText("Drama").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithText("Comedy").assertIsFocused()
        press(Key.DirectionLeft)
        press(Key.DirectionDown)
        compose.onNodeWithText("Genre 3").assertIsFocused()

        compose.onNodeWithText("Genre 3").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("Genre 3", opened)
    }

    @Test
    fun comingBackLandsOnTheGenreThatWasOpened() {
        show { TvGenresIndex(genres, onOpenGenre = {}, restoreKey = "Genre 4") }

        compose.onNodeWithText("Genre 4").assertIsFocused()
    }

    @Test
    fun noGenresSaysSoAsAStopTheRemoteRestsOn() {
        show { TvGenresIndex(emptyList(), onOpenGenre = {}) }

        compose.onNodeWithText("Nothing in the library has a genre recorded.").assertIsFocused()
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
}
