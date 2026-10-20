package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import designsystem.Spacing
import model.HouseholdWords
import model.Profile
import ui.profile.MAX_NAME

/** The open form under a household row, for tests: its names are Manage's and Everyone here's too. */
internal const val HouseholdFormTag = "household-form"

/*
 * The owner's forms — the web's `household-forms.js`: a household's new
 * name, a grown-up chosen from a list, and a new household with its admin,
 * inline as Manage's add forms are. Each only gathers what was typed or
 * chosen, and hands over nothing incomplete, as the web's return silently;
 * the view model asks the PINs, and says why a change did not take. None
 * sends while not [enabled]: a change is already out.
 */

/** A household's new name, starting from [current]. */
@Composable
internal fun RenameForm(
    current: String,
    enabled: Boolean,
    onRename: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(current) }
    val send = { if (enabled && name.isNotBlank()) onRename(name.trim()) }
    Column(Modifier.testTag(HouseholdFormTag), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        NameField(name, { name = it }, HouseholdWords.HOUSEHOLD_NAME, send)
        LinePill(HouseholdWords.RENAME, onClick = send, enabled = enabled && name.isNotBlank())
    }
}

/** One of [people], a pill each: pressing one chooses them, as the row's own pill said what for. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonForm(
    people: List<Profile>,
    enabled: Boolean,
    onChoose: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
        modifier = Modifier.testTag(HouseholdFormTag),
    ) {
        people.forEach { one -> QuietPill(one.name, onClick = { onChoose(one.id) }, enabled = enabled) }
    }
}

/**
 * A new household: its name, then its admin — a new grown-up, named here, or
 * one of [movers]. Hands over the name and the grown-up's id, or the new
 * one's name, and empties, as Manage's add forms do.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreateHouseholdForm(
    movers: List<Profile>,
    enabled: Boolean,
    onCreate: (name: String, adminId: String?, adminName: String?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    // Null is a new grown-up, the web's first choice.
    var adminId by rememberSaveable { mutableStateOf<String?>(null) }
    var newName by rememberSaveable { mutableStateOf("") }
    val ready = enabled && name.isNotBlank() && (adminId != null || newName.isNotBlank())
    val send = {
        if (ready) {
            onCreate(name.trim(), adminId, newName.trim().takeIf { adminId == null })
            name = ""
            newName = ""
            adminId = null
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        NameField(name, { name = it }, HouseholdWords.HOUSEHOLD_NAME, send)
        Text(HouseholdWords.ADMIN, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            SettingsChip(HouseholdWords.A_NEW_GROWN_UP, selected = adminId == null, onClick = { adminId = null })
            movers.forEach { one -> SettingsChip(one.name, selected = adminId == one.id, onClick = { adminId = one.id }) }
        }
        if (adminId == null) NameField(newName, { newName = it }, HouseholdWords.NEW_GROWN_UP_NAME, send)
        LinePill(HouseholdWords.CREATE_A_HOUSEHOLD, onClick = send, enabled = ready)
    }
}

/** A name, labelled [label] for the eye and the screen reader alike. */
@Composable
private fun NameField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(MAX_NAME)) },
        singleLine = true,
        placeholder = { Text(label) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
    )
}
