package ui.player

import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.media3.common.MediaItem
import io.mockk.clearMocks
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
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import player.setInList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerLifecycleTest {
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

    @Test
    fun configurationChangeDisposesTheScreenAndRetainsPlayback() {
        val oldActivity = controller.get()
        val retainedViewModel = oldActivity.playerViewModel
        compose.runOnUiThread {
            val landscape =
                Configuration(oldActivity.resources.configuration).apply {
                    orientation = Configuration.ORIENTATION_LANDSCAPE
                }
            controller.configurationChange(landscape).visible()
        }
        compose.waitForIdle()

        assertNotSame(oldActivity, controller.get())
        assertTrue(oldActivity.isDestroyed)
        assertEquals(listOf(true), fixture.disposals)
        assertEquals(2, fixture.compositions)
        assertSame(retainedViewModel, controller.get().playerViewModel)
        assertEquals(1, fixture.createdViewModels)
        assertEquals(PlayerUiState.Playing, retainedViewModel.state.value)
        verify(exactly = 0) { fixture.media.stop() }
        verify(exactly = 1) { fixture.media.setMediaItem(any<MediaItem>(), any<Long>()) }
        verify(exactly = 1) { fixture.media.prepare() }
    }

    @Test
    fun backgroundingSavesTheCurrentPositionWithoutStoppingPlayback() {
        clearMocks(fixture.repository, answers = false)
        fixture.positionMs = 73_000
        compose.runOnUiThread { controller.pause().stop() }
        compose.waitForIdle()

        coVerify(exactly = 1) { fixture.repository.setProgress("set-one", 73.0, 600.0) }
        verify(exactly = 0) { fixture.media.stop() }
        assertTrue(fixture.disposals.isEmpty())
        assertFalse(controller.get().isDestroyed)
        compose.runOnUiThread { controller.restart().start().resume() }
        compose.waitForIdle()
        assertEquals(
            PlayerUiState.Playing,
            controller
                .get()
                .playerViewModel.state.value,
        )
    }

    @Test
    fun navigatingAwayStopsOnceEvenWhenTheActivityLaterStops() {
        fixture.positionMs = 91_000
        compose.onNodeWithText("←").performClick()
        compose.onNodeWithText("Library").assertIsDisplayed()

        assertEquals(listOf(false), fixture.disposals)
        verify(exactly = 1) { fixture.media.stop() }
        coVerify { fixture.repository.setProgress("set-one", 91.0, 600.0) }
        clearMocks(fixture.repository, answers = false)
        compose.runOnUiThread { controller.pause().stop() }
        compose.waitForIdle()
        verify(exactly = 1) { fixture.media.stop() }
        coVerify(exactly = 0) { fixture.repository.setProgress(any(), any(), any()) }
    }
}
