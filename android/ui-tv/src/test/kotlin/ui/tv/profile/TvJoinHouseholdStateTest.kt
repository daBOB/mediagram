package ui.tv.profile

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import catalog.profile.JoinHouseholdViewModel
import data.DefaultHouseholdsRepository
import data.DefaultWatchStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import model.HouseholdWords
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testing.FakeCore
import testing.FakeCoreProvider
import ui.tv.catalog.TvScreenStateTest
import uniffi.mediagram_core.Profile

/**
 * Join a household on television, by remote alone, over the real view model
 * and [FakeCore]: andre, the owner, runs Home; Dan runs Attic and has no PIN,
 * so the owner's opens it; Bea runs Flat. This box has joined none, so each
 * is offered, a row apiece, in the core's order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvJoinHouseholdStateTest : TvScreenStateTest() {
    private val core =
        FakeCore().apply {
            profiles =
                listOf(
                    Profile("a", "andre", admin = true, owner = true),
                    Profile("d", "Dan", admin = true, household = "attic"),
                    Profile("b", "Bea", admin = true, household = "flat"),
                )
            roles.pins["a"] = "1234"
            roles.pins["b"] = "5678"
            homes.names["attic"] = "Attic"
            homes.names["flat"] = "Flat"
            homes.joined = null
        }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var join: JoinHouseholdViewModel

    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    @After
    fun stopReading() = scope.cancel()

    private fun joining() {
        val provider = FakeCoreProvider(core)
        val repository = DefaultWatchStateRepository(provider, Dispatchers.Unconfined)
        runBlocking { repository.reload() }
        join = JoinHouseholdViewModel(repository, DefaultHouseholdsRepository(provider, repository, scope, Dispatchers.Unconfined))
        show { TvJoinHousehold(join) }
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun firstRowHasFocus() {
        joining()
        compose.onNodeWithText(HouseholdWords.JOIN_A_HOUSEHOLD).assertExists()
        compose.onNodeWithText(HouseholdWords.JOIN_NOTE).assertExists()
        compose.onNode(hasText("Attic") and hasClickAction()).assertIsFocused()
    }

    @Test
    fun downAndOkAskTheAdminsPin() {
        joining()
        press(Key.DirectionDown)
        compose.onNode(hasText("Flat") and hasClickAction()).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Bea’s PIN, or the owner’s").assertExists()
    }

    /**
     * Back from a household's PIN lands on that household; the one holding
     * the remote going — deleted elsewhere — hands it back there, not to
     * nothing nor to the top.
     */
    @Test
    fun aRowThatGoesHandsTheRemoteToTheOneThatAsked() {
        joining()
        repeat(2) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        back()
        compose.onNode(hasText("Home") and hasClickAction()).assertIsFocused()
        press(Key.DirectionUp)
        compose.runOnUiThread {
            core.homes.names.remove("flat")
            join.reread()
        }
        compose.waitForIdle()
        compose.onAllNodes(hasText("Flat") and hasClickAction()).assertCountEquals(0)
        compose.onNode(hasText("Home") and hasClickAction()).assertIsFocused()
    }
}
