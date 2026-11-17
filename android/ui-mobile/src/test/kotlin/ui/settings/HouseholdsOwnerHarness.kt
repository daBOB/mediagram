package ui.settings

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import model.Profile
import testing.FakeCore
import ui.common.settings.SettingsSection
import ui.profile.PinFieldTag
import kotlin.test.assertTrue
import uniffi.mediagram_core.Profile as CoreProfile

/**
 * The owner's household changes on the Profiles page, over the real view model
 * and a fake core that keeps the households' rules. andre, the owner (PIN
 * 1234), runs Home with Bea and her kid Tom; Dan runs Flat with Cy; Barn is empty.
 */
abstract class HouseholdsOwnerHarness : ProfilesSettingsHarness() {
    val owner = andre.copy(owner = true)

    fun openAs(chosen: Profile) =
        open(chosen, pins = mapOf("a" to "1234", "d" to "5678", "c" to "1111"), initial = SettingsSection.PROFILES, people = listOf(tom, owner, bea)) {
            profiles = listOf(CoreProfile("d", "Dan", admin = true, household = "flat"), CoreProfile("c", "Cy", household = "flat"))
            homes.names += mapOf("flat" to "Flat", "barn" to "Barn")
        }

    /** A row's own action, named for its household. */
    fun action(label: String, household: String): SemanticsNodeInteraction = compose.onNode(hasContentDescription("$label $household"))

    fun inForm(text: String): SemanticsNodeInteraction = compose.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(hasTestTag(HouseholdFormTag)))

    fun field(label: String): SemanticsNodeInteraction = compose.onNode(hasContentDescription(label) and hasSetTextAction())

    /** The open row form's name, which the create form's field below shares a label with. */
    fun formField(): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(HouseholdFormTag)))

    fun pin(digits: String) {
        compose.onNodeWithTag(PinFieldTag).performTextInput(digits)
        compose.waitForIdle()
    }

    fun core(): FakeCore = household.core

    /** A household's two add forms' name fields, in the owner's view. */
    val addGrownUp = hasSetTextAction() and hasContentDescription("$ADD_A_GROWN_UP: name")
    val addKid = hasSetTextAction() and hasContentDescription("$ADD_A_KID: name")

    /** [name] typed into [household]'s [field], and its [label] pressed. */
    fun add(household: String, field: SemanticsMatcher, label: String, name: String) {
        inPart(household, field).performScrollTo().performTextReplacement(name)
        inPart(household, hasText(label) and hasClickAction()).performScrollTo().performClick()
        compose.waitForIdle()
    }

    fun made(name: String) = core().profiles.single { it.name == name }

    /** Each match's middle, top to bottom of the page: a header's line sits level with its heading, and below the fold too. */
    fun ys(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).fetchSemanticsNodes().map { it.positionInRoot.y + it.size.height / 2f }

    /** From [household]'s heading's row down to the next heading's. */
    fun span(household: String): (Float) -> Boolean {
        val top = ys(hasText(household) and isHeading()).single()
        val next = ys(isHeading()).filter { it > top }.minOrNull() ?: Float.MAX_VALUE
        // A pixel's slack: a heading and the line beside it are level, give or take rounding.
        return { it >= top - 1f && it < next - 1f }
    }

    fun countIn(household: String, matcher: SemanticsMatcher) = ys(matcher).count(span(household))

    /** The first node matching [matcher] in [household]'s part of the owner's view. */
    fun inPart(household: String, matcher: SemanticsMatcher): SemanticsNodeInteraction {
        val index = ys(matcher).indexOfFirst(span(household))
        assertTrue(index >= 0, "nothing like that in $household")
        return compose.onAllNodes(matcher)[index]
    }
}
