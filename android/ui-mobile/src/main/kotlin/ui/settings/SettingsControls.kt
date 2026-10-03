package ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import designsystem.Radius
import designsystem.Spacing

/**
 * Settings' own controls, at the tokens the approved round-2 mockups draw
 * them in (`round2/tokens.css:99-109`, `round2/b-storage.html`'s
 * `.segments`/`.switch`, `round2/b-appearance.html`'s `.swatch`/`.dot`) —
 * every outline control on a Settings/System page, so a section composable
 * reaches for one of these rather than a bare Material button.
 */
private val PillMinHeight = 48.dp

/** [MaterialTheme.typography.bodyMedium] is already Geist at 15sp — `Interface` itself is internal to `core:designsystem`, so every pill's own type reaches for it through this public role instead. */
@Composable
private fun pillTextStyle(fontSize: TextUnit = 15.sp) = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize)

/** An affirmative action a settings screen offers — "Change library", a field's "Save". Border and text in the accent. */
@Composable
internal fun LinePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Radius.control),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier =
            modifier.heightIn(min = PillMinHeight).let {
                if (contentDescription != null) it.semantics { this.contentDescription = contentDescription } else it
            },
    ) { Text(text, style = pillTextStyle()) }
}

/** An action that undoes something — "Sign out", a session's own row. Neutral border, figures text. */
@Composable
internal fun QuietPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
    contentDescription: String? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Radius.control),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        contentPadding = PaddingValues(horizontal = if (small) 14.dp else 20.dp),
        // Material's MinimumInteractiveComponentSize already grows a small
        // pill to a 48dp touch target on its own; only its drawn height shrinks.
        modifier =
            modifier.heightIn(min = if (small) 36.dp else PillMinHeight).let {
                if (contentDescription != null) it.semantics { this.contentDescription = contentDescription } else it
            },
    ) { Text(text, style = pillTextStyle(if (small) 13.sp else 15.sp)) }
}

/**
 * One choice in a row of them — Storage's Budget, a multi-volume Where —
 * at the mockup's own 48dp height (`round2/b-storage.html`'s `.segments
 * label`, taller than [DESIGN.md]'s own earlier "chip" token guess).
 */
@Composable
internal fun SettingsChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier.alpha(if (enabled) 1f else 0.4f)
                .heightIn(min = 48.dp)
                .border(
                    width = 1.dp,
                    color = if (selected) colors.primary else colors.outlineVariant,
                    shape = RoundedCornerShape(Radius.control),
                )
                .selectable(selected = selected, enabled = enabled, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = pillTextStyle(14.sp),
            color = if (selected) colors.onBackground else colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/**
 * A picture of a theme or an artwork mode: 16:9, 12dp corner — Shapes'
 * one deliberate exception to [Radius.control] — ringed in the accent, with
 * the page's own ground as the gap between the ring and the picture, when
 * selected. Purely presentational: the caller owns `selectable` and the
 * merge boundary, the same way an accent dot's own wrapping column does —
 * putting `selectable` on this nested box instead left it out of the
 * caller's merged semantics node entirely.
 */
@Composable
internal fun SwatchCard(
    selected: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.card),
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val ring =
        if (selected) {
            Modifier.border(2.dp, colors.primary, shape).padding(4.dp)
        } else {
            Modifier.border(1.dp, colors.outlineVariant, shape)
        }
    Box(modifier = modifier.aspectRatio(16f / 9f).then(ring)) {
        Box(modifier = Modifier.fillMaxSize().clip(shape), content = content)
    }
}

/**
 * A section's own columns, every multi-column section (Telegram, Storage,
 * System) arranges itself through this rather than repeating the branch.
 * On EXPANDED as many sit side by side as keep each at least
 * [ColumnMinWidth]; the rest move to the next row rather than every
 * column squeezing narrower — a ledger value or a device name wrapping in a
 * 190dp column reads worse than a second row. Stacked on compact/medium.
 */
@Composable
internal fun SettingsColumns(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    columns: List<@Composable () -> Unit>,
) {
    if (expanded) {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            val perRow = ((maxWidth + ColumnGap) / (ColumnMinWidth + ColumnGap)).toInt().coerceIn(1, columns.size.coerceAtLeast(1))
            // Rows of equal weights rather than a FlowRow of computed widths:
            // two widths that add up to the whole row exactly can still round
            // a pixel over it, and a FlowRow then drops the second column onto
            // a line of its own.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge)) {
                for (row in columns.chunked(perRow)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ColumnGap)) {
                        for (column in row) Box(modifier = Modifier.weight(1f)) { column() }
                        // A short last row keeps its columns the width of the ones above.
                        repeat(perRow - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge)) {
            for (column in columns) column()
        }
    }
}

/** The narrowest a settings column gets before the next one moves to a new line. */
private val ColumnMinWidth = 320.dp

/** Between side-by-side settings columns. */
private val ColumnGap = 40.dp
