package ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ProfileViewModel
import data.WatchSync
import designsystem.InMemoryAppearanceSettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import model.Profile
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import setup.AppearanceViewModel
import setup.ProfileSettingsViewModel
import setup.SettingsUiState
import setup.SettingsViewModel
import system.CacheBudgetViewModel
import system.LanCacheViewModel
import system.SystemViewModel
import testing.FakeCore
import testing.WatchStateFixture
import ui.common.settings.SettingsSection
import ui.profile.PinFieldTag
import ui.profile.ProfileGate

/**
 * Settings drawn inside the real [ProfileGate], as the app draws it, over the
 * real picker and Manage view models and a fake core that keeps the
 * household's rules — so what the gate does to Manage on a first composition
 * or a recreated activity is what a test sees.
 */
abstract class ProfilesSettingsHarness {
    @get:Rule val compose = createEmptyComposeRule()
    private val store = ViewModelStore()
    lateinit var controller: ActivityController<ComponentActivity>
    lateinit var manage: ManageProfilesViewModel
    lateinit var household: WatchStateFixture

    val andre = Profile("a", "andre", admin = true)
    val bea = Profile("b", "Bea")
    val tom = Profile("t", "Tom", kids = true, kidsAge = 12, parentId = "b", dailyLimit = 60)

    @Before fun hilt() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
    }

    @After fun close() {
        try {
            compose.runOnUiThread {
                if (::controller.isInitialized) controller.close()
                store.clear()
            }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    /** Settings at [initial] (the index when null), over [tom], [andre] and [bea] with [pins], [chosen] watching. */
    fun open(
        chosen: Profile,
        pins: Map<String, String> = emptyMap(),
        initial: SettingsSection? = null,
    ) {
        compose.runOnUiThread {
            household = WatchStateFixture(listOf(tom, andre, bea), chosen.id, FakeCore().apply { roles.pins += pins })
            val sync = mockk<WatchSync>(relaxed = true)
            manage = ManageProfilesViewModel(household.repository, sync)
            // Only the chosen profile matters here; every other model's state is read and left unread.
            val none = MutableStateFlow(null)
            val models =
                mapOf<Class<out ViewModel>, ViewModel>(
                    ProfileSettingsViewModel::class.java to
                        mockk<ProfileSettingsViewModel>(relaxed = true) { every { profile } returns MutableStateFlow(chosen); every { subtitle } returns MutableStateFlow("off") },
                    SettingsViewModel::class.java to mockk<SettingsViewModel>(relaxed = true) { every { state } returns MutableStateFlow(SettingsUiState()) },
                    CacheBudgetViewModel::class.java to mockk<CacheBudgetViewModel>(relaxed = true) { every { state } returns none; every { failure } returns none },
                    LanCacheViewModel::class.java to mockk<LanCacheViewModel>(relaxed = true) { every { state } returns none; every { failure } returns none },
                    SystemViewModel::class.java to mockk<SystemViewModel>(relaxed = true) { every { state } returns none; every { failure } returns none },
                    AppearanceViewModel::class.java to AppearanceViewModel(InMemoryAppearanceSettings()),
                    ProfileViewModel::class.java to ProfileViewModel(household.repository, sync),
                    ManageProfilesViewModel::class.java to manage,
                )
            val provider =
                ViewModelProvider(
                    store,
                    object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(models.getValue(modelClass))!!
                    },
                )
            models.keys.forEach { provider[it] }
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            draw(initial)
        }
        compose.waitForIdle()
    }

    /** The activity recreated — a theme, locale or font-scale change — its view models kept, and drawn again as onCreate would. */
    fun recreate(initial: SettingsSection?) {
        compose.runOnUiThread {
            controller.recreate().visible()
            draw(initial)
        }
        compose.waitForIdle()
    }

    private fun draw(initial: SettingsSection?) {
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        controller.get().setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                MaterialTheme { ProfileGate { SettingsScreen(initial = initial, leavesFromSection = false, tally = emptyList(), onLeave = {}) } }
            }
        }
    }

    /** Says who you are from the section's own "Who are you?": Bea, by her PIN. */
    fun actAsBea() {
        compose.onNode(hasText("Bea") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithTag(PinFieldTag).performTextInput("2468")
        compose.waitForIdle()
    }

    fun tomsAge() = household.core.profiles.single { it.id == "t" }.kidsAge?.toInt()
}
