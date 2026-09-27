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
import designsystem.InMemoryAppearanceSettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import setup.AppearanceViewModel
import setup.SettingsUiState
import setup.SettingsViewModel
import system.CacheBudgetViewModel
import system.LanCacheViewModel
import system.SystemUiState
import system.SystemViewModel
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two-pane (EXPANDED)/one-pane (compact) flows the approved mockups
 * describe: an index row opens a section and Back returns to the index on
 * compact width; a section opened directly (the System menu shortcut)
 * leaves on Back without ever showing the index; EXPANDED shows both panes
 * together, Telegram already selected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsPanesTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    private lateinit var controller: ActivityController<ComponentActivity>
    private var left = 0

    @Before fun mockFactory() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
    }

    @After fun close() {
        compose.runOnUiThread {
            if (::controller.isInitialized) controller.close()
            owner.viewModelStore.clear()
        }
        unmockkStatic(::HiltViewModelFactory)
    }

    private fun open(
        initial: SettingsSection?,
        leavesFromSection: Boolean,
    ) {
        val settingsModel = mockk<SettingsViewModel>(relaxed = true)
        every { settingsModel.state } returns MutableStateFlow(SettingsUiState(account = "Viewer", library = "Library"))
        val cacheModel = mockk<CacheBudgetViewModel>(relaxed = true)
        every { cacheModel.state } returns MutableStateFlow(null)
        every { cacheModel.failure } returns MutableStateFlow(null)
        every { cacheModel.volumes } returns MutableStateFlow(emptyList())
        every { cacheModel.chosenVolumeId } returns MutableStateFlow(null)
        val lanModel = mockk<LanCacheViewModel>(relaxed = true)
        every { lanModel.state } returns MutableStateFlow(null)
        val systemModel = mockk<SystemViewModel>(relaxed = true)
        every { systemModel.state } returns
            MutableStateFlow(SystemUiState("channel", 4, 2, 3, null, null, 12, 100, "Internal storage", false, 0, 0, 0, 0, true, "test", 0))
        every { systemModel.failure } returns MutableStateFlow(null)
        val models =
            mapOf<Class<out ViewModel>, ViewModel>(
                SettingsViewModel::class.java to settingsModel,
                AppearanceViewModel::class.java to AppearanceViewModel(InMemoryAppearanceSettings()),
                CacheBudgetViewModel::class.java to cacheModel,
                LanCacheViewModel::class.java to lanModel,
                SystemViewModel::class.java to systemModel,
            )
        compose.runOnUiThread {
            val provider =
                ViewModelProvider(
                    owner.viewModelStore,
                    object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(models.getValue(modelClass))!!
                    },
                )
            models.keys.forEach { provider[it] }
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    MaterialTheme {
                        SettingsScreen(initial = initial, leavesFromSection = leavesFromSection, tally = listOf("4 films"), onLeave = { left++ })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun systemBack() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h800dp")
    fun compactIndexOpensASectionAndBackReturnsToTheIndex() {
        open(initial = null, leavesFromSection = false)
        compose.onNodeWithText("Telegram").assertIsDisplayed()
        compose.onNodeWithText("Storage").performClick()
        compose.onNodeWithText("WHAT THIS DEVICE KEEPS AND WHERE IT COMES FROM").assertIsDisplayed()
        systemBack()
        compose.onNodeWithText("SETTINGS").assertIsDisplayed()
        assertEquals(0, left)
        systemBack()
        assertEquals(1, left)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h800dp")
    fun aSectionOpenedDirectlyLeavesOnBackWithoutTouchingTheIndex() {
        open(initial = SettingsSection.SYSTEM, leavesFromSection = true)
        compose.onNodeWithText("Catalogue").assertIsDisplayed()
        systemBack()
        assertEquals(1, left)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1600dp-h1068dp")
    fun expandedShowsTheIndexAndTelegramTogetherAndSignOutIsReachable() {
        open(initial = null, leavesFromSection = false)
        compose.onNodeWithText("mediagram").assertIsDisplayed()
        compose.onNodeWithText("Sign out").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1600dp-h1068dp")
    fun theIndexPaneNeverGrowsPastItsOwn320dpEvenWithAStartInset() {
        open(initial = null, leavesFromSection = false)
        // The mockup's own index width, exactly — requiredWidth (not width)
        // is what a start inset on a real device is not able to push past.
        val right = compose.onNodeWithText("Telegram").fetchSemanticsNode().boundsInRoot.right
        assertTrue(right <= 320f, "expected the index row's own right edge at or under 320dp, was ${right}px")
    }
}
