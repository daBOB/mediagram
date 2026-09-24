package ui

import android.os.Bundle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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

    private fun collection() {
        compose.onNode(hasText("Series") and hasClickAction()).performClick()
        compose.onNode(hasText("Example Show") and hasClickAction()).performClick()
        compose.onNode(hasText("Season 1") and hasClickAction()).assertIsDisplayed()
    }

    private fun season() {
        collection()
        compose.onNode(hasText("Season 1") and hasClickAction()).performClick()
        compose.onNode(hasText("First episode", substring = true) and hasClickAction()).assertIsDisplayed()
    }

    private fun title() {
        season()
        compose.onNode(hasText("First episode", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithText("▶ Play").assertIsDisplayed()
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

    @Test fun collectionSeasonTitlePlayerAndBothBackActionsTraverseTheRealBranches() {
        title()
        compose.onNodeWithText("▶ Play").performClick()
        compose.onNodeWithText("←").assertIsDisplayed()
        assertEquals(PlayerUiState.Playing, fixture.player.state.value)
        compose.onNodeWithText("←").performClick()
        compose.onNodeWithText("▶ Play").assertIsDisplayed()
        verify(exactly = 1) { fixture.playback.media.stop() }
        systemBack()
        compose.onNode(hasText("First episode", substring = true) and hasClickAction()).assertIsDisplayed()
        back()
        compose.onNode(hasText("Season 1") and hasClickAction()).assertIsDisplayed()
        back()
        compose.onNodeWithText("Mediagram").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test fun switchingMenuScreensKeepsTheUnderlyingTitleAndBackReturnsDirectlyToIt() {
        title()
        menu("System")
        compose.onNodeWithText("Catalogue").assertIsDisplayed()
        menu("TMDB key…")
        compose.onNodeWithText("Fetch poster artwork").assertIsDisplayed()
        menu("Settings")
        compose.onNodeWithText("Telegram").assertIsDisplayed()
        systemBack()
        compose.onNodeWithText("▶ Play").assertIsDisplayed()
        back()
        compose.onNode(hasText("First episode", substring = true) and hasClickAction()).assertIsDisplayed()
    }

    @Test fun aSavedTitleResolvesAfterTheRecreatedCatalogFinishesLoading() {
        title()
        restoreWhileLoading()
        compose.onNodeWithText("Loading your library…").assertIsDisplayed()
        compose.onNodeWithText("▶ Play").assertDoesNotExist()
        compose.runOnUiThread { fixture.catalogReady.complete(Unit) }
        compose.onNodeWithText("▶ Play").assertIsDisplayed()
        back()
        compose.onNode(hasText("First episode", substring = true) and hasClickAction()).assertIsDisplayed()
        back()
        compose.onNode(hasText("Season 1") and hasClickAction()).assertIsDisplayed()
    }

    @Test fun aSavedSeasonResolvesAfterLoadingAndBackUncoversItsCollection() {
        season()
        restoreWhileLoading()
        compose.onNodeWithText("Loading your library…").assertIsDisplayed()
        compose.runOnUiThread { fixture.catalogReady.complete(Unit) }
        compose.onNode(hasText("First episode", substring = true) and hasClickAction()).assertIsDisplayed()
        back()
        compose.onNode(hasText("Season 1") and hasClickAction()).assertIsDisplayed()
    }

    @Test fun updatingFromAnOverlayReturnsToTheCatalogAndClearsTheDeepStack() {
        title()
        menu("System")
        val before = fixture.refreshes
        menu("Update library")
        compose.onNodeWithText("Mediagram").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        assertEquals(before + 1, fixture.refreshes)
        collection()
        // A stale season must not turn this new collection visit into its episode list.
        compose.onNodeWithText("Second episode").assertDoesNotExist()
    }

    @Test fun playingFromAHandBuiltListReturnsToThatList() {
        compose.onNode(hasText("Collections") and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasText("Favourites") and hasClickAction()).performClick()
        compose.onNode(hasText("First episode", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithText("←").assertIsDisplayed()
        systemBack()
        compose.onNodeWithText("Rename").assertIsDisplayed()
        compose.onNode(hasText("First episode", substring = true) and hasClickAction()).assertIsDisplayed()
        back()
        compose.onNodeWithText("Mediagram").assertIsDisplayed()
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
