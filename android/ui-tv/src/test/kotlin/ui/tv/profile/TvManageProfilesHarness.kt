package ui.tv.profile

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import catalog.profile.ManageUiState
import model.Profile
import org.junit.Before
import ui.common.profile.ManageActions
import ui.tv.catalog.TvScreenStateTest

/**
 * Manage profiles on television ([TvManageProfiles]), over plain state and
 * recorded actions, driven by the remote out of touch mode, where a remote
 * always is: andre runs the household, Bea is a grown-up, Tom her kid.
 */
abstract class TvManageProfilesHarness : TvScreenStateTest() {
    protected val andre = Profile("a", "andre", admin = true, hasPin = true)
    protected val bea = Profile("b", "Bea", hasPin = true)
    protected val tom = Profile("t", "Tom", kids = true, kidsAge = 12, parentId = "b")
    protected val calls = mutableListOf<String>()
    private val actions =
        ManageActions(
            onActAs = { calls += "actAs $it" },
            onAddKid = { name, age, daily, _ -> calls += "addKid $name $age $daily" },
            onSetKidsAge = { id, age -> calls += "age $id $age" },
            onSetDailyLimit = { id, minutes -> calls += "daily $id $minutes" },
            onSetHiddenSections = { id, sections -> calls += "sections $id $sections" },
            onRemove = { calls += "remove $it" },
            onAddGrownUp = { name, _ -> calls += "addGrownUp $name" },
            onChangePin = { calls += "pin $it" },
            onClose = { calls += "close" },
        )

    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    protected fun manage(state: ManageUiState) = show { TvManageProfiles(state, actions) }

    /** A key to whatever holds the remote — the dialog's window first, while one is open. */
    protected fun press(key: Key) {
        val inDialog = isFocused() and hasAnyAncestor(isDialog())
        val target = if (compose.onAllNodes(inDialog).fetchSemanticsNodes().isNotEmpty()) inDialog else isFocused()
        compose.onNode(target).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** [name]'s row: a person's name over their line, or an action's words. */
    protected fun row(name: String) = compose.onNode(hasText(name) and hasClickAction())
}
