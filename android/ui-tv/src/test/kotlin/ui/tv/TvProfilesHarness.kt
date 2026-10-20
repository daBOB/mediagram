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
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import ui.tv.catalog.films
import ui.tv.profile.TvManageProfilesTag
import ui.tv.profile.tvPinKeyTag
import ui.tv.setup.TvTextQuestionFieldTag

/**
 * Who's watching, PINs and the Profiles page walked through the whole app
 * with the remote alone — over the real `ProfileViewModel` and
 * `ManageProfilesViewModel` and a `FakeCore` that keeps the household's
 * rules: the PIN, the roles, the wait after too many wrong PINs. Names go in
 * through the system keyboard; everything else is the D-pad.
 */
abstract class TvProfilesHarness {
    @get:Rule val compose = createEmptyComposeRule()
    internal lateinit var fixture: TvAppFixture
    internal lateinit var controller: ActivityController<TvAppTestActivity>

    internal val andre = Profile("a", "andre", admin = true)
    internal val bo = Profile("b", "Bo")
    internal val cy = Profile("c", "Cy", kids = true, kidsAge = 6, parentId = "b")
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

    internal fun launch(
        profiles: List<Profile>,
        chosen: String?,
        pins: Map<String, String> = this.pins,
        ready: String = if (chosen == null) "Who's watching?" else "Film 0",
    ) {
        compose.runOnUiThread {
            fixture = TvAppFixture(TvSetupStage.READY, profiles, chosen, films(1), pins = pins, settingsSeeTheViewer = true)
            TvAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText(ready)).fetchSemanticsNodes().isNotEmpty() }
    }

    internal fun awaitLibrary(shows: String = "Film 0") {
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText(shows)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Who's watching?").assertCountEquals(0)
    }

    /** From the picker's Manage profiles, or [fromPicker] not, from the Profiles page's own row → who you are → that grown-up's PIN. */
    internal fun openManageAs(
        who: SemanticsMatcher,
        pin: String,
        fromPicker: Boolean = true,
    ) {
        if (fromPicker) {
            walkTo(hasTestTag(TvManageProfilesTag), Key.DirectionDown)
            press(Key.DirectionCenter)
        }
        compose.onNode(rowOf("Manage profiles")).assertIsFocused()
        press(Key.DirectionCenter)
        walkTo(who, Key.DirectionDown)
        press(Key.DirectionCenter)
        pin(pin)
        compose.onNodeWithText("Manage profiles").assertExists()
    }

    internal fun rowOf(text: String) = hasText(text) and hasClickAction()

    /** The app left and opened again, the view models kept. */
    internal fun leaveAndReturn() {
        compose.runOnUiThread { controller.pause().stop() }
        compose.waitForIdle()
        compose.runOnUiThread { controller.start().resume() }
        compose.waitForIdle()
    }

    /** Back through the activity's dispatcher — whatever the screen registered last takes it. */
    internal fun back() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** A key to whatever holds the remote — a dialog's window first, while one is open. */
    internal fun press(key: Key) {
        val inDialog = isFocused() and hasAnyAncestor(isDialog())
        val target = if (compose.onAllNodes(inDialog).fetchSemanticsNodes().isNotEmpty()) inDialog else isFocused()
        compose.onNode(target).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** The masthead's avatar, then its menu's "Switch profile", as selecting them does. */
    internal fun reopenPicker() {
        compose.onNode(hasContentDescription("Menu, watching as andre")).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNode(hasText("Switch profile") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** A name through the system keyboard, as the remote's text field takes one. */
    internal fun name(text: String) {
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput(text)
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()
        compose.waitForIdle()
    }

    /** [key] until [target] holds the remote — failing if it never does, which is a row out of the remote's reach. */
    internal fun walkTo(
        target: SemanticsMatcher,
        key: Key,
    ) {
        repeat(MAX_STEPS) {
            if (compose.onAllNodes(target and isFocused()).fetchSemanticsNodes().isNotEmpty()) return
            press(key)
        }
        compose.onNode(target).assertIsFocused()
    }

    /** [digits] on the pad by D-pad and Centre alone, from wherever the remote rests on it. */
    internal fun pin(digits: String) {
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
