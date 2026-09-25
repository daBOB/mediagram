package ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import playback.InMemoryLanCacheSettings
import playback.LanCacheTokenStatus
import playback.LanChunkProtocol
import playback.LanPutResult
import playback.LanServer
import playback.LanServerSource
import playback.LanServerStatus
import settings.InMemoryLanCacheTokenSettings
import system.CacheBudgetViewModel
import system.LanCacheViewModel

private val WORKING_OCCUPANCY =
    CacheOccupancy(heldBytes = 0, budgetBytes = 1L shl 30, volumeLabel = "Internal storage", fellBack = false, capBytes = 8L shl 30)

/** No discovery, no server, nothing pinged — [CacheSection]'s own test cares only that the block mounts. */
private class NoopLocator : LanServerSource {
    override val server: StateFlow<LanServer?> = MutableStateFlow(null)
    override val searching: StateFlow<Boolean> = MutableStateFlow(false)

    override fun discover() = Unit
}

private class NoopLanChunkProtocol : LanChunkProtocol {
    override suspend fun get(
        baseUrl: String,
        setId: String,
        index: Long,
        expectedLength: Int,
    ): ByteArray? = null

    override suspend fun put(
        baseUrl: String,
        token: String,
        setId: String,
        index: Long,
        total: Long,
        body: ByteArray,
    ) = LanPutResult.Stored

    override suspend fun verify(baseUrl: String): Boolean = false

    override suspend fun status(baseUrl: String): LanServerStatus? = null
}

/**
 * [CacheSection] hosts both cache blocks over one [CacheBudgetViewModel]
 * and is the only one of the three that triggers its read — see
 * `CacheBudgetBlock.kt` and `CacheVolumeBlock.kt`'s own comments on why
 * they no longer do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CacheSectionTest {
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

    @Test fun mountingReadsTheCacheExactlyOnceForBothBlocks() {
        compose.runOnUiThread {
            val cacheBudgetModel = CacheBudgetViewModel(ApplicationProvider.getApplicationContext(), Dispatchers.Main.immediate)
            val lanCacheModel =
                LanCacheViewModel(
                    ApplicationProvider.getApplicationContext(),
                    InMemoryLanCacheSettings(),
                    InMemoryLanCacheTokenSettings(),
                    LanCacheTokenStatus(),
                    NoopLocator(),
                    NoopLanChunkProtocol(),
                )
            val factory =
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        when (modelClass) {
                            CacheBudgetViewModel::class.java -> cacheBudgetModel as T
                            LanCacheViewModel::class.java -> lanCacheModel as T
                            else -> error("unexpected view model class in this test: $modelClass")
                        }
                }
            ViewModelProvider(owner.viewModelStore, factory)[CacheBudgetViewModel::class.java]
            ViewModelProvider(owner.viewModelStore, factory)[LanCacheViewModel::class.java]
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    MaterialTheme { CacheSection() }
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Held").assertIsDisplayed()
        compose.onNodeWithText("Where").assertIsDisplayed()
        coVerify(exactly = 1) { CacheProvider.occupancy(any(), any()) }
    }
}
