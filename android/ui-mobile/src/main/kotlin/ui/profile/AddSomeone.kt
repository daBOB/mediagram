package ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import catalog.profile.NEW_KID_LIMIT
import designsystem.Radius
import designsystem.Spacing
import model.DailyLimitWords
import model.HouseholdWords
import model.Profile
import ui.settings.LinePill
import ui.settings.Notice
import ui.settings.SettingsChip

/**
 * A quiet button that opens a form in place, and closes it again — the web's
 * `opener`, which Add someone and Create a household both are: a + in a
 * dashed square, drawn in the accent while open, then [label]. [description]
 * names it where [label] alone would not tell it from its like.
 */
@Composable
internal fun Opener(
    label: String,
    open: Boolean,
    onToggle: () -> Unit,
    description: String = label,
) {
    val colors = MaterialTheme.colorScheme
    val edge = if (open) colors.primary else colors.outline
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics {
                    contentDescription = description
                    stateDescription = if (open) EXPANDED else COLLAPSED
                }.padding(vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.size(36.dp).drawBehind {
                    val dashes = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())).takeUnless { open }
                    drawRoundRect(edge, cornerRadius = CornerRadius(Radius.control.toPx()), style = Stroke(1.dp.toPx(), pathEffect = dashes))
                },
        ) { Text("+", color = if (open) colors.primary else colors.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics {}) }
        Text(label, style = RowName)
    }
}

/**
 * Add someone — the web's `profile-add-someone.js`, the one way into a
 * household: an [Opener] whose form asks Grown-up or Kid where both may be
 * added ([grownUp], [kid]), a name, and for a kid its age limit, its daily
 * limit and — with more than one of [parents] — who it belongs to, [default]
 * to start with. The form keeps what was typed until an add from it went
 * through ([took] rises), then closes; [notice] says under it why the last
 * add from it did not take. [household] names the button where each one has
 * its own.
 */
@Composable
internal fun AddSomeone(
    grownUp: Boolean,
    kid: Boolean,
    parents: List<Profile>,
    default: Profile?,
    household: String?,
    took: Int,
    notice: String?,
    onAddGrownUp: (name: String) -> Unit,
    onAddKid: (name: String, age: Int, dailyLimit: Int?, parentId: String?) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val before = remember { took }
    LaunchedEffect(took) { if (took > before) open = false }
    Opener(HouseholdWords.ADD_SOMEONE, open, { open = !open }, household?.let { "${HouseholdWords.ADD_SOMEONE_TO} $it" } ?: HouseholdWords.ADD_SOMEONE)
    if (!open) return
    // Held only while open: closed and opened again, the form starts over.
    var kids by rememberSaveable { mutableStateOf(!grownUp) }
    var name by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableIntStateOf(NEW_KID_LIMIT) }
    // Saved as 0 for "No limit": no menu entry is zero minutes, and the saver takes no null.
    var daily by rememberSaveable { mutableIntStateOf(0) }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    // One chosen who has left the household since is nobody's choice any more.
    val parent = parents.find { it.id == chosen } ?: default
    val label = if (kids) ADD_A_KID else ADD_A_GROWN_UP
    val send = {
        val typed = name.trim()
        if (typed.isNotEmpty()) if (kids) onAddKid(typed, age, daily.takeIf { it > 0 }, parent?.id) else onAddGrownUp(typed)
    }
    Tray {
        if (grownUp && kid) {
            Row(Modifier.selectableGroup().semantics { contentDescription = HouseholdWords.ADD_SOMEONE }, horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                SettingsChip(HouseholdWords.GROWN_UP, selected = !kids, onClick = { kids = false })
                SettingsChip(HouseholdWords.KID, selected = kids, onClick = { kids = true })
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME) },
            singleLine = true,
            placeholder = { Text("Name") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { send() }),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label: name" },
        )
        if (kids) {
            Line(AGE_LIMIT) { LimitChoice(age, "Age limit for the new kid") { age = it } }
            Line(DailyLimitWords.LABEL) { DailyLimitChips(daily.takeIf { it > 0 }, "${DailyLimitWords.LABEL} for the new kid", { daily = it ?: 0 }) }
            if (parents.size > 1) Line(HouseholdWords.BELONGS_TO) { BelongsTo(parents, parent?.id) { chosen = it } }
        }
        LinePill(label, onClick = send, enabled = name.isNotBlank())
        notice?.let { Notice(it) }
    }
}

/** A chip for each of [parents], as Create a household's Admin is chosen; the group says what for. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BelongsTo(
    parents: List<Profile>,
    chosen: String?,
    onChoose: (String) -> Unit,
) {
    FlowRow(Modifier.selectableGroup().semantics { contentDescription = HouseholdWords.BELONGS_TO }, horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        parents.forEach { one -> SettingsChip(one.name, selected = one.id == chosen, onClick = { onChoose(one.id) }) }
    }
}
