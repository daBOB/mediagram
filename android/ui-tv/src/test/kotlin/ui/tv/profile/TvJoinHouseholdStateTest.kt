package ui.tv.profile

import androidx.compose.foundation.layout.Row
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
import ui.tv.TvTextRow
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

    /** [barBeside]: a row to the list's left stands in for the chrome bar, as Settings has one. */
    private fun joining(barBeside: Boolean = false) {
        val provider = FakeCoreProvider(core)
        val repository = DefaultWatchStateRepository(provider, Dispatchers.Unconfined)
        runBlocking { repository.reload() }
        join = JoinHouseholdViewModel(repository, DefaultHouseholdsRepository(provider, repository, scope, Dispatchers.Unconfined))
        show {
            Row {
                if (barBeside) TvTextRow(BAR, onClick = {})
                TvJoinHousehold(join)
            }
        }
    }

    private fun goes(id: String) {
        compose.runOnUiThread {
            core.homes.names.remove(id)
            join.reread()
        }
        compose.waitForIdle()
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
        goes("flat")
        compose.onAllNodes(hasText("Flat") and hasClickAction()).assertCountEquals(0)
        compose.onNode(hasText("Home") and hasClickAction()).assertIsFocused()
    }

    /** The row the remote left for the bar going leaves the remote in the bar. */
    @Test
    fun theRowLeftForTheBarGoingKeepsTheRemoteInTheBar() {
        joining(barBeside = true)
        press(Key.DirectionDown)
        press(Key.DirectionLeft)
        compose.onNode(hasText(BAR) and hasClickAction()).assertIsFocused()
        goes("flat")
        compose.onAllNodes(hasText("Flat") and hasClickAction()).assertCountEquals(0)
        compose.onNode(hasText(BAR) and hasClickAction()).assertIsFocused()
    }

    /** So does the first row going, the one the remote lands on: landing is for the list arriving, not for each change. */
    @Test
    fun theFirstRowGoingKeepsTheRemoteInTheBar() {
        joining(barBeside = true)
        press(Key.DirectionLeft)
        compose.onNode(hasText(BAR) and hasClickAction()).assertIsFocused()
        goes("attic")
        compose.onAllNodes(hasText("Attic") and hasClickAction()).assertCountEquals(0)
        compose.onNode(hasText(BAR) and hasClickAction()).assertIsFocused()
    }

    /** No household any PIN here opens: a line says so where the rows would be. */
    @Test
    fun noHouseholdThisBoxCanJoinSaysSo() {
        core.roles.pins.clear()
        joining()
        compose.onNodeWithText(HouseholdWords.NONE_TO_JOIN).assertExists()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}

private const val BAR = "Menu"
