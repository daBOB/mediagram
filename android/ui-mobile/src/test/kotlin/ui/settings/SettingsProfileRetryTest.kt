package ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import data.DefaultWatchStateRepository
import data.InMemoryCoreStorage
import data.StoredCoreProvider
import data.settings.InMemoryLibrarySettings
import data.settings.InMemoryTelegramSettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
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
import designsystem.InMemoryAppearanceSettings
import setup.AppearanceViewModel
import setup.ProfileSettingsViewModel
import ui.profileSettingsModel
import setup.Libraries
import setup.SettingsViewModel
import system.CacheBudgetViewModel
import system.LanCacheViewModel
import system.SystemViewModel
import testing.FakeCore
import uniffi.mediagram_core.AccountSummary
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A tall window: SettingsPage's own huge title pushes "Application id and
// hash…" past Robolectric's short default window — see
// ui.LibraryFlowTest's own note on the same qualifier.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class SettingsProfileRetryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    private lateinit var model: SettingsViewModel
    private lateinit var controller: ActivityController<ComponentActivity>
    private val core =
        FakeCore(
            accountAnswer = AccountSummary("Viewer", null),
            datacenter = 4,
            profilesFailure = IOException("private-state-path"),
        )
    private var builds = 0

    @Before fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        val settings = InMemoryTelegramSettings()
        val provider =
            StoredCoreProvider(settings, Dispatchers.Main.immediate) {
                builds++
                core
            }
        val library = Libraries(provider, InMemoryLibrarySettings())
        val watch = DefaultWatchStateRepository(provider, Dispatchers.Main.immediate)
        compose.runOnUiThread {
            model = SettingsViewModel(provider, library, InMemoryCoreStorage(), settings, Dispatchers.Main.immediate, watch)
            // SettingsScreen is the hub: it resolves every Settings/System
            // ViewModel through hiltViewModel(), not only this one — this
            // owner has to hand back all five, or a lookup this test never
            // exercises falls to ViewModelProvider's default factory, which
            // cannot construct one with no Hilt entry point to supply its
            // arguments. Storage/System's own facts are irrelevant here,
            // relaxed mocks with an unread state are enough.
            val appearanceModel = AppearanceViewModel(InMemoryAppearanceSettings())
            val cacheModel = mockk<CacheBudgetViewModel>(relaxed = true)
            every { cacheModel.state } returns MutableStateFlow(null)
            every { cacheModel.failure } returns MutableStateFlow(null)
            every { cacheModel.volumes } returns MutableStateFlow(emptyList())
            every { cacheModel.chosenVolumeId } returns MutableStateFlow(null)
            val lanModel = mockk<LanCacheViewModel>(relaxed = true)
            every { lanModel.state } returns MutableStateFlow(null)
            every { lanModel.failure } returns MutableStateFlow(null)
            val systemModel = mockk<SystemViewModel>(relaxed = true)
            every { systemModel.state } returns MutableStateFlow(null)
            every { systemModel.failure } returns MutableStateFlow(null)
            val models = mapOf<Class<out ViewModel>, ViewModel>(
                SettingsViewModel::class.java to model,
                AppearanceViewModel::class.java to appearanceModel,
                ProfileSettingsViewModel::class.java to profileSettingsModel(),
                CacheBudgetViewModel::class.java to cacheModel,
                LanCacheViewModel::class.java to lanModel,
                SystemViewModel::class.java to systemModel,
            )
            val held =
                ViewModelProvider(
                    owner.viewModelStore,
                    object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(models.getValue(modelClass))!!
                    },
                )
            models.keys.forEach { held[it] }
            // The initial row read awaits credentials; installing them starts the
            // real Settings model before opening its retained Activity-owned screen.
            kotlinx.coroutines.runBlocking { provider.supply(1234, "0123456789abcdef0123456789abcdef") }
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    MaterialTheme {
                        SettingsScreen(initial = SettingsSection.TELEGRAM, leavesFromSection = false, tally = emptyList(), onLeave = {})
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @After fun close() {
        try {
            compose.runOnUiThread {
                if (::controller.isInitialized) controller.close()
                owner.viewModelStore.clear()
            }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    @Test fun acceptedIdentityOffersProfileRetryInsteadOfAskingForCredentialsAgain() {
        compose.onNodeWithText("Application id and hash…").performClick()
        compose.onNodeWithText("api_id").performTextReplacement("5678")
        compose.onNodeWithText("api_hash").performTextReplacement("fedcba9876543210fedcba9876543210")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Application identity changed, but profiles could not be loaded. Try again.").assertIsDisplayed()
        compose.onNodeWithText("api_hash").assertDoesNotExist()
        compose.onNodeWithText("private-state-path").assertDoesNotExist()
        assertTrue(model.completions.value.isEmpty())
        core.profilesFailure = null
        compose.onNodeWithText("Try again").performClick()
        // Back on the Telegram section itself, not the reload prompt.
        compose.onNodeWithText("Change library").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        assertEquals(2, builds)
        assertEquals(1, model.completions.value.size)
        assertEquals(2, core.profilesCalls)
    }
}
