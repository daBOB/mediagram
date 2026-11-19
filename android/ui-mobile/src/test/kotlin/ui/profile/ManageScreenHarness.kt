package ui.profile

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import catalog.profile.ManageUiState
import org.junit.After
import org.junit.Rule
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import ui.common.profile.ManageActions

/**
 * Manage profiles drawn straight from a state — [show], then [shown] for the
 * next one — with no page around it, over a record of what it asked for
 * ([calls]). Subclasses run in a tall window, so a tap reaches every row as it
 * is: there is no page scroll to bring one into view.
 */
abstract class ManageScreenHarness {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    val calls = mutableListOf<String>()
    val shown = mutableStateOf<ManageUiState>(ManageUiState.Closed)
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

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    fun show(state: ManageUiState) {
        shown.value = state
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MaterialTheme { ManageProfilesScreen(shown.value, actions) } }
        }
        compose.waitForIdle()
    }

    fun inDialog(text: String): SemanticsNodeInteraction = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    fun opened(name: String) = compose.onNode(personRow(name)).performClick()

    fun openAdd() = compose.onNode(addSomeoneUnnamed).performClick()

    /** How [text] is drawn: struck through, or not. */
    fun decorationOf(text: String): TextDecoration? {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(text), useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().layoutInput.style.textDecoration
    }
}
