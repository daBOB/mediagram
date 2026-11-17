package ui.tv.profile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import catalog.profile.HouseholdRow
import catalog.profile.ManageUiState
import catalog.profile.PinPrompt
import model.Household
import model.Profile
import org.junit.Before
import ui.common.profile.ManageActions
import ui.tv.catalog.TvScreenStateTest
import ui.tv.setup.TvTextQuestionFieldTag

/**
 * The owner's view on television ([TvOwnerManage]), walked with the remote:
 * andre owns the install and runs Home with Lea; Ida runs the Loft, listed
 * first live; Maja runs the Flat with Tim. A change reaches the rows as the
 * view model would hand it on, so they redraw under the remote; what is sent,
 * and where the panel around them is sent, is recorded.
 */
abstract class TvOwnerViewHarness : TvScreenStateTest() {
    private val andre = Profile("andre", "andre", admin = true, hasPin = true, owner = true)
    private val lea = Profile("lea", "Lea", kids = true, kidsAge = 12)
    private val ida = Profile("ida", "Ida", admin = true, hasPin = true, household = "loft")
    private val maja = Profile("maja", "Maja", admin = true, hasPin = true, household = "flat")
    private val tim = Profile("tim", "Tim", kids = true, kidsAge = 6, parentId = "maja", household = "flat")
    private val live = listOf(Household("loft", "Loft", "ida"), Household("flat", "Flat", "maja"), Household("home", "Home", "andre"))
    private val lines = mapOf("loft" to "Admin Ida · 1 profile", "flat" to "Admin Maja · 2 profiles", "home" to "Admin andre · 2 profiles")
    private val rows = live.map { HouseholdRow(it, lines.getValue(it.id), it.id == "home", true, false, false, emptyList(), emptyList()) }
    private val shown = mutableStateOf(ManageUiState.Managing(andre, listOf(ida, maja), listOf(lea, tim), households = live, rows = rows))
    private val landing = mutableStateOf<String?>(null)
    protected val prompt = mutableStateOf<PinPrompt?>(null)
    internal val went = mutableListOf<TvHouseholdStep>()
    protected val calls = mutableListOf<String>()

    /** What the next add's answer is. */
    protected var onAdd: () -> Unit = {}

    protected fun change(edit: (ManageUiState.Managing) -> ManageUiState.Managing) {
        compose.runOnUiThread {
            val next = edit(shown.value)
            shown.value = next.copy(answers = shown.value.answers + 1, everyone = listOf(next.actor) + next.grownUps + next.kids)
        }
        compose.waitForIdle()
    }

    private val actions =
        ManageActions(
            onActAs = {},
            onAddKid = { name, age, daily, parent ->
                calls += "kid $name $age $daily $parent"
                onAdd()
            },
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
            onAddGrownUp = { _, _ -> },
            onChangePin = { calls += "pin $it" },
            onClose = {},
        )

    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        show {
            TvOwnerManage(shown.value, actions, landing.value, { step -> went += step.also { if (it is TvHouseholdStep.List) landing.value = it.row } }, prompt.value) {
                prompt.value = null
            }
        }
    }

    /** A key to whatever holds the remote — the dialog's window first, while one is open. */
    protected fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        val inDialog = isFocused() and hasAnyAncestor(isDialog())
        val target = if (compose.onAllNodes(inDialog).fetchSemanticsNodes().isNotEmpty()) inDialog else isFocused()
        compose.onNode(target).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    protected fun focusedOn(start: String) = compose.onNode(isFocused()).assert(hasText(start, substring = true))

    protected fun walkTo(target: SemanticsMatcher) {
        repeat(20) { if (compose.onAllNodes(target and isFocused()).fetchSemanticsNodes().isEmpty()) press(Key.DirectionDown) }
        compose.onNode(target and isFocused()).assertExists()
    }

    protected fun name(text: String) {
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextClearance()
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput(text)
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()
        compose.waitForIdle()
    }
}
