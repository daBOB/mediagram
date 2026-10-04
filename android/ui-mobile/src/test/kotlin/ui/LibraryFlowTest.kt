package ui

import android.os.Bundle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import player.PlayerUiState
import kotlin.test.assertEquals

// A tall window: the show's own page is a LazyColumn (its Episodes tab can
// run to a few hundred rows), and Robolectric's default window is short
// enough that even its first episode row sits past the prefetch window and
// is never composed — see ui.catalog.title.SeriesPageTest's own note.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class LibraryFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: LibraryFlowFixture
    private lateinit var controller: ActivityController<LibraryFlowTestActivity>

    @Before fun open() {
        // The ViewModels are already held in the real store. Only Hilt's
        // generated-Activity factory lookup needs replacing in Robolectric.
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = LibraryFlowFixture()
            LibraryFlowTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(LibraryFlowTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
    }

    @After fun close() {
        try {
            compose.runOnUiThread {
                if (::controller.isInitialized) controller.close()
                if (::fixture.isInitialized) fixture.close()
            }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    /** Opens the show's own page — the Episodes tab is its default, Season 1 shown first. */
    private fun collection() {
        compose.onNode(hasText("Series") and hasClickAction()).performClick()
        compose.onNode(hasText("Example Show") and hasClickAction()).performClick()
        compose.onNode(hasText("First episode", substring = true) and hasClickAction()).assertIsDisplayed()
    }

    /**
     * Picks Season 2 from the show page's own season picker — replaces the
     * old season-poster wall's own hop. `hasClickAction()` is what tells the
     * picker's own button apart from the episode list's plain "Season 1"
     * heading right below it, which names the same season but opens nothing.
     */
    private fun season() {
        collection()
        compose.onNode(hasText("Season 1", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithText("Season 2 · one episode").performClick()
        compose.onNode(hasText("Second episode", substring = true) and hasClickAction()).assertIsDisplayed()
    }

    /**
     * Opens a film's own title page from the Movies tab — a poster tap opens
     * details, where an episode row plays instead (see
     * [anEpisodeRowInTheSeasonListPlaysRatherThanOpeningATitlePage]). The
     * department front page can carry the one film under more than one
     * heading; either leads to the same set id.
     */
    private fun title() {
        compose.onNode(hasText("Movies") and hasClickAction()).performClick()
        compose.onAllNodes(hasText("Example Film") and hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("▶ Play").assertIsDisplayed()
    }

    private fun assertAtTheShelves() {
        compose.onNodeWithText("mediagram").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    private fun menu(label: String) {
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithText(label).performClick()
    }

    private fun back() = compose.onNodeWithContentDescription("Back").performClick()

    private fun systemBack() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test fun titlePlayerAndBothBackActionsTraverseTheRealBranches() {
        title()
        compose.onNodeWithText("▶ Play").performClick()
        compose.onNodeWithText("←").assertIsDisplayed()
        assertEquals(PlayerUiState.Playing, fixture.player.state.value)
        compose.onNodeWithText("←").performClick()
        compose.onNodeWithText("▶ Play").assertIsDisplayed()
        verify(exactly = 1) { fixture.playback.media.stop() }
        systemBack()
        assertAtTheShelves()
    }

    /** Every episode row plays straight into the player — `course-view.js`'s `lessonRow`, which never opens a title page first. */
    @Test fun anEpisodeRowInTheSeasonListPlaysRatherThanOpeningATitlePage() {
        season()
        compose.onNode(hasText("Second episode", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithText("←").assertIsDisplayed()
        assertEquals(PlayerUiState.Playing, fixture.player.state.value)
    }

    /**
     * A second title pushed straight over the first, from its own Similar
     * row, opens fresh rather than inheriting the first one's tab — and back
     * finds the first as it was left ([ui.LibraryPositions.frameKey]).
     */
    @Test fun aSimilarTitleOpensFreshAndBackRestoresTheFirstTitlesOwnTab() {
        title()
        compose.onNodeWithText("Similar").performScrollTo().performClick()
        compose.onNode(hasText("Second Feature", substring = true) and hasClickAction()).performClick()

        compose.onNode(hasText("Overview") and isSelected()).assertExists()

        back()

        compose.onNode(hasText("Similar") and isSelected()).assertExists()
        compose.onNode(hasText("Second Feature", substring = true) and hasClickAction()).assertExists()
    }

    // Settings/System render outside LibraryScaffold (the approved mockups
    // have no bar), so there is no overflow menu inside either of them to
    // switch onward from — only TMDB key still keeps the bar.
    @Test fun switchingMenuScreensKeepsTheUnderlyingTitleAndBackReturnsDirectlyToIt() {
        title()
        menu("TMDB key…")
        compose.onNodeWithText("Fetch poster artwork").assertIsDisplayed()
        menu("Settings")
        // Compact width opens on the index; "Telegram" is one of its rows.
        compose.onNodeWithText("Telegram").assertIsDisplayed()
        systemBack()
        compose.onNodeWithText("▶ Play").assertIsDisplayed()
        back()
        assertAtTheShelves()
    }

    @Test fun openingSystemDirectlyLeavesBackToTheUnderlyingTitle() {
        title()
        menu("System")
        compose.onNodeWithText("Catalogue").assertIsDisplayed()
        systemBack()
        compose.onNodeWithText("▶ Play").assertIsDisplayed()
    }

    @Test fun aSavedTitleResolvesAfterTheRecreatedCatalogFinishesLoading() {
        title()
        restoreWhileLoading()
        compose.onNodeWithText("Loading your library…").assertIsDisplayed()
        compose.onNodeWithText("▶ Play").assertDoesNotExist()
        compose.runOnUiThread { fixture.catalogReady.complete(Unit) }
        compose.onNodeWithText("▶ Play").assertIsDisplayed()
        back()
        assertAtTheShelves()
    }

    /**
     * Season 2 — chosen by [season] rather than the default — survives a
     * killed-and-recreated process the same way the collection's own key
     * already did: both ride the same saved frame payload
     * ([ui.LibraryPositions.setCollectionSeason]).
     */
    @Test fun aSavedSeasonResolvesAfterLoadingAndBackUncoversItsCollection() {
        season()
        restoreWhileLoading()
        compose.onNodeWithText("Loading your library…").assertIsDisplayed()
        compose.runOnUiThread { fixture.catalogReady.complete(Unit) }
        compose.onNode(hasText("Second episode", substring = true) and hasClickAction()).assertIsDisplayed()
        back()
        compose.onNodeWithText("mediagram").assertIsDisplayed()
    }

    @Test fun updatingFromAnOverlayReturnsToTheCatalogAndClearsTheDeepStack() {
        title()
        // TMDB key still keeps the bar (System/Settings no longer do), so
        // it is the overlay this test opens the update from.
        menu("TMDB key…")
        val before = fixture.refreshes
        menu("Update library")
        compose.onNodeWithText("mediagram").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        assertEquals(before + 1, fixture.refreshes)
        collection()
        // A stale season must not turn this new collection visit into its episode list.
        compose.onNodeWithText("Second episode").assertDoesNotExist()
    }

    @Test fun playingFromAHandBuiltListReturnsToThatList() {
        compose.onNode(hasText("Collections") and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasText("FAVOURITES") and hasClickAction()).performClick()
        compose.onNode(hasText("First episode", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithText("←").assertIsDisplayed()
        systemBack()
        compose.onNodeWithText("Rename").assertIsDisplayed()
        compose.onNode(hasText("First episode", substring = true) and hasClickAction()).assertIsDisplayed()
        back()
        compose.onNodeWithText("mediagram").assertIsDisplayed()
    }

    private fun restoreWhileLoading() {
        compose.runOnUiThread {
            val saved = Bundle()
            controller
                .saveInstanceState(saved)
                .pause()
                .stop()
                .destroy()
            fixture.close()
            fixture = LibraryFlowFixture(loading = true)
            LibraryFlowTestActivity.fixture = fixture
            controller =
                Robolectric
                    .buildActivity(LibraryFlowTestActivity::class.java)
                    .create(saved)
                    .start()
                    .resume()
                    .visible()
        }
        compose.waitForIdle()
    }
}
