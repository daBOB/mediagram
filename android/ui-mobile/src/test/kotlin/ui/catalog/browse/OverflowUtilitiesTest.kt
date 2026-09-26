package ui.catalog.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

/**
 * The masthead's departments-only tab row and the four browsing utilities
 * this phase moved into the overflow menu (`ui.BrowseActions`) — Continue
 * and Watchlist are no longer tabs, and Latest/Genres are reachable from
 * anywhere the same way. See `plans/260926-1330-android-editorial-departments-parity/phase-05-phone-departments-and-browse.md`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OverflowUtilitiesTest {
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

    private fun openMenu(label: String) {
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithText(label).performClick()
    }

    @Test fun continueAndWatchlistAreNoLongerMastheadTabs() {
        compose.onNodeWithText("Series").assertIsDisplayed()
        compose.onNodeWithText("Collections").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue").assertDoesNotExist()
        compose.onNodeWithText("Watchlist").assertDoesNotExist()
    }

    @Test fun theOverflowMenuOffersAllFourNewUtilities() {
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithText("My List").assertIsDisplayed()
        compose.onNodeWithText("Continue watching").assertIsDisplayed()
        compose.onNodeWithText("Latest").assertIsDisplayed()
        compose.onNodeWithText("Genres").assertIsDisplayed()
    }

    @Test fun genresOpensTheIndexPageOverAnEmptyLibrary() {
        // The fixture's only titles are episodes with no genres recorded.
        openMenu("Genres")
        compose.onNodeWithText("Nothing in the library has a genre recorded.").assertIsDisplayed()
    }

    @Test fun latestOpensOverTheShelvesFromAnywhere() {
        openMenu("Latest")
        // "Latest" itself names both the bar and the page's own heading;
        // this one line is unique to the page having actually rendered.
        compose.onNodeWithText("Newest arrivals first").assertIsDisplayed()
    }

    @Test fun myListAndContinueWatchingLandOnTheirOwnHiddenTab() {
        openMenu("My List")
        compose.onNodeWithText("Nothing on the list.").assertIsDisplayed()
        // Back to the catalog root, then the other utility.
        compose.onNodeWithText("Series").performClick()
        openMenu("Continue watching")
        compose.onNodeWithText("Nothing started yet.").assertIsDisplayed()
    }
}
