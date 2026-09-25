package ui.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.mockk.coEvery
import io.mockk.coVerify
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
import player.setInList

/**
 * The material-rendered notice PlayerScreen shows over the playing screen
 * when a list write is refused — [PlayerLifecycleTest] covers the ViewModel
 * calls PlayerLifecycle itself makes; this covers what the screen built
 * around it does with a failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerNoticesTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: PlayerLifecycleFixture
    private lateinit var controller: ActivityController<PlayerTestActivity>

    @Before
    fun openPlayer() {
        compose.runOnUiThread {
            fixture = PlayerLifecycleFixture()
            PlayerTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(PlayerTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
        assertEquals(
            PlayerUiState.Playing,
            controller
                .get()
                .playerViewModel.state.value,
        )
    }

    @After
    fun closePlayer() {
        compose.runOnUiThread {
            if (::controller.isInitialized) controller.close()
            if (::fixture.isInitialized) fixture.close()
        }
    }

    @Test
    fun aRefusedListWriteShowsARecoverableNoticeOverThePlayingScreen() {
        coEvery { fixture.repository.setInList(any(), any(), any()) } returns false
        compose.runOnUiThread { controller.get().playerViewModel.setInList("list-one", true) }
        compose.onNodeWithText("Could not confirm the list update. Check it and try again.").assertIsDisplayed()
        assertEquals(
            PlayerUiState.Playing,
            controller
                .get()
                .playerViewModel.state.value,
        )
        verify(exactly = 0) { fixture.media.stop() }
        compose.onNodeWithText("Dismiss").performClick()
        compose.onNodeWithText("Could not confirm the list update. Check it and try again.").assertDoesNotExist()
    }

    @Test
    fun aRefusedListCreationShowsTheNoticeInsideTheOpenDialog() {
        coEvery { fixture.repository.createList(any()) } returns null
        compose.onNodeWithText("Add to list").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Favourites")
        compose.onNodeWithText("New list").performClick()
        compose
            .onNode(
                hasText("Could not confirm the list update. Check it and try again.") and hasAnyAncestor(isDialog()),
            ).assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        assertEquals(
            PlayerUiState.Playing,
            controller
                .get()
                .playerViewModel.state.value,
        )
        verify(exactly = 0) { fixture.media.stop() }
        coVerify(exactly = 0) { fixture.repository.setInList(any(), any(), any()) }
    }
}
