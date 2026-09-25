package ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import kotlinx.coroutines.runBlocking
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
import playback.PlainCacheVolumeSettings
import system.CacheBudgetViewModel
import kotlin.test.assertEquals

private val WORKING_OCCUPANCY =
    CacheOccupancy(heldBytes = 0, budgetBytes = 1L shl 30, volumeLabel = "Internal storage", fellBack = false, capBytes = 8L shl 30)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CacheVolumeBlockTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    private lateinit var model: CacheBudgetViewModel
    private lateinit var controller: ActivityController<ComponentActivity>

    @Before fun prepare() {
        mockkObject(CacheProvider)
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        coEvery { CacheProvider.occupancy(any(), any()) } returns WORKING_OCCUPANCY
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

    private fun open() {
        compose.runOnUiThread {
            model = CacheBudgetViewModel(ApplicationProvider.getApplicationContext())
            ViewModelProvider(
                owner.viewModelStore,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(model)!!
                },
            )[CacheBudgetViewModel::class.java]
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    MaterialTheme { CacheVolumeBlock() }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun theRecordedVolumeIsSelectedAndTheRestartSentenceIsShown() {
        // Robolectric reports no removable storage, so "Internal storage" is
        // the only row — the same shape as the Redmi in the field.
        open()

        compose.onNodeWithText("Where").assertIsDisplayed()
        compose.onNodeWithText("Internal storage", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Takes effect the next time the app starts. Titles already held will be fetched again.")
            .assertIsDisplayed()
    }

    @Test fun choosingAVolumePersistsTheChoice() {
        open()
        compose.onNodeWithText("Internal storage", substring = true).performClick()
        compose.waitForIdle()

        val persisted = runBlocking { PlainCacheVolumeSettings(ApplicationProvider.getApplicationContext()).read() }
        assertEquals("internal", persisted)
        assertEquals("internal", model.chosenVolumeId.value)
    }
}
