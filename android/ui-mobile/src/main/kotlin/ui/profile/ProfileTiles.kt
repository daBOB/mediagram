package ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import catalog.profile.kidsTag
import designsystem.Spacing
import model.Profile

/** A profile tile's tag, for tests: Who's watching? and Who's managing? draw the same tiles. */
internal const val ProfileTileTag = "profile-tile"

/**
 * A profile's avatar tile — the web's `profile-tiles.js`, which Who's
 * watching? and Who's managing? both draw, so the two questions look alike:
 * a big initial, the name, and under it [line] — a kid's FSK on the picker,
 * Owner on Who's managing?.
 */
@Composable
internal fun ProfileTile(
    profile: Profile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    line: String? = profile.kidsTag,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(ProfileTileTag).clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Initial(profile.name)
        Text(profile.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.small))
        line?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
    }
}

/** The letter on a tile, and on a person's row: drawn, not read out — the name beside it is. */
@Composable
internal fun Initial(
    name: String,
    size: Dp = 64.dp,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    ink: Color = Color.Unspecified,
) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(size).clearAndSetSemantics {}) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(initialOf(name), style = style, color = ink)
        }
    }
}

private fun initialOf(name: String): String =
    name
        .trim()
        .take(1)
        .uppercase()
        .ifEmpty { "?" }
