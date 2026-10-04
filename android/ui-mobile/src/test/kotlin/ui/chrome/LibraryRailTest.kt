package ui.chrome

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.LibraryFlowFixture
import ui.LibraryFlowTestActivity
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The rail at a tablet's own EXPANDED width: every row the web's rail-nav
 * carries, this fixture's own counts, the tally, and that every row still
 * lands where the old overflow's matching item used to (`OverflowUtilitiesTest`,
 * `LibraryFlowTest`) — now reached beside the shelves rather than behind a ⋮.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1164dp-h777dp")
class LibraryRailTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: LibraryFlowFixture
    private lateinit var controller: ActivityController<LibraryFlowTestActivity>

    @Before fun open() {
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

    @Test fun everyRowItsCountsAndTheTallyAreOnScreenAtOnce() {
        val labels = listOf("My List", "Continue watching", "Latest", "Genres", "Stats", "Settings", "System")
        for (label in labels) {
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        val tops = labels.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops, "the rail lists its rows in the web's order")
        // My List and Continue watching are both empty in this fixture, and
        // so is Documentaries — the one department pill that still prints
        // its count at zero rather than dropping out — so all three read "0".
        compose.onAllNodesWithText("0").assertCountEquals(3)
        // The fixture's one shelf, "Series", holds one show — the tally
        // prints it uppercase, the same as SettingsIndex's own tally does.
        compose.onNodeWithText("ONE SHOW").assertIsDisplayed()
    }

    @Test fun latestLandsOnTheLatestPageWithTheRailStillBesideIt() {
        compose.onNodeWithText("Latest").performClick()
        compose.onNodeWithText("Newest arrivals first").assertIsDisplayed()
        // Still beside it, the same as before the tap — a pushed frame keeps the rail on EXPANDED.
        compose.onNodeWithText("Genres").assertIsDisplayed()
    }

    @Test fun statsLandsOnTheStatsPageWithTheRailStillBesideIt() {
        compose.onNodeWithText("Stats").performClick()
        compose.onNodeWithText("Nothing watched yet.").assertIsDisplayed()
        compose.onNodeWithText("Genres").assertIsDisplayed()
    }

    @Test fun genresLandsOnTheGenresIndex() {
        compose.onNodeWithText("Genres").performClick()
        compose.onAllNodesWithText("Drama").onFirst().assertIsDisplayed()
    }

    @Test fun systemLandsOnTheSystemScreen() {
        compose.onNodeWithText("System").performClick()
        compose.onNodeWithText("Catalogue").assertIsDisplayed()
    }

    @Test fun settingsLandsOnTheSettingsScreen() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Telegram").assertIsDisplayed()
    }

    @Test fun myListAndContinueWatchingLandOnTheSameHiddenTabTheOldMenuDid() {
        compose.onNodeWithText("My List").performClick()
        compose.onNodeWithText("Nothing on your list.").assertIsDisplayed()
        compose.onNodeWithContentDescription("mediagram — home").performClick()
        compose.onNodeWithText("Continue watching").performClick()
        compose.onNodeWithText("Nothing started yet.").assertIsDisplayed()
    }

    @Test fun backOnTheReopenedPickerStaysAsTheViewerRatherThanLeavingTheApp() {
        compose.onNodeWithContentDescription("Who's watching: Viewer").performClick()
        compose.onNodeWithText("Stay as I am").assertIsDisplayed()

        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }

        compose.onAllNodesWithText("Who's watching?").assertCountEquals(0)
        compose.onNodeWithText("Genres").assertIsDisplayed()
        assertFalse(controller.get().isFinishing, "Back left the app")
    }

    @Test fun theWordmarkReturnsHomeFromWhereverTheRailOpenedSomethingElse() {
        compose.onNodeWithText("Latest").performClick()
        compose.onNodeWithText("Newest arrivals first").assertIsDisplayed()
        compose.onNodeWithContentDescription("mediagram — home").performClick()
        compose.onNodeWithText("Newest arrivals first").assertDoesNotExist()
    }

    @Test fun theAvatarReopensTheProfileChooser() {
        compose.onNodeWithContentDescription("Who's watching: Viewer").performClick()
        compose.onNodeWithText("Who's watching?").assertIsDisplayed()
    }

    @Test fun theBarsSearchIconOpensSearchFromTheRoot() {
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("Search titles and summaries").assertIsDisplayed()
    }
}
