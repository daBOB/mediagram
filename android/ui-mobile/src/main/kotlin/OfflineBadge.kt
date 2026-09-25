package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import designsystem.Spacing

/**
 * "offline" — the web's own wording (`set-badge.js`'s `offlineBadge`) for a
 * title this device holds in full: it plays with Telegram unreachable.
 * Callers only place this when their own `held` flag is true; there is no
 * `held` parameter here to check, so the same composable serves a card, a
 * search row and a list row alike.
 */
@Composable
internal fun OfflineBadge(modifier: Modifier = Modifier) {
    Text(
        text = "offline",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(BADGE_RADIUS))
            .padding(horizontal = Spacing.small, vertical = Spacing.extraSmall),
    )
}

private val BADGE_RADIUS = 4.dp
