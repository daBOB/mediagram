package ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import catalog.profile.ManageUiState
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import setup.AppearanceViewModel
import setup.ProfileSettingsViewModel
import setup.SettingsUiState
import setup.SettingsViewModel
import system.CacheBudgetViewModel
import system.LanCacheViewModel
import system.SystemViewModel
import ui.common.settings.SettingsSection
import testing.FakeCore
import testing.WatchStateFixture
import ui.profile.PinFieldTag
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Settings › Profiles on the phone, the web's Profiles tab: shown only while a grown-up is
 * chosen, everyone listed with their role, and Manage inline over the real view model and a
 * fake core — its PIN held for the visit and dropped once the section is left.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfilesSettingsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val store = ViewModelStore()
    private lateinit var controller: ActivityController<ComponentActivity>
    private lateinit var manage: ManageProfilesViewModel
    private lateinit var household: WatchStateFixture

    private val andre = Profile("a", "andre", admin = true)
    private val bea = Profile("b", "Bea")
    private val tom = Profile("t", "Tom", kids = true, kidsAge = 12, parentId = "b", dailyLimit = 60)

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
    private fun open(
        chosen: Profile,
        pins: Map<String, String> = emptyMap(),
        initial: SettingsSection? = null,
    ) {
        compose.runOnUiThread {
            household = WatchStateFixture(listOf(tom, andre, bea), chosen.id, FakeCore().apply { roles.pins += pins })
            manage = ManageProfilesViewModel(household.repository, mockk<WatchSync>(relaxed = true))
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
            val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    MaterialTheme { SettingsScreen(initial = initial, leavesFromSection = false, tally = emptyList(), onLeave = {}) }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Says who you are from the section's own "Who are you?": Bea, by her PIN. */
    private fun actAsBea() {
        compose.onNode(hasText("Bea") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithTag(PinFieldTag).performTextInput("2468")
        compose.waitForIdle()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h800dp")
    fun aGrownUpSeesProfilesInSettings() {
        open(chosen = bea)
        compose.onNodeWithText("Profiles").assertExists()
        compose.onNodeWithText("3 profiles").assertExists()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h800dp")
    fun aKidSeesNoProfilesSection() {
        open(chosen = tom)
        compose.onNodeWithText("Profile").assertExists()
        compose.onNodeWithText("Profiles").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h2400dp")
    fun everyoneIsListedWithTheirRole() {
        open(chosen = bea, pins = mapOf("a" to "1234"))
        compose.onNodeWithText("Profiles").performClick()
        compose.onNodeWithText("Everyone here").assertExists()
        val admin = compose.onNodeWithText("Admin · PIN set").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("Grown-up · No PIN").assertExists()
        val kid = compose.onNodeWithText("Kid · Bea · FSK 12 · 1 h").fetchSemanticsNode().boundsInRoot.top
        assertTrue(admin < kid, "expected the grown-ups listed before the kids")
        // Changing anything still asks who you are first.
        compose.onNodeWithText("Who are you?").assertExists()
    }

    // Default window for the PIN tests, where a text-field dialog cannot relayout forever as in a
    // taller one; the index's last rows sit below its fold there, so they open the section directly.
    @Test
    fun leavingTheSectionForgetsThePin() {
        open(chosen = bea, pins = mapOf("b" to "2468"), initial = SettingsSection.PROFILES)
        actAsBea()
        compose.onNodeWithText("As Bea").assertExists()
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("SETTINGS").assertExists()
        assertEquals(ManageUiState.Closed, manage.state.value)
        compose.runOnUiThread { manage.setKidsAge("t", 6) }
        compose.waitForIdle()
        assertEquals(12, tomsAge(), "a change went through on a PIN nobody gave again")
    }

    @Test
    fun returningToTheAppAsksWhoYouAreAgain() {
        open(chosen = bea, pins = mapOf("b" to "2468"), initial = SettingsSection.PROFILES)
        actAsBea()
        compose.runOnUiThread { controller.pause().stop() }
        compose.waitForIdle()
        assertEquals(ManageUiState.Closed, manage.state.value)
        compose.runOnUiThread { controller.start().resume() }
        compose.waitForIdle()

        compose.onNodeWithText("Who are you?").assertExists()
        compose.onNodeWithText("As Bea").assertDoesNotExist()
        compose.runOnUiThread { manage.setKidsAge("t", 6) }
        compose.waitForIdle()
        assertEquals(12, tomsAge(), "a change went through on a PIN nobody gave again")
    }

    private fun tomsAge() = household.core.profiles.single { it.id == "t" }.kidsAge?.toInt()
}
