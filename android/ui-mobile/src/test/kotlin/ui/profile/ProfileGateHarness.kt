package ui.profile

import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import model.Profile
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import testing.FakeCore
import testing.WatchStateFixture

/**
 * The gate end to end: the real picker and Manage view models over the
 * app's own repository and a fake core that keeps the household's rules —
 * so what a tap sends is what the core answers, PINs, waits and refusals
 * included. Behind the gate stands one line naming who is watching, and a
 * way to reopen the picker as the bar's name does.
 *
 * Subclasses run in Robolectric's default window, not a tall one: there, a
 * dialog holding a text field, opened by a tap in a larger window, can
 * relayout its window forever — a plain M3 AlertDialog and OutlinedTextField
 * do the same, so it is not these screens'. Manage's lower sections are
 * scrolled to instead.
 */
abstract class ProfileGateHarness {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    lateinit var household: WatchStateFixture

    /** Manage's view model, for asking it to act as the screen could. */
    lateinit var manage: ManageProfilesViewModel
    private val store = ViewModelStore()

    val andre = Profile("a", "andre", admin = true)
    val bea = Profile("b", "Bea")
    val tom = Profile("t", "Tom", kids = true, kidsAge = 12, parentId = "b")

    // The view models are already held in the store; only Hilt's
    // generated-Activity factory lookup needs replacing under Robolectric.
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

    /** [profiles] in the core with their [pins], [chosen] already chosen on this device, and the gate drawn over them. */
    fun open(
        vararg profiles: Profile,
        pins: Map<String, String> = emptyMap(),
        chosen: String? = null,
    ) {
        compose.runOnUiThread {
            household = WatchStateFixture(profiles.toList(), chosen, FakeCore().apply { roles.pins += pins })
            val sync = mockk<WatchSync>(relaxed = true)
            manage = ManageProfilesViewModel(household.repository, sync)
            val models = listOf(ProfileViewModel(household.repository, sync), manage)
            val provider =
                ViewModelProvider(
                    store,
                    object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(models.first(modelClass::isInstance))!!
                    },
                )
            models.forEach { provider[it::class.java] }
            val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    MaterialTheme {
                        ProfileGate { bar ->
                            Column {
                                Text("Watching as ${bar.name}")
                                TextButton(onClick = bar.onChoose) { Text("Change") }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    fun pin(digits: String) {
        compose.onNodeWithTag(PinFieldTag).performTextInput(digits)
        compose.waitForIdle()
    }

    fun inDialog(text: String): SemanticsNodeInteraction = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    /** A name field, by the label of the form it belongs to. */
    fun named(form: String): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasContentDescription("$form: name"))

    fun manageAs(
        name: String,
        digits: String,
    ) {
        compose.onNodeWithText("Manage profiles").performClick()
        compose.onNodeWithText("Who are you?").assertExists()
        compose.onNodeWithText(name).performClick()
        pin(digits)
        compose.onNodeWithText("As $name").assertExists()
    }

    fun profile(id: String) = household.core.profiles.single { it.id == id }

    /** Back as the open dialog's own window receives it. */
    fun backOnTheDialog() {
        compose.runOnUiThread { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    fun backOnTheScreen() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    fun isFinishing(): Boolean = controller.get().isFinishing

    /** The app left — Home, the screen timing out — and opened again, the view models kept. */
    fun leaveAndReturn() {
        compose.runOnUiThread { controller.pause().stop() }
        compose.waitForIdle()
        compose.runOnUiThread { controller.start().resume() }
        compose.waitForIdle()
    }
}
