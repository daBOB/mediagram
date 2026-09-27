package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import designsystem.LedgerLabel
import designsystem.LocalCatalogueTones
import designsystem.LedgerValue
import designsystem.SectionHead
import designsystem.Spacing

/**
 * One [Block] row: a label, its value, the fraction to draw as a thin
 * accent meter instead of the plain hairline beneath it (Storage's own
 * Held row), and — a single-volume Where row's own case — a click the row
 * answers even though it draws no picker of its own; [selected] marks it
 * `selectable` (rather than merely `clickable`) when that click stands for
 * a choice already made, so a test — and a screen reader — can tell the
 * two apart. [held] leads the value with the sage dot and colour a live,
 * good-news connection earns — Storage's own "Connected to …" — the same
 * mark a session row's "This device" already carries.
 */
internal data class LedgerEntry(
    val label: String,
    val value: String?,
    val meterFraction: Float? = null,
    val onClick: (() -> Unit)? = null,
    val selected: Boolean? = null,
    val held: Boolean = false,
)

private val LedgerRowPadding = 13.dp
private val LedgerRowMinHeight = 48.dp

/** A settings/system ledger without a meter row — the common case, all 7 of [Block]'s other callers. */
@Composable
internal fun Block(
    heading: String? = null,
    rows: List<Pair<String, String?>>,
    modifier: Modifier = Modifier,
) = Block(heading, rows.map { (label, value) -> LedgerEntry(label, value) }, modifier)

/**
 * A settings/system screen's own table: an optional heading, then a quiet
 * label left and a value right, tabular, each divided by a soft rule
 * beneath it — the approved mockup's own `.ledger` (`round2/tokens.css:92-97`).
 * A row whose value is null or empty is left out entirely, as `Block`
 * always has.
 */
@JvmName("blockOfEntries")
@Composable
internal fun Block(
    heading: String?,
    rows: List<LedgerEntry>,
    modifier: Modifier = Modifier,
) {
    val tones = LocalCatalogueTones.current
    Column(modifier = modifier) {
        heading?.let {
            Text(text = it, style = SectionHead, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = Spacing.medium))
        }
        for (entry in rows) {
            if (entry.value.isNullOrEmpty()) continue
            Column {
                val rowModifier =
                    when {
                        entry.selected != null ->
                            Modifier.selectable(selected = entry.selected, onClick = entry.onClick ?: {}, role = Role.RadioButton)

                        entry.onClick != null -> Modifier.clickable(onClick = entry.onClick)
                        else -> Modifier
                    }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = LedgerRowMinHeight)
                            .then(rowModifier)
                            .padding(vertical = LedgerRowPadding),
                    // A real gap, and the label never shrinks to make room —
                    // the mockup's own `dt{flex:none}` — only the value's own
                    // column (`dd{min-width:0}`) gives, wrapping within itself
                    // rather than colliding with the label before it.
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    Text(text = entry.label, style = LedgerLabel, color = tones.quiet)
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (entry.held) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                        }
                        Text(
                            text = entry.value,
                            style = LedgerValue,
                            color = if (entry.held) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                    }
                }
                val fraction = entry.meterFraction?.coerceIn(0f, 1f)
                if (fraction == null) {
                    HorizontalDivider(color = tones.ruleSoft)
                } else {
                    Row(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                        if (fraction > 0f) Box(Modifier.weight(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                        if (fraction < 1f) Box(Modifier.weight(1f - fraction).fillMaxHeight().background(tones.ruleSoft))
                    }
                }
            }
        }
    }
}
