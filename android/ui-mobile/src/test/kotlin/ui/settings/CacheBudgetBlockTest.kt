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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
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
import java.io.IOException
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CacheBudgetBlockTest {
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
                    MaterialTheme { CacheBudgetBlock() }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun firstReadFailureStaysVisibleAndRetryRecoversTheRealBudget() {
        coEvery { CacheProvider.occupancy(any(), any()) } throws IOException("private-cache-path")
        open()
        compose.onNodeWithText("Cache").assertIsDisplayed()
        compose.onNodeWithText("Could not read the cache. Try again.").assertIsDisplayed()
        compose.onNodeWithText("private-cache-path").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Could not read the cache. Try again.").assertIsDisplayed()
        coEvery { CacheProvider.occupancy(any(), any()) } returns CacheOccupancy(128, 1L shl 30)
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Held").assertIsDisplayed()
        compose.onNodeWithText("Could not read the cache. Try again.").assertDoesNotExist()
        assertEquals(CacheOccupancy(128, 1L shl 30), model.state.value)
        coVerify(exactly = 3) { CacheProvider.occupancy(any(), any()) }
    }

    @Test fun failedResizeKeepsTheLastReadAndRetryReadsAnAlreadyPersistedChange() {
        val prior = CacheOccupancy(128, 1L shl 30)
        coEvery { CacheProvider.occupancy(any(), any()) } returns prior
        coEvery { CacheProvider.setBudget(any(), any()) } throws IOException("private-keystore-path")
        open()
        compose.onNodeWithText("2.0 GB").performClick()
        compose.onNodeWithText("Could not confirm the cache allowance. Try again.").assertIsDisplayed()
        compose.onNodeWithText("Held").assertIsDisplayed()
        assertEquals(prior, model.state.value)
        // Persistence can succeed before a later cache operation fails. A retry reads
        // that committed value, rather than pretending that the old budget still holds.
        coEvery { CacheProvider.occupancy(any(), any()) } returns CacheOccupancy(256, 2L shl 30)
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Could not confirm the cache allowance. Try again.").assertDoesNotExist()
        assertEquals(CacheOccupancy(256, 2L shl 30), model.state.value)
        coVerify(exactly = 1) { CacheProvider.setBudget(any(), 2L shl 30) }
    }

    @Test fun aFailedConfirmationReadDoesNotInventABudgetAndLaterChoiceRecovers() {
        val prior = CacheOccupancy(128, 1L shl 30)
        coEvery { CacheProvider.occupancy(any(), any()) } returns prior
        coEvery { CacheProvider.setBudget(any(), any()) } returns Unit
        open()
        coEvery { CacheProvider.occupancy(any(), any()) } throws IOException("private-cache-path")
        compose.onNodeWithText("2.0 GB").performClick()
        compose.onNodeWithText("Could not confirm the cache allowance. Try again.").assertIsDisplayed()
        assertEquals(prior, model.state.value)
        coEvery { CacheProvider.occupancy(any(), any()) } returns CacheOccupancy(256, 4L shl 30)
        compose.onNodeWithText("4.0 GB").performClick()
        compose.onNodeWithText("Could not confirm the cache allowance. Try again.").assertDoesNotExist()
        assertEquals(CacheOccupancy(256, 4L shl 30), model.state.value)
    }

    @Test fun cancelledChoiceKeepsTheLastReadWithoutAnErrorAndAllowsTheNextChoice() {
        val prior = CacheOccupancy(128, 1L shl 30)
        coEvery { CacheProvider.occupancy(any(), any()) } returns prior
        coEvery { CacheProvider.setBudget(any(), any()) } throws CancellationException("leaving")
        open()
        compose.onNodeWithText("2.0 GB").performClick()
        compose.onNodeWithText("Could not confirm the cache allowance. Try again.").assertDoesNotExist()
        assertEquals(prior, model.state.value)
        coEvery { CacheProvider.setBudget(any(), any()) } returns Unit
        coEvery { CacheProvider.occupancy(any(), any()) } returns CacheOccupancy(256, 2L shl 30)
        compose.onNodeWithText("2.0 GB").performClick()
        assertEquals(CacheOccupancy(256, 2L shl 30), model.state.value)
    }
}
