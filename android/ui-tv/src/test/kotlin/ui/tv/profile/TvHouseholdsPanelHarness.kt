package ui.tv.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import catalog.profile.OwnerPin
import catalog.profile.HouseholdsOwnerUiState
import catalog.profile.HouseholdsOwnerViewModel
import data.DefaultHouseholdsRepository
import data.DefaultWatchStateRepository
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import model.HouseholdWords
import org.junit.After
import org.junit.Before
import testing.FakeCore
import testing.FakeCoreProvider
import ui.tv.catalog.TvScreenStateTest
import ui.tv.setup.TvTextQuestionFieldTag
import uniffi.mediagram_core.Profile
import kotlin.test.assertIs

/**
 * The owner's household steps over the real view model and [FakeCore], drawn
 * around a stand-in for the owner's view that lists only the households:
 * andre, the owner, runs Home; Bea runs Flat, with Cy, who has no PIN.
 * Drawn as the owner's view draws it, after the owner's tile and PIN —
 * unless a test puts the owner in front of it, watching, with no PIN given.
 */
abstract class TvHouseholdsPanelHarness : TvScreenStateTest() {
    protected val core =
        FakeCore().apply {
            profiles =
                listOf(
                    Profile("a", "andre", admin = true, owner = true),
                    Profile("b", "Bea", admin = true, household = "flat"),
                    Profile("c", "Cy", household = "flat"),
                )
            roles.pins["a"] = "1234"
            roles.pins["b"] = "5678"
            homes.names["flat"] = "Flat"
        }

    /** Hands the core out; a test holds a call open here. */
    protected val provider = FakeCoreProvider(core)
    protected lateinit var owner: HouseholdsOwnerViewModel
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /** How many times the panel was left. */
    protected var left = 0

    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    @After
    fun stopReading() = scope.cancel()

    /** The panel; [watching] puts the owner in front of it, so it opens with no PIN — otherwise the owner's view shows, the PIN held. */
    protected fun panel(watching: Boolean = false) {
        if (watching) core.chosen = "a"
        val repository = DefaultWatchStateRepository(provider, Dispatchers.Unconfined)
        runBlocking { repository.reload() }
        // As Manage leaves it once the owner's tile had their PIN.
        val pin =
            OwnerPin().apply {
                held = "1234".takeUnless { watching }
                showView(!watching)
            }
        owner = HouseholdsOwnerViewModel(repository, DefaultHouseholdsRepository(provider, repository, scope, Dispatchers.Unconfined), mockk(relaxed = true), pin)
        // As the gate says it, which this panel is drawn without.
        if (watching) owner.watching("a")
        show { TvHouseholdsPanel(owner, reread = {}, onDone = { left++ }) { landing, go -> Headers(landing, go) } }
    }

    /** A stand-in for the owner's view around the panel: each household's header, then Create a household. */
    @Composable
    private fun Headers(
        landing: String?,
        go: (TvHouseholdStep) -> Unit,
    ) {
        val rows = (owner.state.collectAsState().value as? HouseholdsOwnerUiState.Open)?.rows.orEmpty()
        val headers = rows.map { it.household.id to "${it.household.name} · ${it.line}" } + (NEW to HouseholdWords.CREATE_A_HOUSEHOLD)
        TvChoicesScreen(HouseholdWords.HOUSEHOLDS, null, headers, landing) { go(if (it == NEW) TvHouseholdStep.CreateName() else TvHouseholdStep.Actions(it)) }
    }

    /** What the part holds now. */
    protected fun part() = assertIs<HouseholdsOwnerUiState.Open>(owner.state.value)

    protected fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** By the remote's own digit keys, as a remote that has them types. */
    protected fun pin(digits: String) = digits.forEach { press(DigitKeys[it - '0']) }

    /** Through the system keyboard, as the remote's text field takes one, over what the field held. */
    protected fun name(text: String) {
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextClearance()
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput(text)
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()
        compose.waitForIdle()
    }

    protected fun row(text: String) = compose.onNode(hasText(text) and hasClickAction())

    protected fun noRow(text: String) = compose.onAllNodes(hasText(text) and hasClickAction()).assertCountEquals(0)

    private companion object {
        val DigitKeys = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
    }
}
