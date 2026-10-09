package ui.common.player

import android.content.res.Configuration
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import io.mockk.mockk
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
import player.PlayerViewModel
import player.screenShown

/**
 * [PlayerLifecycle] against a mocked ViewModel: the effects under test are
 * exactly the calls it makes — stop, save, screenShown — never anything
 * about what the player is actually doing, so nothing here needs a real
 * one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerLifecycleTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var viewModel: PlayerViewModel
    private lateinit var controller: ActivityController<PlayerLifecycleTestActivity>

    @Before
    fun openScreen() {
        // `screenShown` is an extension, so it is checked where it is defined.
        mockkStatic(PLAYER_TOGETHER)
        viewModel = mockk(relaxed = true)
        compose.runOnUiThread {
            PlayerLifecycleTestActivity.viewModel = viewModel
            PlayerLifecycleTestActivity.hide = {}
            controller = Robolectric.buildActivity(PlayerLifecycleTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
    }

    @After
    fun closeScreen() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
        unmockkStatic(PLAYER_TOGETHER)
    }

    private companion object {
        const val PLAYER_TOGETHER = "player.PlayerViewModelTogetherKt"
    }

    @Test
    fun configurationChangeDisposesTheScreenWithoutStoppingPlayback() {
        val oldActivity = controller.get()
        compose.runOnUiThread {
            val landscape =
                Configuration(oldActivity.resources.configuration).apply {
                    orientation = Configuration.ORIENTATION_LANDSCAPE
                }
            controller.configurationChange(landscape).visible()
        }
        compose.waitForIdle()
        verify(exactly = 0) { viewModel.stop() }
    }

    @Test
    fun backgroundingSavesTheCurrentPositionWithoutStoppingPlayback() {
        compose.runOnUiThread { controller.pause().stop() }
        compose.waitForIdle()
        verify(exactly = 1) { viewModel.save() }
        verify(exactly = 0) { viewModel.stop() }
        compose.runOnUiThread { controller.restart().start().resume() }
        compose.waitForIdle()
    }

    /** Out of a watch-together session, the player listens for one only while its screen is started. */
    @Test
    fun stoppingAndStartingTheScreenSaySo() {
        verify(exactly = 1) { viewModel.screenShown(true) }

        compose.runOnUiThread { controller.pause().stop() }
        compose.waitForIdle()
        verify(exactly = 1) { viewModel.screenShown(false) }

        compose.runOnUiThread { controller.restart().start().resume() }
        compose.waitForIdle()
        verify(exactly = 2) { viewModel.screenShown(true) }
    }

    @Test
    fun navigatingAwayStopsOnceEvenWhenTheActivityLaterStops() {
        compose.runOnUiThread { PlayerLifecycleTestActivity.hide() }
        compose.waitForIdle()
        verify(exactly = 1) { viewModel.stop() }
        compose.runOnUiThread { controller.pause().stop() }
        compose.waitForIdle()
        verify(exactly = 1) { viewModel.stop() }
        verify(exactly = 0) { viewModel.save() }
    }
}
