package ui.profile

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import model.HouseholdWords

/** [name]'s row on Manage while it opens: a button that says whether it is open. */
internal fun personRow(name: String): SemanticsMatcher =
    hasText(name) and hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)

/** The Add someone of anyone but the owner, who has one, unnamed. */
internal val addSomeoneUnnamed: SemanticsMatcher = hasContentDescription(HouseholdWords.ADD_SOMEONE) and hasClickAction()

/** [name]'s tile, on Who's watching? or Who's managing?. */
internal fun tile(name: String): SemanticsMatcher = hasTestTag(ProfileTileTag) and hasText(name)

/** A person's row, or an Add someone, opened unless it already is. */
internal fun SemanticsNodeInteraction.openIfClosed() {
    if (fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) != EXPANDED) performScrollTo().performClick()
}
