package ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.HouseholdRow
import catalog.profile.HouseholdsOwnerUiState
import catalog.profile.HouseholdsOwnerViewModel
import designsystem.LocalCatalogueTones
import designsystem.Spacing
import model.HouseholdWords
import ui.profile.Opener
import ui.profile.Tray

/**
 * The owner's household changes inside Manage's owner view — the web's
 * `household-rows.js`: each household's [content] header — its name as a
 * heading, who runs it and how many profiles are in it, and a ⋯ with what the
 * owner may do to it ([HouseholdMenu]), the form one of those opens — and
 * Create a household, a button that opens its form, which [content] places
 * after the last. Why the last change did not take is said beside the header
 * or form it was sent from; the create form's place when that household has
 * gone. A form closes only once a change from it went through: a refusal, or
 * a PIN prompt dismissed, keeps what was typed. Nothing is pressed while a
 * change is out. Every PIN is the view model's to ask.
 */
@Composable
internal fun OwnerHouseholds(
    owners: HouseholdsOwnerViewModel,
    content: @Composable (header: @Composable (HouseholdRow) -> Unit, create: @Composable () -> Unit) -> Unit,
) {
    val part = owners.state.collectAsStateWithLifecycle().value as? HouseholdsOwnerUiState.Open
    // The household a change was last sent from; null is the create form.
    var sentFrom by rememberSaveable { mutableStateOf<String?>(null) }
    val noticeAt = sentFrom?.takeIf { id -> part?.rows.orEmpty().any { it.household.id == id } }
    // How many changes from each place went through, by household id; "" is the create form.
    val took = remember { mutableStateMapOf<String, Int>() }
    var seen by remember { mutableStateOf(part?.answers) }
    LaunchedEffect(part?.answers) {
        val answers = part?.answers ?: return@LaunchedEffect
        val place = sentFrom.orEmpty()
        if (answers > (seen ?: answers) && part.notice == null) took[place] = (took[place] ?: 0) + 1
        seen = answers
    }
    content(
        { row ->
            val id = row.household.id
            HouseholdHeader(row, owners, enabled = part?.sending == false, part?.notice.takeIf { noticeAt == id }, took[id] ?: 0) { sentFrom = id }
        },
        {
            part?.let { open ->
                CreateHousehold(open, took[""] ?: 0, open.notice.takeIf { noticeAt == null }) { name, adminId, adminName ->
                    sentFrom = null
                    owners.create(name, adminId, adminName)
                }
            }
        },
    )
}

/** One household's line: its name as a heading, its line and ⋯, the form an action opens under it, and its notice. */
@Composable
private fun HouseholdHeader(
    row: HouseholdRow,
    owners: HouseholdsOwnerViewModel,
    enabled: Boolean,
    notice: String?,
    took: Int,
    onSend: () -> Unit,
) {
    val id = row.household.id
    val name = row.household.name
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(took) { if (took > 0) form = null }
    val send = { action: () -> Unit ->
        onSend()
        action()
    }
    // Opens its form under the line, and closes it again.
    val opens = { label: String -> { form = label.takeUnless { form == label } } }
    val actions =
        with(HouseholdWords) {
            listOfNotNull(
                (RENAME to opens(RENAME)).takeIf { row.canRename },
                (CHANGE_ADMIN to opens(CHANGE_ADMIN)).takeIf { row.canChangeAdmin },
                (MOVE_HERE to opens(MOVE_HERE)).takeIf { row.canMove },
                (USE_HERE to { send { owners.useHere(id) } }).takeIf { row.canUseHere },
                (DELETE to { send { owners.delete(id) } }).takeIf { row.canDelete },
            )
        }
    Row(Modifier.fillMaxWidth().padding(top = Spacing.extraLarge, bottom = Spacing.small), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            // A heading, so a screen reader can move from household to household.
            Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(row.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HouseholdMenu(name, actions, enabled)
    }
    val cancel = { form = null }
    when (form) {
        HouseholdWords.RENAME -> Tray { RenameForm(name, enabled, cancel) { send { owners.rename(id, it) } } }
        HouseholdWords.CHANGE_ADMIN -> Tray { PersonForm(row.adminChoices, enabled, cancel) { send { owners.changeAdmin(id, it) } } }
        HouseholdWords.MOVE_HERE -> Tray { PersonForm(row.moveChoices, enabled, cancel) { send { owners.moveHere(id, it) } } }
    }
    notice?.let { Notice(it, Modifier.padding(bottom = Spacing.small)) }
    HorizontalDivider(thickness = 0.5.dp, color = LocalCatalogueTones.current.ruleSoft)
}

/** Create a household: a button that opens its form, closed again once one was made from it ([took] rises). */
@Composable
private fun CreateHousehold(
    open: HouseholdsOwnerUiState.Open,
    took: Int,
    notice: String?,
    onCreate: (name: String, adminId: String?, adminName: String?) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    val before = remember { took }
    LaunchedEffect(took) { if (took > before) creating = false }
    Column(Modifier.padding(top = Spacing.medium)) {
        Opener(HouseholdWords.CREATE_A_HOUSEHOLD, creating, { creating = !creating })
        if (creating) Tray { CreateHouseholdForm(open.createChoices, enabled = !open.sending, took = took, onCreate = onCreate) }
        notice?.let { Notice(it) }
    }
}

/** Why a change did not take, read out as it changes. */
@Composable
internal fun Notice(
    text: String,
    modifier: Modifier = Modifier,
) = Text(
    text,
    color = MaterialTheme.colorScheme.error,
    style = MaterialTheme.typography.bodyMedium,
    modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
)
