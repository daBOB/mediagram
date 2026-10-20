package ui.tv.profile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import catalog.profile.CHANGE_YOUR_PIN
import catalog.profile.ManageUiState
import model.Household
import model.Profile
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.common.profile.ManageActions
import ui.tv.catalog.TvScreenStateTest
import kotlin.test.assertEquals

/**
 * The owner's Manage profiles on television, walked with the remote: andre
 * owns the install and runs Home with Lea; Ida runs the Loft, listed first
 * live; Maja runs the Flat with Tim. A change reaches the panel as the view
 * model would hand it on, so the rows redraw under the remote.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvManageHouseholdsStateTest : TvScreenStateTest() {
    private val andre = Profile("andre", "andre", admin = true, hasPin = true, owner = true)
    private val lea = Profile("lea", "Lea", kids = true, kidsAge = 12)
    private val ida = Profile("ida", "Ida", admin = true, hasPin = true, household = "loft")
    private val maja = Profile("maja", "Maja", admin = true, hasPin = true, household = "flat")
    private val tim = Profile("tim", "Tim", kids = true, kidsAge = 6, parentId = "maja", household = "flat")
    private val live = listOf(Household("loft", "Loft", "ida"), Household("flat", "Flat", "maja"), Household("home", "Home", "andre"))
    private val shown = mutableStateOf(ManageUiState.Managing(andre, listOf(ida, maja), listOf(lea, tim), households = live))
    private val calls = mutableListOf<String>()

    private fun change(edit: (ManageUiState.Managing) -> ManageUiState.Managing) {
        compose.runOnUiThread { shown.value = edit(shown.value).copy(answers = shown.value.answers + 1) }
        compose.waitForIdle()
    }

    private val actions =
        ManageActions(
            onActAs = {},
            onAddKid = { _, _, _ -> },
            onSetKidsAge = { id, age ->
                calls += "age $id $age"
                change { it.copy(kids = it.kids.map { kid -> if (kid.id == id) kid.copy(kidsAge = age) else kid }) }
            },
            onSetDailyLimit = { _, _ -> },
            onSetHiddenSections = { _, _ -> },
            onRemove = { id ->
                calls += "remove $id"
                change { it.copy(grownUps = it.grownUps.filterNot { one -> one.id == id }) }
            },
            onAddGrownUp = {},
            onChangePin = { calls += "pin $it" },
            onClose = {},
        )

    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        show { TvManageProfiles(shown.value, actions) }
    }

    /** A key to whatever holds the remote — the dialog's window first, while one is open. */
    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        val inDialog = isFocused() and hasAnyAncestor(isDialog())
        val target = if (compose.onAllNodes(inDialog).fetchSemanticsNodes().isNotEmpty()) inDialog else isFocused()
        compose.onNode(target).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun everyHouseholdsRowsAreReachedWalkingDownAndTheirNamesTakeNoFocus() {
        listOf("Home", "Loft", "Flat").forEach { compose.onNodeWithText(it).assertExists() }
        // This device's household first, where adding stays.
        compose.onNodeWithText(ADD_A_GROWN_UP).assertIsFocused()
        press(Key.DirectionDown, 2)
        compose.onNodeWithText(ADD_A_KID).assertIsFocused()
        // Past the Loft's name, Grown-ups, and on to the Flat's.
        press(Key.DirectionDown)
        compose.onNodeWithText("Ida").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("Maja").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("Tim · FSK 6 · No limit").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText(CHANGE_YOUR_PIN).assertIsFocused()
    }

    @Test
    fun anotherHouseholdsRowsAreChangedByRemoteAndAKidsRowKeepsIt() {
        press(Key.DirectionDown, 5)
        press(Key.DirectionCenter)
        // The current limit holds the remote; the other is one step down.
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals(listOf("age tim 12"), calls)
        compose.onNodeWithText("Tim · FSK 12 · No limit").assertIsFocused()
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        // Where the remote is after the PIN pad is the pad's to say: in the app it stands in this screen's place.
        assertEquals(listOf("age tim 12", "pin maja"), calls)
    }

    @Test
    fun aHouseholdLeftWithNobodyToManageLosesItsName() {
        press(Key.DirectionDown, 3)
        press(Key.DirectionCenter)
        // Reset PIN -> Sections -> Remove, then past Cancel.
        press(Key.DirectionDown, 2)
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertEquals(listOf("remove ida"), calls)
        compose.onNodeWithText("Loft").assertDoesNotExist()
        // The row the remote was on went; it starts again at the top.
        compose.onNodeWithText(ADD_A_GROWN_UP).assertIsFocused()
    }
}
