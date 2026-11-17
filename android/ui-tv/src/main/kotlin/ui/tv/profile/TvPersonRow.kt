package ui.tv.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.initialsOf
import designsystem.Spacing
import designsystem.TvType
import model.HouseholdWords
import ui.tv.TvFocus
import ui.tv.rememberStableRequester

/**
 * One calm row on television's Manage profiles — the tablet's `PersonRow` for
 * a remote: an initial ([mark]), the [name] over its [line], and a › where OK
 * opens something ([onClick]). Focused, the name takes the accent and
 * underline every TV text row does ([TvFocus.textStyle]). With no [onClick] —
 * someone the manager may not change — it is no control, and the remote
 * passes over it, but it is still one thing said. A row with no line is an
 * action, Add someone's: no ›, as it opens a question rather than someone's
 * controls. [description] names the row, in place of its words, where they
 * alone would not tell it from its like.
 */
@Composable
internal fun TvPersonRow(
    name: String,
    line: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    mark: String = initialsOf(name).take(1),
    description: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = rememberStableRequester(focusRequester)
    val colors = MaterialTheme.colorScheme
    val ink = if (focused) colors.primary else colors.onSurfaceVariant
    val press =
        if (onClick == null) {
            Modifier.semantics(mergeDescendants = true) {}
        } else {
            Modifier
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .clickable(indication = null, interactionSource = null, onClick = onClick)
                .then(description?.let { Modifier.clearAndSetSemantics { contentDescription = it } } ?: Modifier)
        }
    Row(
        modifier = modifier.fillMaxWidth().then(press).padding(vertical = Spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Box(Modifier.size(MarkSize).clip(CircleShape).background(colors.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(mark, style = TvType.bodyMedium.copy(fontWeight = FontWeight.Medium), color = ink, modifier = Modifier.clearAndSetSemantics {})
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = TvFocus.textStyle(TvType.bodyMedium.copy(fontWeight = FontWeight.Medium), focused))
            line?.let { Text(it, style = TvType.bodySmall, color = colors.onSurfaceVariant) }
        }
        if (onClick != null && line != null) Text("›", style = TvType.titleLarge, color = ink, modifier = Modifier.clearAndSetSemantics {})
    }
}

/**
 * This device's quiet line — the web's `drawThisDevice`, the tablet's
 * `ThisDevice`: [text], the household this box shows, then Switch household,
 * which OK opens ([onSwitch]). One row as wide as the page, so Down reaches it
 * as every other row; focused, Switch household takes the accent.
 */
@Composable
internal fun TvDeviceRow(
    text: String,
    onSwitch: () -> Unit,
    modifier: Modifier,
    focusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .clickable(indication = null, interactionSource = null, onClick = onSwitch)
                .padding(vertical = Spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = TvType.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(HouseholdWords.SWITCH_HOUSEHOLD, style = TvFocus.textStyle(TvType.bodyMedium, focused))
    }
}

/** The initial's circle: a step under the tiles' avatar, as a row is under a tile. */
private val MarkSize = 32.dp
