package ui.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.LocalCatalogueTones
import designsystem.Radius
import designsystem.Spacing
import designsystem.TvType.LedgerLabel
import designsystem.TvType.LedgerValue
import designsystem.TvType.SectionHead
import ui.tv.TvFocus

private val LedgerRowPadding = 10.dp

/** How far the focused block's fill reaches past its text, so the words never touch its edge. */
private val FocusFillBleed = Spacing.small

/**
 * A heading and its label/value rows — the phone's own ledger (`ui.components.Block`)
 * at a television's sizes: a quiet label left, its value in the page's own
 * ink right, divided from the row after it by a soft hairline, never a
 * monospace grid. A row whose value is null or empty is left out entirely,
 * as on the phone.
 *
 * [focusable] makes the whole block a stop for the remote, its heading
 * taking the focus treatment: a page of facts has nothing to press, but
 * the remote still has to rest somewhere, and stepping block by block is
 * what scrolls a page longer than the screen. While the remote is on it the
 * whole block, rows and all, takes the chosen settings row's fill
 * ([TvFocus.ChosenFillAlpha]), so the section being read stands out from the
 * couch and not only its heading. The fill is drawn past the block's edges
 * rather than padded into it, so focusing a block never shifts its text.
 */
@Composable
internal fun TvInfoBlock(
    heading: String,
    rows: List<Pair<String, String?>>,
    modifier: Modifier = Modifier,
    focusable: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val tones = LocalCatalogueTones.current
    val fill = MaterialTheme.colorScheme.onBackground.copy(alpha = TvFocus.ChosenFillAlpha)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                // Read as one, heading and rows together, wherever the remote rests on it.
                .let {
                    if (focusable) {
                        it
                            .onFocusChanged { state -> focused = state.isFocused }
                            .drawBehind {
                                if (!focused) return@drawBehind
                                val bleed = FocusFillBleed.toPx()
                                drawRoundRect(
                                    color = fill,
                                    topLeft = Offset(-bleed, -bleed),
                                    size = Size(size.width + 2 * bleed, size.height + 2 * bleed),
                                    cornerRadius = CornerRadius(Radius.control.toPx()),
                                )
                            }.focusable()
                            .semantics(mergeDescendants = true) {}
                    } else {
                        it
                    }
                },
    ) {
        Text(text = heading, style = TvFocus.textStyle(SectionHead, focused))
        Spacer(Modifier.height(Spacing.small))
        for ((label, value) in rows) {
            if (value.isNullOrEmpty()) continue
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = LedgerRowPadding),
                    // A real gap, and the label never shrinks to make room —
                    // only the value's own column gives, wrapping within
                    // itself rather than colliding with the label before it.
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    Text(text = label, style = LedgerLabel, color = tones.quiet)
                    Text(
                        text = value,
                        style = LedgerValue,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tones.ruleSoft))
            }
        }
    }
}
