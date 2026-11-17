package ui.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import model.HOME_HOUSEHOLD
import model.HouseholdWords
import model.Profile
import testing.FakeCore
import ui.common.settings.SettingsSection
import ui.profile.PinFieldTag
import ui.profile.openIfClosed
import ui.profile.personRow
import kotlin.test.assertTrue
import uniffi.mediagram_core.Profile as CoreProfile

/**
 * The owner's household changes on the Profiles page, over the real view model
 * and a fake core that keeps the households' rules. andre, the owner (PIN
 * 1234), runs Home with Bea and her kid Tom; Dan runs Flat with Cy; Barn is
 * empty — or, by [openWithMadani], Celine runs Madani instead of Flat.
 */
abstract class HouseholdsOwnerHarness : ProfilesSettingsHarness() {
    val owner = andre.copy(owner = true)

    /** This device on [joined], Home's people listed in [people]'s order. */
    fun openAs(
        chosen: Profile,
        people: List<Profile> = listOf(tom, owner, bea),
        joined: String = HOME_HOUSEHOLD,
    ) = open(chosen, pins = mapOf("a" to "1234", "d" to "5678", "c" to "1111"), initial = SettingsSection.PROFILES, people = people) {
        profiles = listOf(CoreProfile("d", "Dan", admin = true, household = "flat"), CoreProfile("c", "Cy", household = "flat"))
        homes.names += mapOf("flat" to "Flat", "barn" to "Barn")
        homes.joined = joined
    }

    /** Home with Bea's PIN 2468; Celine (5678) runs Madani; Barn is empty; this device shows [joined]. */
    fun openWithMadani(
        chosen: Profile,
        joined: String = HOME_HOUSEHOLD,
    ) = open(chosen, pins = mapOf("a" to "1234", "b" to "2468", "c" to "5678"), initial = SettingsSection.PROFILES, people = listOf(tom, owner, bea)) {
        profiles = listOf(CoreProfile("c", "Celine", admin = true, household = "madani"))
        homes.names += mapOf("madani" to "Madani", "barn" to "Barn")
        homes.joined = joined
    }

    fun row(name: String): SemanticsNodeInteraction = compose.onNode(personRow(name))

    /** [name]'s row opened, unless it already is. */
    fun opened(name: String) {
        row(name).openIfClosed()
        compose.waitForIdle()
    }

    /** A household's ⋯, named for it. */
    fun more(household: String): SemanticsNodeInteraction = compose.onNode(hasContentDescription("${HouseholdWords.ACTIONS_FOR} $household"))

    /** What [household]'s ⋯ offers, top to bottom; the menu closed again after. */
    fun menu(household: String): List<String> {
        more(household).performScrollTo().performClick()
        val words =
            compose.onAllNodes(hasClickAction() and hasAnyAncestor(isPopup())).fetchSemanticsNodes()
                .sortedBy { it.positionInRoot.y }
                .map { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text } }
        more(household).performClick()
        compose.waitForIdle()
        return words
    }

    /** [label] chosen from [household]'s ⋯. */
    fun choose(label: String, household: String) {
        more(household).performScrollTo().performClick()
        compose.onNode(hasText(label) and hasAnyAncestor(isPopup())).performClick()
        compose.waitForIdle()
    }

    /** A household's one Add someone, named for it. */
    fun addSomeone(household: String): SemanticsNodeInteraction = compose.onNode(hasContentDescription("${HouseholdWords.ADD_SOMEONE_TO} $household"))

    /** [household]'s Add someone form open, unless it already is. */
    fun openAdd(household: String) {
        addSomeone(household).openIfClosed()
        compose.waitForIdle()
    }

    fun inForm(text: String): SemanticsNodeInteraction = compose.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(hasTestTag(HouseholdFormTag)))

    fun field(label: String): SemanticsNodeInteraction = compose.onNode(hasContentDescription(label) and hasSetTextAction())

    /** The open row form's name, which the create form's field below shares a label with. */
    fun formField(): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(HouseholdFormTag)))

    fun pin(digits: String) {
        compose.onNodeWithTag(PinFieldTag).performTextInput(digits)
        compose.waitForIdle()
    }

    fun core(): FakeCore = household.core

    /** The Add someone form's name field, as a grown-up or a kid is chosen in it. */
    val addGrownUp = hasSetTextAction() and hasContentDescription("$ADD_A_GROWN_UP: name")
    val addKid = hasSetTextAction() and hasContentDescription("$ADD_A_KID: name")

    /** [household]'s Add someone opened, a kid chosen when [label] adds one, [name] typed into [field], and [label] pressed. */
    fun add(household: String, field: SemanticsMatcher, label: String, name: String) {
        openAdd(household)
        if (label == ADD_A_KID) inPart(household, hasText(HouseholdWords.KID) and isSelectable()).performScrollTo().performClick()
        inPart(household, field).performScrollTo().performTextReplacement(name)
        inPart(household, hasText(label) and hasClickAction()).performScrollTo().performClick()
        compose.waitForIdle()
    }

    fun made(name: String) = core().profiles.single { it.name == name }

    /** Each match's middle, top to bottom of the page, below the fold too. */
    fun ys(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).fetchSemanticsNodes().map { it.positionInRoot.y + it.size.height / 2f }

    /** From [household]'s heading down to the next heading. */
    fun span(household: String): (Float) -> Boolean {
        val top = ys(hasText(household) and isHeading()).single()
        val next = ys(isHeading()).filter { it > top }.minOrNull() ?: Float.MAX_VALUE
        // A pixel's slack, for rounding.
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
