package ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import designsystem.Spacing
import model.DAILY_LIMIT_CHOICES
import model.KIDS_LIMITS
import model.dailyLimitChoiceFor
import ui.settings.LinePill
import ui.settings.SettingsChip

/** The longest name the core keeps (`profiles.rs`'s `MAX_NAME`); the web's field stops at the same. */
internal const val MAX_NAME = 120

/**
 * A name and the pill that adds — the web's `addForm`, which the picker's
 * first profile is. The field empties once its name is handed over, as the
 * web's redrawn form does; a blank name hands over nothing.
 */
@Composable
internal fun NameForm(
    label: String,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (name.isNotBlank()) {
            onSubmit(name.trim())
            name = ""
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME) },
            singleLine = true,
            placeholder = { Text("Name") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label: name" },
        )
        LinePill(label, onClick = submit, enabled = name.isNotBlank(), modifier = Modifier.padding(top = Spacing.small))
    }
}

/**
 * A kid's limit, FSK 6 or 12 — the web's `limitChoice` select as two chips,
 * named for whose limit it is. Choosing the one already set sends nothing,
 * as a select fires no change for it.
 */
@Composable
internal fun LimitChoice(
    value: Int,
    label: String,
    onChoose: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        modifier = Modifier.selectableGroup().semantics { contentDescription = label },
    ) {
        KIDS_LIMITS.forEach { age -> SettingsChip("FSK $age", selected = value == age, onClick = { if (age != value) onChoose(age) }) }
    }
}

/**
 * A kid's daily watching limit — the web's `dailyChoice` select as chips, in
 * the menu's order, named for whose it is. A limit set elsewhere that is not
 * on the menu shows as itself. Choosing the one already set sends nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DailyLimitChips(
    minutes: Int?,
    label: String,
    onChoose: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = dailyLimitChoiceFor(minutes)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        modifier = modifier.selectableGroup().semantics { contentDescription = label },
    ) {
        (DAILY_LIMIT_CHOICES + shown).distinct().forEach { choice ->
            SettingsChip(choice.label, selected = shown == choice, onClick = { if (choice != shown) onChoose(choice.minutes) })
        }
    }
}
