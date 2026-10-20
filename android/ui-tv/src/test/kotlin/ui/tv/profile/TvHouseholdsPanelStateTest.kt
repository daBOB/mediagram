package ui.tv.profile

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testing.FakeCore
import testing.FakeCoreProvider
import ui.tv.catalog.TvScreenStateTest
import ui.tv.setup.TvTextQuestionFieldTag
import uniffi.mediagram_core.Profile
import kotlin.test.assertEquals

/**
 * The owner's Households panel by remote alone, over the real view model and
 * [FakeCore]: andre, the owner, runs Home; Bea runs Flat, with Cy. Nobody is
 * watching, so the panel opens as the Owner row opens it: the owner's PIN first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvHouseholdsPanelStateTest : TvScreenStateTest() {
    private val core =
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var left = 0

    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    @After
    fun stopReading() = scope.cancel()

    private fun panel() {
        val provider = FakeCoreProvider(core)
        val repository = DefaultWatchStateRepository(provider, Dispatchers.Unconfined)
        runBlocking { repository.reload() }
        val owner = HouseholdsOwnerViewModel(repository, DefaultHouseholdsRepository(provider, repository, scope, Dispatchers.Unconfined), mockk(relaxed = true))
        show { TvHouseholdsPanel(owner, reread = {}, onDone = { left++ }) }
    }

    /** The panel, the owner's PIN given. */
    private fun opened() {
        panel()
        pin("1234")
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** By the remote's own digit keys, as a remote that has them types. */
    private fun pin(digits: String) = digits.forEach { press(DigitKeys[it - '0']) }

    /** Through the system keyboard, as the remote's text field takes one, over what the field held. */
    private fun name(text: String) {
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextClearance()
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput(text)
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()
        compose.waitForIdle()
    }

    private fun row(text: String) = compose.onNode(hasText(text) and hasClickAction())

    private fun noRow(text: String) = compose.onAllNodes(hasText(text) and hasClickAction()).assertCountEquals(0)

    @Test
    fun theOwnerRowAsksThePinThenListsHouseholds() {
        panel()
        compose.onNodeWithText(HouseholdWords.OWNERS_PIN).assertExists()
        pin("1234")
        row("Flat · Admin Bea · 2 profiles").assertIsFocused()
        row("Home · Admin andre · 1 profile").assertExists()
        row(HouseholdWords.CREATE_A_HOUSEHOLD).assertExists()
    }

    @Test
    fun givingUpTheOwnersPinLeavesThePanel() {
        panel()
        back()
        assertEquals(1, left)
    }

    /** Flat: a new name, another admin, this device; not empty, so no Delete. Home is the owner's own: no admin to change. */
    @Test
    fun okOnARowOffersOnlyWhatItAllows() {
        opened()
        press(Key.DirectionCenter)
        row(HouseholdWords.RENAME).assertIsFocused()
        row(HouseholdWords.CHANGE_ADMIN).assertExists()
        row(HouseholdWords.USE_HERE).assertExists()
        noRow(HouseholdWords.MOVE_HERE)
        noRow(HouseholdWords.DELETE)

        back()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        row(HouseholdWords.RENAME).assertIsFocused()
        row(HouseholdWords.MOVE_HERE).assertExists()
        noRow(HouseholdWords.CHANGE_ADMIN)
        noRow(HouseholdWords.USE_HERE)
        noRow(HouseholdWords.DELETE)
    }

    /** A taken name is said on the step that sent it; a free one is sent, and the remote is back on its household. */
    @Test
    fun renameStepsSendTheName() {
        opened()
        repeat(2) { press(Key.DirectionCenter) }
        name("home")
        compose.onNodeWithText(HouseholdWords.HOUSEHOLD_NAME_TAKEN).assertExists()
        name("Loft")
        assertEquals("Loft", core.homes.names["flat"])
        row("Loft · Admin Bea · 2 profiles").assertIsFocused()
    }

    /** A new household run by a new grown-up: its name, the admin, theirs, then their first PIN typed twice. */
    @Test
    fun createStepsSendNameAdminAndPin() {
        opened()
        repeat(2) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        name("Barn")
        row(HouseholdWords.A_NEW_GROWN_UP).assertIsFocused()
        row("Cy").assertExists()
        press(Key.DirectionCenter)
        name("Eve")
        compose.onNodeWithText("A PIN for Eve").assertExists()
        pin("2468")
        pin("2468")

        val eve = core.profiles.single { it.name == "Eve" }
        assertEquals("Barn", core.homes.names[eve.household])
        assertEquals("2468", core.roles.pins[eve.id])
        row(HouseholdWords.CREATE_A_HOUSEHOLD).assertIsFocused()
        row("Barn · Admin Eve · 1 profile").assertExists()
    }

    /** Back walks out the way the remote came in, a row at a time; Back from the list leaves the panel. */
    @Test
    fun backFromAStepReturnsToItsRow() {
        opened()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        row("Cy").assertIsFocused()

        back()
        row(HouseholdWords.MOVE_HERE).assertIsFocused()
        back()
        row("Home · Admin andre · 1 profile").assertIsFocused()
        assertEquals(0, left)
        back()
        assertEquals(1, left)
    }

    private companion object {
        val DigitKeys = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
    }
}
