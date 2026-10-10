package ui.catalog

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import designsystem.catalogueIndication
import designsystem.hoverMotion

/**
 * A round outline link set on the page itself, in ink — the web's
 * `.dept-all` ("All N films →" at the foot of Movies) and, with [large]
 * off, `.dept-row .make` ("＋ New list" under Your lists), both in
 * `departments.css`. Fully round like the departments bar's pills, not the
 * 6dp settings pill: these are the catalogue's own way onward, not a
 * setting.
 */
@Composable
internal fun PagePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                // `.dept-all` moves 3px toward the whole shelf on hover, as a whole
                // (`departments.css:81,83`); `.make` has no motion (`:107`).
                .then(if (large) Modifier.hoverMotion(source, durationMs = 520, nudge = 3.dp) else Modifier)
                .heightIn(min = if (large) 52.dp else 44.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(
                    interactionSource = source,
                    // `.dept-all` is a link and doesn't shrink; `.make` is a button and does.
                    indication = if (large) catalogueIndication(pressScale = 1f) else LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = if (large) 28.dp else 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            // Geist, as every control here is — a bare `Text` would fall back
            // to the reading face.
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (large) 15.sp else 14.sp,
                    fontWeight = if (large) FontWeight.Medium else FontWeight.Normal,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
