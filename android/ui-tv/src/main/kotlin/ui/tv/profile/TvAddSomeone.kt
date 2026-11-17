package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import catalog.profile.ManagePart
import model.HouseholdWords

/**
 * Add someone on television — the web's `profile-add-someone.js` as steps of
 * their own: where both may be added, Grown-up or Kid first, the remote on
 * Grown-up as the web's form starts; then [TvAddProfileFlow] for that one —
 * for a kid, Belongs to among [part]'s grown-ups where there is more than
 * one, its admin first. Back from that flow's first step is the choice again,
 * on the one chosen, and Back from the choice is [onCancel]. What was chosen
 * and typed is saved state, each kind's apart, so a holder around this keeps
 * it while a PIN pad stands in its place. [notice] says why the last add from
 * it did not take, on whichever step shows. The headings name the household
 * where each has its own.
 */
@Composable
internal fun TvAddSomeone(
    part: ManagePart,
    notice: String?,
    onAdd: (name: String, age: Int?, dailyLimit: Int?, parentId: String?) -> Unit,
    onCancel: () -> Unit,
) {
    val both = part.addsGrownUp && part.addsKid
    // Null while Grown-up or Kid is asked; [last] is the one chosen, which the question lands on again.
    var kid by rememberSaveable { mutableStateOf(if (both) null else part.addsKid) }
    var last by rememberSaveable { mutableStateOf(false) }
    val kinds = rememberSaveableStateHolder()
    val where = part.name?.let { " · $it" }.orEmpty()
    val chosen = kid
    if (chosen == null) {
        BackHandler(onBack = onCancel)
        val choices = listOf(GROWN_UP to HouseholdWords.GROWN_UP, KID to HouseholdWords.KID)
        TvChoicesScreen(HouseholdWords.ADD_SOMEONE + where, null, choices, if (last) KID else GROWN_UP, notice) {
            last = it == KID
            kid = last
        }
        return
    }
    kinds.SaveableStateProvider(chosen) {
        TvAddProfileFlow(
            heading = (if (chosen) ADD_A_KID else ADD_A_GROWN_UP) + where,
            askLimit = chosen,
            onAdd = onAdd,
            onCancel = { if (both) kid = null else onCancel() },
            parents = if (chosen) part.kidParents else emptyList(),
            parentDefault = part.kidParentDefault?.id,
            notice = notice,
        )
    }
}

private const val GROWN_UP = "grown-up"
private const val KID = "kid"
