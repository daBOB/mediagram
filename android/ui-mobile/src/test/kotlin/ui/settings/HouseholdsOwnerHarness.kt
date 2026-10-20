package ui.settings

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import model.Profile
import testing.FakeCore
import ui.common.settings.SettingsSection
import ui.profile.PinFieldTag
import uniffi.mediagram_core.Profile as CoreProfile

/**
 * The owner's Households part on the Profiles page, over the real view model
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
}
