package ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import ui.tv.catalog.films
import ui.tv.profile.TvManageProfilesTag
import ui.tv.profile.TvProfilePickerFirstTileTag
import ui.tv.profile.tvClaimTag
import ui.tv.profile.tvKidsLimitTag
import ui.tv.profile.tvPinKeyTag
import ui.tv.setup.TvTextQuestionFieldTag
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Who's watching, PINs and Manage profiles walked through the whole app with
 * the remote alone — over the real `ProfileViewModel` and
 * `ManageProfilesViewModel` and a `FakeCore` that keeps the household's
 * rules: the PIN, the roles, the wait after too many wrong PINs. Names go in
 * through the system keyboard; everything else is the D-pad.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvProfilesFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvAppFixture
    private lateinit var controller: ActivityController<TvAppTestActivity>

    private val andre = Profile("a", "andre", admin = true)
    private val bo = Profile("b", "Bo")
    private val cy = Profile("c", "Cy", kids = true, kidsAge = 6, parentId = "b")
    private val pins = mapOf("a" to "1234", "b" to "5678")

    @Before
    fun stubHilt() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    @After
    fun close() {
        try {
            compose.runOnUiThread {
                if (::controller.isInitialized) controller.close()
                if (::fixture.isInitialized) fixture.close()
            }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    @Test
    fun aDeviceWithNoGrownUpMakesTheFirstProfileWithAPinTypedTwice() {
        launch(listOf(Profile("k", "TV kids", kids = true)), chosen = null, pins = emptyMap())
        compose.onNodeWithText("Create the first profile — it runs this household").assertIsFocused()
        press(Key.DirectionCenter)
        name("Ann")
        compose.onNodeWithText("A PIN for Ann").assertExists()
        pin("2468")
        compose.onNodeWithText("The new PIN again").assertExists()
        pin("2468")

        compose.onAllNodesWithText("Create the first profile — it runs this household").assertCountEquals(0)
        compose.onNode(hasText("Ann") and hasClickAction()).assertExists()
        compose.onNodeWithTag(TvManageProfilesTag).assertExists()
        val ann = fixture.core.profiles.single { it.name == "Ann" }
        assertTrue(ann.admin, "the first profile runs the household")
        assertEquals("2468", fixture.core.roles.pins[ann.id])
    }

    @Test
    fun aKidsTileOpensWithoutAPin() {
        launch(listOf(andre, bo, cy), chosen = null)
        walkTo(hasText("Cy") and hasClickAction(), Key.DirectionRight)
        press(Key.DirectionCenter)
        // In, as Cy: the library filtered to Cy's own limit, and no PIN was asked.
        awaitLibrary("Nothing rated FSK 6 or under yet.")
        assertEquals("c", fixture.core.chosen)
    }

    @Test
    fun aGrownUpsTileOpensOnlyOnItsOwnPin() {
        launch(listOf(andre, bo, cy), chosen = null)
        compose.onNodeWithTag(TvProfilePickerFirstTileTag).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("andre’s PIN").assertExists()
        pin("1111")
        compose.onNodeWithText("Wrong PIN.").assertExists()
        pin("1234")
        awaitLibrary()
    }

    @Test
    fun tooManyWrongPinsMakeTheProfileWait() {
        launch(listOf(andre, bo, cy), chosen = null)
        press(Key.DirectionCenter)
        // The remote's own digit keys, as a remote that has them types.
        repeat(6) { listOf(Key.Nine, Key.Nine, Key.Nine, Key.Nine).forEach(::press) }
        compose.onNodeWithText("Too many wrong PINs. Try again in 60 s.").assertExists()
        pin("1234")
        compose.onNodeWithText("Too many wrong PINs. Try again in 60 s.").assertExists()
    }

    @Test
    fun aHouseholdWithNoAdminIsAskedWhoRunsItOnce() {
        launch(listOf(andre.copy(admin = false), bo, cy), chosen = null)
        press(Key.DirectionUp)
        compose.onNodeWithTag(tvClaimTag("a")).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("andre’s PIN").assertExists()
        pin("1234")
        compose.onAllNodesWithText("Who runs this household?").assertCountEquals(0)
        assertTrue(fixture.core.profiles.single { it.id == "a" }.admin)
        compose.onNodeWithTag(TvProfilePickerFirstTileTag).assertIsFocused()
    }

    /** Back on the PIN gives up the PIN only — the reopened picker's own Back ("Stay as I am") is the next press. */
    @Test
    fun backOnThePinGivesUpThePinNotThePicker() {
        launch(listOf(andre, bo, cy), chosen = "a")
        compose.onNode(hasContentDescription("Who's watching: andre")).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("Stay as I am").assertExists()
        walkTo(hasText("Bo") and hasClickAction(), Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Bo’s PIN").assertExists()

        back()
        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onNode(hasText("Bo") and hasClickAction()).assertIsFocused()

        back()
        awaitLibrary()
        assertFalse(controller.get().isFinishing, "Back left the app")
    }

    /**
     * Manage opened from the masthead, then the box left — Home, a remote
     * left alone. Back in the library, the next one to choose who is
     * watching meets the picker, not that grown-up's Manage, and the PIN
     * Manage held changes nothing any more.
     */
    @Test
    fun leavingTheAppClosesManageAndForgetsItsPin() {
        launch(listOf(andre, bo, cy), chosen = "a")
        reopenPicker()
        openManageAs(rowOf("Bo"), "5678")

        leaveAndReturn()
        awaitLibrary()
        reopenPicker()

        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onAllNodesWithText("As Bo").assertCountEquals(0)
        compose.runOnUiThread { fixture.manage.setKidsAge("c", 12) }
        compose.waitForIdle()
        assertEquals(6, fixture.core.profiles.single { it.id == "c" }.kidsAge?.toInt(), "a change went through on a PIN nobody gave again")
    }

    /** A first entry of a new PIN is not kept for whoever opens the app next. */
    @Test
    fun leavingTheAppDropsAHalfTypedNewPin() {
        launch(listOf(andre, bo, cy), chosen = null, pins = mapOf("a" to "1234"))
        walkTo(hasText("Bo") and hasClickAction(), Key.DirectionRight)
        press(Key.DirectionCenter)
        pin("5678")
        compose.onNodeWithText("The new PIN again").assertExists()

        leaveAndReturn()
        compose.onAllNodesWithText("The new PIN again").assertCountEquals(0)
        compose.onNodeWithText("Who's watching?").assertExists()
    }

    @Test
    fun theAdminResetsAPinAndRemovesAGrownUpWithItsKid() {
        launch(listOf(andre, bo, cy), chosen = null)
        openManageAs(rowOf("andre"), "1234")
        compose.onNode(rowOf("Bo")).assertIsFocused()

        press(Key.DirectionCenter)
        compose.onNode(hasText("Reset PIN") and hasAnyAncestor(isDialog())).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("A new PIN for Bo").assertExists()
        pin("4321")
        pin("4321")
        compose.onNodeWithText("As andre").assertExists()
        assertEquals("4321", fixture.core.roles.pins["b"])

        walkTo(rowOf("Bo"), Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Remove Bo, their kids, and everything they have watched?").assertExists()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.onAllNodesWithText("Bo").assertCountEquals(0)
        assertEquals(listOf("a"), fixture.core.profiles.map { it.id })

        walkTo(rowOf("Done"), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Who's watching?").assertExists()
        compose.onAllNodesWithText("Cy").assertCountEquals(0)
        compose.onNodeWithTag(TvManageProfilesTag).assertIsFocused()
    }

    @Test
    fun aGrownUpAddsAKidAtSixAndRaisesItsLimit() {
        launch(listOf(andre, bo, cy), chosen = null)
        openManageAs(rowOf("Bo"), "5678")
        compose.onNodeWithText("Grown-ups").assertDoesNotExist()
        compose.onNode(rowOf("Cy · FSK 6")).assertIsFocused()

        walkTo(rowOf("Add a kid"), Key.DirectionDown)
        press(Key.DirectionCenter)
        name("Lina")
        compose.onNodeWithTag(tvKidsLimitTag(6)).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNode(rowOf("Lina · FSK 6")).assertExists()

        walkTo(rowOf("Lina · FSK 6"), Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNode(rowOf("Lina · FSK 12")).assertExists()
        val lina = fixture.core.profiles.single { it.name == "Lina" }
        assertEquals(12, lina.kidsAge?.toInt())
        assertEquals("b", lina.parentId)
    }

    @Test
    fun aNameAlreadyTakenIsSaidOnThePanel() {
        launch(listOf(andre, bo, cy), chosen = null)
        openManageAs(rowOf("andre"), "1234")
        walkTo(rowOf("Add a kid"), Key.DirectionDown)
        press(Key.DirectionCenter)
        name("cy")
        press(Key.DirectionCenter)
        compose.onNodeWithText("A profile with that name already exists.").assertExists()
        assertEquals(3, fixture.core.profiles.size)
    }

    @Test
    fun aWrongPinOnWhoAreYouKeepsAsking() {
        launch(listOf(andre, bo, cy), chosen = null)
        walkTo(hasTestTag(TvManageProfilesTag), Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Who are you?").assertExists()
        press(Key.DirectionCenter)
        pin("0000")
        compose.onNodeWithText("Wrong PIN.").assertExists()
        back()
        compose.onNodeWithText("Who are you?").assertExists()
        back()
        compose.onNodeWithText("Who's watching?").assertExists()
    }

    private fun launch(
        profiles: List<Profile>,
        chosen: String?,
        pins: Map<String, String> = this.pins,
    ) {
        compose.runOnUiThread {
            fixture = TvAppFixture(TvSetupStage.READY, profiles, chosen, films(1), pins = pins)
            TvAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        val ready = if (chosen == null) "Who's watching?" else "Film 0"
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText(ready)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitLibrary(shows: String = "Film 0") {
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText(shows)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Who's watching?").assertCountEquals(0)
    }

    /** From the picker, Manage profiles → who you are → that grown-up's PIN. */
    private fun openManageAs(
        who: SemanticsMatcher,
        pin: String,
    ) {
        walkTo(hasTestTag(TvManageProfilesTag), Key.DirectionDown)
        press(Key.DirectionCenter)
        walkTo(who, Key.DirectionDown)
        press(Key.DirectionCenter)
        pin(pin)
        compose.onNodeWithText("Manage profiles").assertExists()
    }

    private fun rowOf(text: String) = hasText(text) and hasClickAction()

    /** The masthead's "who is watching" entry, as selecting it does. */
    private fun reopenPicker() {
        compose.onNode(hasContentDescription("Who's watching: andre")).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** The app left and opened again, the view models kept. */
    private fun leaveAndReturn() {
        compose.runOnUiThread { controller.pause().stop() }
        compose.waitForIdle()
        compose.runOnUiThread { controller.start().resume() }
        compose.waitForIdle()
    }

    /** Back through the activity's dispatcher — whatever the screen registered last takes it. */
    private fun back() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** A key to whatever holds the remote — a dialog's window first, while one is open. */
    private fun press(key: Key) {
        val inDialog = isFocused() and hasAnyAncestor(isDialog())
        val target = if (compose.onAllNodes(inDialog).fetchSemanticsNodes().isNotEmpty()) inDialog else isFocused()
        compose.onNode(target).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** [key] until [target] holds the remote — failing if it never does, which is a row out of the remote's reach. */
    private fun walkTo(
        target: SemanticsMatcher,
        key: Key,
    ) {
        repeat(MAX_STEPS) {
            if (compose.onAllNodes(target and isFocused()).fetchSemanticsNodes().isNotEmpty()) return
            press(key)
        }
        compose.onNode(target).assertIsFocused()
    }

    /** A name through the system keyboard, as the remote's text field takes one. */
    private fun name(text: String) {
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput(text)
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()
        compose.waitForIdle()
    }

    /** [digits] on the pad by D-pad and Centre alone, from wherever the remote rests on it. */
    private fun pin(digits: String) {
        digits.forEach { digit ->
            val (row, column) = PadPlaces.getValue(digit.toString())
            var (atRow, atColumn) = PadPlaces.getValue(focusedKey())
            // Along a row of digits first, then down a column: the bottom row
            // has a gap under "7", which a step down from it would cross.
            if (atRow == 3) {
                repeat(atRow - row) { press(Key.DirectionUp) }
                atRow = row
            }
            repeat(column - atColumn) { press(Key.DirectionRight) }
            repeat(atColumn - column) { press(Key.DirectionLeft) }
            repeat(row - atRow) { press(Key.DirectionDown) }
            repeat(atRow - row) { press(Key.DirectionUp) }
            compose.onNodeWithTag(tvPinKeyTag(digit.toString())).assertIsFocused()
            press(Key.DirectionCenter)
        }
    }

    private fun focusedKey(): String =
        PadPlaces.keys.first { key -> compose.onAllNodes(hasTestTag(tvPinKeyTag(key)) and isFocused()).fetchSemanticsNodes().isNotEmpty() }

    private companion object {
        const val MAX_STEPS = 12

        /** Where each key sits on the pad, row and column. */
        val PadPlaces: Map<String, Pair<Int, Int>> =
            ((1..9).associate { "$it" to Pair((it - 1) / 3, (it - 1) % 3) }) + mapOf("0" to Pair(3, 1), "Delete" to Pair(3, 2))
    }
}
