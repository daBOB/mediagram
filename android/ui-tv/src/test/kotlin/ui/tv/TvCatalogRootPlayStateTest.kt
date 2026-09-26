package ui.tv

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import catalog.CatalogUiState
import catalog.shelvesOf
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.catalog.TvScreenStateTest
import ui.tv.catalog.films
import ui.tv.profile.TvChosenProfile
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [TvCatalogRoot]'s own `onPlay` — the cover story's "Watch now"
 * (`web/home-cover.js:137`) plays straight away rather than opening the
 * title page every other plate on this screen leads to, which is what
 * `onPlay`'s own default (`= onOpenTitle`) would otherwise do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvCatalogRootPlayStateTest : TvScreenStateTest() {
    @Test
    fun watchNowPlaysStraightAwayRatherThanOpeningTheTitlePage() {
        val featured = films(4).map { it.copy(backdropPath = "/bd${it.setId}", posterPath = "/p${it.setId}") }
        var opened: String? = null
        var played: String? = null
        show {
            TvCatalogRoot(
                state = CatalogUiState.Ready(shelvesOf(featured), watch = WatchSnapshot.Empty),
                profile = TvChosenProfile(name = "Ada", onChoose = {}),
                fetching = false,
                restoreKey = null,
                onOpenTitle = { opened = it },
                onOpenCollection = {},
                onOpenList = {},
                onCreateList = {},
                onTabChanged = {},
                onOpenSearch = {},
                onOpenMenu = {},
                onEntryRestored = {},
                onFinish = {},
                onPlay = { played = it },
            )
        }

        // A pager, possibly with a neighbour composed too: the first is
        // the one on screen, the same defensive finder the cover story's
        // own existence test already uses.
        compose.onAllNodesWithText("▶ Watch now").onFirst().performSemanticsAction(SemanticsActions.OnClick)

        assertNull(opened)
        assertEquals(true, played?.startsWith("film-"))
    }
}
