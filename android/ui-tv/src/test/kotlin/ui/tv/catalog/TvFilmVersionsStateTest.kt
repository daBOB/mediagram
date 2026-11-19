package ui.tv.catalog

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import catalog.CatalogUiState
import catalog.groupFilms
import catalog.shelvesOf
import catalog.versionRowsOf
import model.Kind
import model.Progress
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.common.catalog.rememberVersionAsk
import kotlin.test.assertEquals

/**
 * A film held more than once on the television: its page lists the copies
 * in place of Play, each row playing its own copy, and a card that plays
 * straight away asks which copy first, the first row holding the remote.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvFilmVersionsStateTest : TvScreenStateTest() {
    private fun copy(id: String, quality: String) =
        set(id, Kind.MOVIE, "Endgame", addedAt = 0, year = 2019, durationSecs = 10_860)
            .copy(posterKey = "tmdb-movie-1", quality = quality, hdr = "SDR", totalBytes = 4_294_967_296)

    private val copies = listOf(copy("hd", "1080p"), copy("uhd", "2160p"))
    private val film = groupFilms(copies).single()

    @Test
    fun theFilmPageListsItsCopiesInPlaceOfPlay() {
        var played: String? = null
        val watch = WatchSnapshot.Empty.copy(progress = listOf(Progress("hd", at = 1_800.0, duration = 10_860.0, updatedAt = 1)))
        show {
            TvTitlePage(
                set = film, info = null, progress = null, onPlay = {},
                versions = versionRowsOf(film, watch, emptySet()), onPlayVersion = { played = it.setId },
            )
        }

        compose.onNodeWithText("▶ Play").assertDoesNotExist()
        compose.onAllNodesWithTag(TvVersionRowTag).assertCountEquals(2)
        // The runtime leaves the header for the rows, where it tells the copies apart.
        compose.onNodeWithText("2019 · 3h 1m").assertDoesNotExist()
        compose.onNodeWithText("Standard · 4K · 3h 1m · 4.0 GB").assertExists()
        // A place in the 1080p copy is the 4K copy's too: one cut, one timeline.
        compose.onNodeWithContentDescription("Resume Standard · 1080p · 3h 1m · 4.0 GB").assertExists()
        compose.onNodeWithContentDescription("Resume Standard · 4K · 3h 1m · 4.0 GB").assertIsFocused()
        compose.onNodeWithContentDescription("Resume Standard · 4K · 3h 1m · 4.0 GB").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("uhd", played)
    }

    @Test
    fun aCardThatPlaysAsksWhichCopyAndPlaysThePick() {
        var opened: Pair<String, List<String>?>? = null
        val state = CatalogUiState.Ready(shelvesOf(copies))
        show {
            val ask = rememberVersionAsk(state) { setId, run -> opened = setId to run }
            LaunchedEffect(Unit) { ask.play("hd", listOf("uhd", "other")) }
            TvVersionChooser(ask, state)
        }

        compose.onNodeWithText("Endgame").assertExists()
        compose.onNodeWithContentDescription("Play Standard · 4K · 3h 1m · 4.0 GB").assertIsFocused()
        compose.onNodeWithContentDescription("Play Standard · 1080p · 3h 1m · 4.0 GB").performSemanticsAction(SemanticsActions.OnClick)
        // The pick takes the film's place in the run, so a list carries on after it.
        assertEquals("hd" to listOf("hd", "other"), opened)
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }
}
