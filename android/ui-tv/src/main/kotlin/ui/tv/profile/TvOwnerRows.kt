package ui.tv.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import catalog.profile.DONE
import catalog.profile.ManageUiState
import catalog.profile.managingAs
import catalog.profile.parts
import designsystem.Spacing
import designsystem.TvType
import model.HouseholdWords
import ui.common.profile.ManageActions
import ui.tv.TvTextRow

/**
 * The owner's view as rows the remote walks — the web's `ownerView`, calm:
 * every household, this device's first, as its header ("Name · Admin X · N
 * profiles"), whose OK opens its actions; then its people, one row each,
 * whose OK opens what may be changed of them, and one Add someone
 * ([TvManagePart], [onAdd]). Then Create a household, and Done. A household
 * the list does not name yet keeps its people, unheaded, as on the web.
 * [notice] says why the last change did not take.
 *
 * Every row is as wide as the page, the people's and the rest alike, so Down
 * is always the next row: a narrower header beside wide rows would be passed
 * over for the person under it, its centre that much further off.
 *
 * The remote lands on household [landing]'s header — on Create a household
 * for [NEW] — or the first. Opening someone's controls makes their household
 * the landing, and Back from them is their row again. When the person whose
 * row holds the remote leaves the household it was drawn in — removed, or
 * moved to another one — the remote goes to that household's header, or the
 * landing once that is gone too: never to nothing.
 */
@Composable
internal fun TvOwnerRows(
    state: ManageUiState.Managing,
    actions: ManageActions,
    landing: String?,
    notice: String?,
    go: (TvHouseholdStep) -> Unit,
    onAdd: (household: String) -> Unit,
) {
    var step by remember { mutableStateOf<TvManageStep>(TvManageStep.List) }
    val parts = state.parts()
    val headers = remember { mutableMapOf<String, FocusRequester>() }
    val create = remember { FocusRequester() }
    val headed = parts.filter { it.household != null }.mapNotNull { it.id }

    // Read once the rows are drawn, which is when their requesters are.
    fun header(id: String?) = id?.takeIf { it in headed }?.let(headers::get)

    fun target() = if (landing == NEW) create else header(landing) ?: header(headed.firstOrNull()) ?: create

    // Who holds the remote, and in which household's rows: a person, or null for any other row.
    var holding by remember { mutableStateOf<Pair<String, String>?>(null) }
    val everyone by rememberUpdatedState(state.everyone)

    fun gone(held: Pair<String, String>?) = held != null && everyone.none { it.id == held.first && it.household == held.second }
    val lost = gone(holding)
    LaunchedEffect(Unit) { target().requestFocus() }
    LaunchedEffect(lost) {
        if (!lost) return@LaunchedEffect
        val from = holding?.second
        holding = null
        (header(from) ?: target()).requestFocus()
    }

    // A row taking the remote because the one holding it went is no move of the viewer's: it does not end the holding.
    fun holds(
        household: String,
        person: String? = null,
    ) = Modifier.onFocusChanged { if (it.isFocused && !gone(holding)) holding = person?.let { one -> one to household } }
    TvManagePage(managingAs(state.actor.name), notice) {
        parts.forEach { part ->
            val id = part.id ?: return@forEach
            key(id) {
                part.household?.let { row ->
                    TvTextRow(
                        "${row.household.name} · ${row.line}",
                        onClick = { go(TvHouseholdStep.Actions(id)) },
                        modifier = holds(id).fillMaxWidth().padding(top = Spacing.medium).semantics { heading() },
                        focusRequester = remember { FocusRequester() }.also { headers[id] = it },
                        style = TvType.titleSmall,
                    )
                }
                val open = { next: TvManageStep ->
                    go(TvHouseholdStep.List(id))
                    step = next
                }
                TvManagePart(part, state, onOpen = open, onAdd = { onAdd(id) }, holds = { holds(id, it) })
            }
        }
        TvTextRow(HouseholdWords.CREATE_A_HOUSEHOLD, onClick = { go(TvHouseholdStep.CreateName()) }, holds(NEW).fillMaxWidth().padding(top = Spacing.medium), create)
        TvTextRow(DONE, onClick = actions.onClose, holds(NEW).fillMaxWidth().padding(top = Spacing.medium))
    }
    TvManageDialog(step, state, actions) { step = it }
}
