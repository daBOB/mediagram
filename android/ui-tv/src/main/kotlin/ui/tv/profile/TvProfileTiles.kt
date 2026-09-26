package ui.tv.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.initialsOf
import designsystem.Spacing
import designsystem.TvTypeScale
import model.Profile
import ui.tv.TvFocus

private const val NEW_PROFILE = "New profile"

/** A tile's width when there is room for it; [profileTileWidth] narrows it towards [MinTileWidth] to fit more. */
private val TileWidth = 180.dp

/** As narrow as a tile goes — "New profile" still reads in full on one line. */
private val MinTileWidth = 140.dp

/**
 * How wide each of [count] tiles is drawn so the whole row fits [room] with
 * [gap] between them — every profile in view at once, as the phone's picker
 * shows them all. The row's own width at most, and never narrower than
 * [MinTileWidth]: past that many profiles the row scrolls with the remote
 * instead, rather than every name being squeezed to an ellipsis.
 */
internal fun profileTileWidth(
    count: Int,
    room: Dp,
    gap: Dp,
): Dp {
    if (count <= 0) return TileWidth
    val fit = (room - gap * (count - 1)) / count
    return fit.coerceIn(MinTileWidth, TileWidth)
}

// Fixed, not derived from content: a kids tile carries one more line ("KIDS")
// than a plain one, and a row of `Card`s sized only by their own content
// grows that one tile taller than its neighbours — a ragged row a fixed
// height (both tiles centring their content inside it) closes off for good.
private val TileHeight = 220.dp
private val AvatarSize = 88.dp

@Composable
internal fun TvProfileTile(
    profile: Profile,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    tag: String,
    width: Dp = TileWidth,
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .width(width)
                .height(TileHeight)
                .testTag(tag)
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        shape = TvFocus.cardShape(),
        scale = TvFocus.cardScale(),
        border = TvFocus.cardBorder(),
        glow = TvFocus.cardGlow(),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.medium),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The shared, two-letter `initialsOf` poster art already falls back
            // to — not the phone dialog's own single-letter `initialOf` — so a
            // second surface never reimplements what one function already
            // covers. The two only ever visibly differ on a two-word name.
            TvProfileAvatar(letters = initialsOf(profile.name))
            Text(profile.name, style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small))
            if (profile.kids) {
                Text(
                    // The phone's own tile label, unabridged — see
                    // `ui.profile.ProfilePickerScreen.ProfileTile`.
                    "KIDS",
                    style = TvTypeScale.body,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.extraSmall),
                )
            }
        }
    }
}

@Composable
internal fun TvAddTile(
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    tag: String,
    width: Dp = TileWidth,
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .width(width)
                .height(TileHeight)
                .testTag(tag)
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        shape = TvFocus.cardShape(),
        scale = TvFocus.cardScale(),
        border = TvFocus.cardBorder(),
        glow = TvFocus.cardGlow(),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.medium),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A literal glyph, not a title to derive letters from — `initialsOf`
            // drops anything that is not a letter or digit, which would turn
            // this into "?" instead of the "+" the phone itself draws.
            TvProfileAvatar(letters = "+")
            Text(NEW_PROFILE, style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small))
        }
    }
}

@Composable
private fun TvProfileAvatar(letters: String) {
    Box(
        modifier =
            Modifier
                .size(AvatarSize)
                .clip(CircleShape)
                // The tile's own card explicitly takes `surface` (see
                // `TvProfileTile`/`TvAddTile` above), leaving `surfaceVariant`
                // here as a genuinely different tone rather than the two
                // resolving to the same value — tv-material's own default
                // card container is `surfaceVariant` too, which is what made
                // this circle disappear into its card before.
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(letters, style = TvTypeScale.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
