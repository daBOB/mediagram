package ui.catalog.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
 * The departments bar's own pill row and the chrome's own reach for what
 * used to be overflow-only utilities — My List, Continue watching, Latest,
 * Genres are now icon buttons in the compact header's row 1 (or rows in
 * [ui.chrome.LibraryRail] on EXPANDED), each with its own name for a content
 * description rather than a `DropdownMenuItem`'s visible text. The ⋮ this
 * width still carries holds only the three actions with no icon of their
 * own — Update library, TMDB key…, Start over.
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

    private fun tapIcon(description: String) {
        compose.onNodeWithContentDescription(description).performScrollTo().performClick()
    }

    @Test fun continueAndWatchlistAreNoLongerDepartmentPills() {
        compose.onNodeWithText("Series").assertIsDisplayed()
        compose.onNodeWithText("Collections").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue").assertDoesNotExist()
        compose.onNodeWithText("Watchlist").assertDoesNotExist()
    }

    @Test fun theTrimmedMenuOffersOnlyTheAndroidOnlyActions() {
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithText("Update library").assertIsDisplayed()
        compose.onNodeWithText("TMDB key…").assertIsDisplayed()
        compose.onNodeWithText("Start over").assertIsDisplayed()
        compose.onNodeWithText("My List").assertDoesNotExist()
        compose.onNodeWithText("Continue watching").assertDoesNotExist()
        compose.onNodeWithText("Latest").assertDoesNotExist()
        compose.onNodeWithText("Genres").assertDoesNotExist()
    }

    @Test fun everyFormerOverflowUtilityIsNowItsOwnIconInTheHeader() {
        tapIcon("My List")
        compose.onNodeWithText("Nothing on the list.").assertIsDisplayed()
        compose.onNodeWithText("Series").performClick()
        tapIcon("Continue watching")
        compose.onNodeWithText("Nothing started yet.").assertIsDisplayed()
    }

    @Test fun genresOpensTheIndexPage() {
        // The fixture's two films share the one genre it records.
        tapIcon("Genres")
        compose.onAllNodesWithText("Drama").onFirst().assertIsDisplayed()
    }

    @Test fun latestOpensOverTheShelvesFromAnywhere() {
        tapIcon("Latest")
        // "Latest" itself names both the icon and the page's own heading;
        // this one line is unique to the page having actually rendered.
        compose.onNodeWithText("Newest arrivals first").assertIsDisplayed()
    }

    @Test fun statsOpensFromItsOwnHeaderIcon() {
        tapIcon("Stats")
        compose.onNodeWithText("Nothing watched yet.").assertIsDisplayed()
    }
}
