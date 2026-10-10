package ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import catalog.profileInitial
import designsystem.StatusDot
import designsystem.catalogueIndication

/**
 * A department pill's own count. Bundled with the title rather than kept as
 * a parallel list, so a caller can never hand [Pill] a title and a count one
 * entry out of step with each other.
 */
data class DepartmentPill(val title: String, val count: Int?)

/**
 * The round 44dp icon button both bars draw their search control from — the
 * web's own `.search-field` sizing (`shell.css:98-101`). [selected] is
 * `null` for a plain button (Search); a non-null value is one of the
 * compact header's own rail rows drawn as an icon, reported the same way
 * [Pill] and the rail's own rows are — `selectable`/`Role.Tab`, not a bare
 * click with nothing but its colour saying which one is current.
 */
@Composable
internal fun CircleIconButton(
    icon: Int,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    dot: String? = null,
) {
    // Never a `button` on the web, so neither kind shrinks (`theme.css:167`): the
    // rail's links drawn as icons (`index.html:55-65`) and the search field (`:89`).
    val noShrink = catalogueIndication(pressScale = 1f)
    val target =
        if (selected != null) {
            Modifier.selectable(selected = selected, interactionSource = null, indication = noShrink, role = Role.Tab, onClick = onClick)
        } else {
            Modifier.clickable(interactionSource = null, indication = noShrink, onClick = onClick)
        }
    Box(
        modifier =
            modifier
                .size(44.dp)
                .clip(CircleShape)
                .then(target)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
        // The icon's top-right corner: centred, then out by the 21 dp icon's half.
        dot?.let { StatusDot(MaterialTheme.colorScheme.tertiary, Modifier.offset(x = 9.dp, y = (-9).dp), description = it) }
    }
}

/**
 * The viewer as an initial in a circle — the web's `.who-button`. Always the
 * theme's own ink-on-paper, never the bar's own blended colour: the web
 * leaves it out of the `[data-cover]` override too (`shell.css:230-246`).
 */
@Composable
internal fun ChromeAvatar(
    profile: ProfileBarState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground)
                .clickable(onClick = profile.onChoose)
                .semantics { contentDescription = "Who's watching: ${profile.name}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = profileInitial(profile.name),
            color = MaterialTheme.colorScheme.background,
            fontFamily = MaterialTheme.typography.labelLarge.fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
        )
    }
}

/** A department pill: fully round, the active one filled at ink@12%, the count dimmer than the name — the web's `.departments a` (`shell.css:100-101`). */
@Composable
internal fun Pill(
    pill: DepartmentPill,
    active: Boolean,
    ink: Color,
    horizontalPadding: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .heightIn(min = 44.dp)
                .clip(CircleShape)
                .then(if (active) Modifier.background(ink.copy(alpha = 0.12f)) else Modifier)
                // A link on the web (`index.html:76-82`), and links don't shrink when pressed.
                .selectable(selected = active, interactionSource = null, indication = catalogueIndication(pressScale = 1f), role = Role.Tab, onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 10.dp, horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(pill.title, style = MaterialTheme.typography.labelLarge, color = ink)
            pill.count?.let {
                Text(it.toString(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = ink.copy(alpha = 0.4f))
            }
        }
    }
}
