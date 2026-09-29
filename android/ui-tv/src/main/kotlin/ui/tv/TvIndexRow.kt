package ui.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.LocalCatalogueTones
import designsystem.Palette
import designsystem.Radius
import designsystem.Spacing
import designsystem.TvTypeScale

/**
 * One row of an index — an icon, a label, and whatever [status] or
 * [trailing] figure names it further — shared by the Settings pane's own
 * section list and the library rail, so the two draw the same treatment
 * (icon tint, selected fill, [TvFocus] underline) rather than each
 * inventing its own. Settings never collapses; the rail does, which is
 * [expanded]'s only reader here — `false` drops the label, [status] and
 * [trailing] and centres the icon alone in whatever width the caller gives
 * the row, the rail's own icons-only form. [contentDescription] is what a
 * collapsed row still says to a screen reader with no visible label to read
 * instead; expanded, the visible [label] already carries that.
 *
 * [onFocusChanged] is Settings' own reason to exist on this row rather than
 * only on [onSelect]: a row gaining focus alone swaps the pane shown beside
 * it, before Right or OK ever steps the remote into it. The rail has
 * nothing to react to on mere focus, so it leaves this at its default.
 */
@Composable
internal fun TvIndexRow(
    icon: Painter,
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    minHeight: Dp = 44.dp,
    trailing: String? = null,
    status: (@Composable () -> Unit)? = null,
    onFocusChanged: (FocusState) -> Unit = {},
    contentDescription: String? = null,
) {
    val tones = LocalCatalogueTones.current
    var focused by remember { mutableStateOf(false) }
    val baseColor = if (selected) MaterialTheme.colorScheme.onBackground else tones.quiet
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    focused = state.isFocused
                    onFocusChanged(state)
                }
                .clip(RoundedCornerShape(Radius.control))
                .then(if (selected) Modifier.background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)) else Modifier)
                .selectable(selected = selected, role = Role.Tab, onClick = onSelect)
                // Merged, and only while collapsed: expanded already reads as
                // one row through its own visible label, and merging then too
                // would fold a settings row's status line into the same
                // announcement, which nothing here asked for.
                .then(
                    if (!expanded && contentDescription != null) {
                        Modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = Spacing.small, vertical = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = if (expanded) Alignment.Top else Alignment.CenterVertically,
    ) {
        Image(
            painter = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (focused) Palette.Imprint else baseColor),
            modifier = Modifier.size(22.dp),
        )
        if (expanded) {
            Column {
                Text(text = label, style = TvFocus.textStyle(TvTypeScale.body.copy(color = baseColor), focused), maxLines = 1, overflow = TextOverflow.Ellipsis)
                status?.invoke()
            }
            trailing?.let {
                Text(text = it, style = TvTypeScale.body.copy(fontSize = TvTypeScale.eyebrow), color = tones.quiet)
            }
        }
    }
}
