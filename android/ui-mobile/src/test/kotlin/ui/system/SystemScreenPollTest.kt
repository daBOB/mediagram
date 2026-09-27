package ui.system

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import system.SystemUiState
import system.SystemViewModel

/**
 * System polls while its page stays on screen, the same way the web's own
 * status panel does (`status-lines.js:14`, 2s) rather than reading once per
 * visit — the plan's own answered question 1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemScreenPollTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    private lateinit var controller: ActivityController<ComponentActivity>
    private val viewModel = mockk<SystemViewModel>(relaxed = true)

    @Before fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        every { viewModel.state } returns MutableStateFlow(facts())
        every { viewModel.failure } returns MutableStateFlow(null)
        compose.runOnUiThread {
            ViewModelProvider(
                owner.viewModelStore,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(viewModel)!!
                },
            )[SystemViewModel::class.java]
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    MaterialTheme { SystemScreen(expanded = false) }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    @After fun close() {
        try {
            compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    @Test fun rereadsEveryTwoSecondsWhileTheScreenStaysComposed() {
        compose.mainClock.advanceTimeBy(2_100)
        verify(exactly = 1) { viewModel.retry() }
        compose.mainClock.advanceTimeBy(2_000)
        verify(exactly = 2) { viewModel.retry() }
    }

    @Test fun doesNotReadBeforeTheFirstIntervalHasPassed() {
        compose.mainClock.advanceTimeBy(1_000)
        verify(exactly = 0) { viewModel.retry() }
    }

    private fun facts() = SystemUiState("channel", 4, 2, 3, null, null, 12, 100, "Internal storage", false, 0, 0, 0, 0, true, "test", 0)
}
