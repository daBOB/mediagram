package ui.settings

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
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import playback.CacheOccupancy
import playback.CacheProvider
import system.CacheBudgetViewModel
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/** Delegates every dispatch to [delegate], recording the thread each one actually ran on. */
private class RecordingDispatcher(
    private val delegate: CoroutineDispatcher,
) : CoroutineDispatcher() {
    @Volatile
    var lastDispatchThread: Thread? = null
        private set

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        delegate.dispatch(context) {
            lastDispatchThread = Thread.currentThread()
            block.run()
        }
    }
}

/**
 * [CacheBudgetViewModel.refresh]'s own dispatcher contract — split out of
 * `CacheBudgetBlockTest` so that file stays under the line limit.
 * `cacheVolumes(context)` walks `StorageManager` and stats every candidate
 * volume, and reading the stored choice is a prefs read; neither belongs
 * on `viewModelScope`'s main dispatcher.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CacheBudgetViewModelDispatchTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    private lateinit var controller: ActivityController<ComponentActivity>

    @Before fun prepare() {
        mockkObject(CacheProvider)
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        coEvery { CacheProvider.occupancy(any(), any()) } returns
            CacheOccupancy(heldBytes = 128, budgetBytes = 1L shl 30, volumeLabel = "Internal storage", fellBack = false, capBytes = 1L shl 30)
    }

    @After fun close() {
        try {
            compose.runOnUiThread {
                if (::controller.isInitialized) controller.close()
                owner.viewModelStore.clear()
            }
        } finally {
            unmockkObject(CacheProvider)
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    @Test fun refreshReadsTheVolumeListAndTheStoredChoiceOffTheGivenDispatcher() {
        val callingThread = Thread.currentThread()
        val recording = RecordingDispatcher(Dispatchers.IO)
        lateinit var model: CacheBudgetViewModel
        compose.runOnUiThread {
            model = CacheBudgetViewModel(ApplicationProvider.getApplicationContext(), recording)
            ViewModelProvider(
                owner.viewModelStore,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(model)!!
                },
            )[CacheBudgetViewModel::class.java]
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    MaterialTheme { CacheBudgetBlock() }
                }
            }
            model.refresh()
        }
        compose.waitForIdle()

        // Both halves matter: a dispatcher that's never actually invoked
        // would leave lastDispatchThread null, which is also "not equal to
        // callingThread" but proves nothing.
        assertNotNull(recording.lastDispatchThread, "the given dispatcher was never actually used")
        assertNotEquals(callingThread, recording.lastDispatchThread)
    }
}
