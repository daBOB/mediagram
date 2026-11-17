package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import designsystem.SectionHead
import designsystem.Spacing
import model.HouseholdWords

/**
 * The owner's household changes inside Manage's owner view — the web's
 * `household-rows.js`: each household's [content] header — its name, who
 * runs it and how many profiles are in it, the owner's actions on it, the
 * form one opens — and Create a household, which [content] places after the
 * last. Why the last change did not take is said beside the header or form
 * it was sent from; the create form's place when that household has gone. A
 * form closes, or empties, only once a change from it went through: a
 * refusal, or a PIN prompt dismissed, keeps what was typed. Nothing is
 * pressed while a change is out. Every PIN is the view model's to ask.
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
                CreateHouseholdForm(open.createChoices, enabled = !open.sending, took = took[""] ?: 0) { name, adminId, adminName ->
                    sentFrom = null
                    owners.create(name, adminId, adminName)
                }
                open.notice.takeIf { noticeAt == null }?.let { Notice(it) }
            }
        },
    )
}

/** One household's header: its name as a heading, its line, what the owner may do to it, the form one of those opens, and its notice. */
@OptIn(ExperimentalLayoutApi::class)
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
    // Opens its form under the header, and closes it again.
    val opens = @Composable { label: String -> RowPill(label, name, enabled) { form = label.takeUnless { form == label } } }
    Row(Modifier.fillMaxWidth().padding(top = Spacing.large, bottom = Spacing.small), verticalAlignment = Alignment.CenterVertically) {
        // A heading, so a screen reader can move from household to household.
        Text(name, style = SectionHead, modifier = Modifier.weight(1f).semantics { heading() })
        Text(row.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
        modifier = Modifier.padding(bottom = Spacing.small),
    ) {
        if (row.canRename) opens(HouseholdWords.RENAME)
        if (row.canChangeAdmin) opens(HouseholdWords.CHANGE_ADMIN)
        if (row.canMove) opens(HouseholdWords.MOVE_HERE)
        if (row.canUseHere) RowPill(HouseholdWords.USE_HERE, name, enabled) { send { owners.useHere(id) } }
        if (row.canDelete) RowPill(HouseholdWords.DELETE, name, enabled) { send { owners.delete(id) } }
    }
    when (form) {
        HouseholdWords.RENAME -> RenameForm(name, enabled) { send { owners.rename(id, it) } }
        HouseholdWords.CHANGE_ADMIN -> PersonForm(row.adminChoices, enabled) { send { owners.changeAdmin(id, it) } }
        HouseholdWords.MOVE_HERE -> PersonForm(row.moveChoices, enabled) { send { owners.moveHere(id, it) } }
    }
    notice?.let { Notice(it, Modifier.padding(bottom = Spacing.small)) }
    HorizontalDivider(thickness = 0.5.dp, color = LocalCatalogueTones.current.ruleSoft)
}

/** Named for its household, so a screen reader does not hear a column of the same action. */
@Composable
private fun RowPill(
    label: String,
    household: String,
    enabled: Boolean,
    onClick: () -> Unit,
) = QuietPill(label, onClick = onClick, enabled = enabled, contentDescription = "$label $household")

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
