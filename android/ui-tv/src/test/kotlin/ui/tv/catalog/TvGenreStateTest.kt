package ui.tv.catalog

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import catalog.CatalogUiState
import catalog.GenreShelf
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A genre page with nothing to open still gives the remote somewhere to rest, in the phone's words. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvGenreStateTest : TvScreenStateTest() {
    @Test
    fun aGenreWithNothingTaggedSaysSoAndHoldsTheRemote() {
        show { TvGenreWall("Western", GenreShelf(emptyList(), emptyList()), WatchSnapshot.Empty, {}, {}, null) }

        compose.onNodeWithText("Nothing in the library is tagged with this genre.").assertIsFocused()
    }

    @Test
    fun aGenreBeforeTheLibraryHasLoadedSaysSoAndHoldsTheRemote() {
        show { TvGenre("Western", CatalogUiState.Loading, WatchSnapshot.Empty, {}, {}, null) }

        compose.onNodeWithText("Loading your library…").assertIsFocused()
    }
}
