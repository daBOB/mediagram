package ui.player

import android.content.res.Configuration
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import io.mockk.mockk
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

/**
 * [PlayerLifecycle] against a mocked ViewModel: the effects under test are
 * exactly the two calls it makes — stop, save — never anything
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
