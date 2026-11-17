package ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import designsystem.LocalCatalogueTones
import designsystem.Radius
import designsystem.Spacing
import model.Profile

/** What a row or button that opens something in place says of itself. */
internal const val EXPANDED = "Expanded"
internal const val COLLAPSED = "Collapsed"

/**
 * A person on the Profiles page — the web's `profile-manage-person.js`: one
 * calm row, their initial, their name and the line that says who they are,
 * and a › when the manager may change something, which opens [controls] in
 * place under it on a tap ([onToggle]). [open] is whether it is; the page
 * keeps one open at a time. With no [controls] — someone the manager may not
 * change — the row is no button and opens nothing.
 */
@Composable
internal fun PersonRow(
    profile: Profile,
    line: String,
    open: Boolean,
    onToggle: () -> Unit,
    controls: (@Composable ColumnScope.() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val press =
        if (controls == null) {
            Modifier
        } else {
            Modifier.clickable(role = Role.Button, onClick = onToggle).semantics { stateDescription = if (open) EXPANDED else COLLAPSED }
        }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).then(press).padding(vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Initial(profile.name, size = 36.dp, style = MaterialTheme.typography.titleSmall, ink = if (open) colors.primary else colors.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(profile.name, style = RowName)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        if (controls != null) {
            // Turned down while open; the row's state already says so to a screen reader.
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = if (open) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.rotate(if (open) 90f else 0f).clearAndSetSemantics {},
            )
        }
    }
    if (open && controls != null) Tray(controls)
    HorizontalDivider(thickness = 0.5.dp, color = LocalCatalogueTones.current.ruleSoft)
}

/** A row's name, and an opener's word: the interface face, a step up from its line, as the web sets them. */
internal val RowName: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium)

/** Where a row or a form opens in place: one step up from the page, rounded as a card. */
@Composable
internal fun Tray(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.medium)
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        content = content,
    )
}
