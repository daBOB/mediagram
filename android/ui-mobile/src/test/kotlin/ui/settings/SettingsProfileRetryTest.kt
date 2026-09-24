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
import data.CoreClient
import data.DefaultWatchStateRepository
import data.InMemoryCoreStorage
import data.StoredCoreProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import settings.InMemoryLibrarySettings
import settings.InMemoryTelegramSettings
import setup.Libraries
import setup.SettingsViewModel
import uniffi.mediagram_core.AccountSummary
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsProfileRetryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    private lateinit var model: SettingsViewModel
    private lateinit var controller: ActivityController<ComponentActivity>
    private val core = mockk<CoreClient>()
    private var builds = 0

    @Before fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        coEvery { core.account() } returns AccountSummary("Viewer", null)
        every { core.dcId() } returns 4
        coEvery { core.profiles() } throws IOException("private-state-path")
        coEvery { core.chosenProfile() } returns null
        val settings = InMemoryTelegramSettings()
        val provider =
            StoredCoreProvider(settings, Dispatchers.Main.immediate) {
                builds++
                core
            }
        val library = Libraries(provider, InMemoryLibrarySettings(), Dispatchers.Main.immediate)
        val watch = DefaultWatchStateRepository(provider, Dispatchers.Main.immediate)
        compose.runOnUiThread {
            model = SettingsViewModel(provider, library, InMemoryCoreStorage(), settings, Dispatchers.Main.immediate, watch)
            ViewModelProvider(
                owner.viewModelStore,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(model)!!
                },
            )[SettingsViewModel::class.java]
            // The initial row read awaits credentials; installing them starts the
            // real Settings model before opening its retained Activity-owned screen.
            kotlinx.coroutines.runBlocking { provider.supply(1234, "0123456789abcdef0123456789abcdef") }
            every { core.close() } returns Unit
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    MaterialTheme { SettingsScreen(cache = {}) }
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
        coEvery { core.profiles() } returns emptyList()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Telegram").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        assertEquals(2, builds)
        assertEquals(1, model.completions.value.size)
        coVerify(exactly = 2) { core.profiles() }
    }
}
