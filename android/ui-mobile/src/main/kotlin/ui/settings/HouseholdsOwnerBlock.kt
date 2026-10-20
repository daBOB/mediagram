package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.HouseholdRow
import catalog.profile.HouseholdsOwnerUiState
import catalog.profile.HouseholdsOwnerViewModel
import designsystem.SectionHead
import designsystem.Spacing
import model.HouseholdWords
import ui.profile.PinDialog
import ui.profile.ProfileRow

/**
 * The owner's Households part of the Profiles page — the web's
 * `settings-households.js`: every live household, who runs it and how many
 * profiles are in it, and the owner's changes — a new household, a new name
 * or admin, a grown-up moved in, this device moved to one, an empty one
 * deleted. Anyone else is told these are the owner's, and may say they are
 * the owner. With the owner watching, the view model opens it at once and
 * asks their PIN at the first change; the PIN is held for the visit
 * ([ui.common.settings.ProfilesVisit]). Every PIN is the view model's to ask.
 */
@Composable
internal fun HouseholdsOwnerBlock() {
    val owners: HouseholdsOwnerViewModel = hiltViewModel()
    val state by owners.state.collectAsStateWithLifecycle()
    val pin by owners.pin.collectAsStateWithLifecycle()
    // The households not read yet: nothing to say about them.
    if (state is HouseholdsOwnerUiState.Loading) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(HouseholdWords.HOUSEHOLDS, style = SectionHead, color = MaterialTheme.colorScheme.onSurface)
        when (val part = state) {
            is HouseholdsOwnerUiState.Closed -> {
                Text(HouseholdWords.OWNER_NOTE, style = MaterialTheme.typography.bodyLarge)
                if (part.canOpen) QuietPill(HouseholdWords.OWNER, onClick = owners::open)
            }
            is HouseholdsOwnerUiState.Open -> OpenPart(part, owners)
            is HouseholdsOwnerUiState.Loading -> Unit
        }
    }
    pin?.let { PinDialog(it, onPin = owners::enterPin, onDismiss = owners::cancelPin) }
}

/**
 * Every household, then a new one. Why the last change did not take is said
 * beside the row or form it was sent from, as the web says it; the create
 * form's place when that row has gone. Nothing is pressed while a change is out.
 */
@Composable
private fun OpenPart(
    part: HouseholdsOwnerUiState.Open,
    owners: HouseholdsOwnerViewModel,
) {
    // The household a change was last sent from; null is the create form.
    var sentFrom by rememberSaveable { mutableStateOf<String?>(null) }
    val noticeAt = sentFrom?.takeIf { id -> part.rows.any { it.household.id == id } }
    part.rows.forEach { row ->
        val id = row.household.id
        key(id) { HouseholdEntry(row, owners, part.sending, part.notice.takeIf { noticeAt == id }) { sentFrom = id } }
    }
    CreateHouseholdForm(part.createChoices, enabled = !part.sending) { name, adminId, adminName ->
        sentFrom = null
        owners.create(name, adminId, adminName)
    }
    part.notice.takeIf { noticeAt == null }?.let { Notice(it) }
}

/** One household: its name and line, what the owner may do to it, the form one of those opens, and its notice. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HouseholdEntry(
    row: HouseholdRow,
    owners: HouseholdsOwnerViewModel,
    sending: Boolean,
    notice: String?,
    onSend: () -> Unit,
) {
    val id = row.household.id
    val name = row.household.name
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    // Closed once sent, as Manage's add forms empty; a refusal is said under the row.
    val send = { action: () -> Unit ->
        onSend()
        form = null
        action()
    }
    // Opens its form under the row, and closes it again.
    val opens = @Composable { label: String -> RowPill(label, name, !sending) { form = label.takeUnless { form == label } } }
    ProfileRow(
        name,
        below = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
                modifier = Modifier.padding(bottom = Spacing.small),
            ) {
                if (row.canRename) opens(HouseholdWords.RENAME)
                if (row.canChangeAdmin) opens(HouseholdWords.CHANGE_ADMIN)
                if (row.canMove) opens(HouseholdWords.MOVE_HERE)
                if (row.canUseHere) RowPill(HouseholdWords.USE_HERE, name, !sending) { send { owners.useHere(id) } }
                if (row.canDelete) RowPill(HouseholdWords.DELETE, name, !sending) { send { owners.delete(id) } }
            }
            when (form) {
                HouseholdWords.RENAME -> RenameForm(name, enabled = !sending) { send { owners.rename(id, it) } }
                HouseholdWords.CHANGE_ADMIN -> PersonForm(row.adminChoices, enabled = !sending) { send { owners.changeAdmin(id, it) } }
                HouseholdWords.MOVE_HERE -> PersonForm(row.moveChoices, enabled = !sending) { send { owners.moveHere(id, it) } }
            }
            notice?.let { Notice(it, Modifier.padding(bottom = Spacing.small)) }
        },
    ) {
        Text(
            row.line,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
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
private fun Notice(
    text: String,
    modifier: Modifier = Modifier,
) = Text(
    text,
    color = MaterialTheme.colorScheme.error,
    style = MaterialTheme.typography.bodyMedium,
    modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
)
