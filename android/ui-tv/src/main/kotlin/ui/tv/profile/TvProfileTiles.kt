package ui.tv.profile

import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import designsystem.Overscan
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.initialsOf
import catalog.profile.kidsTag
import designsystem.Spacing
import designsystem.TvType
import designsystem.tvSp
import model.Profile
import ui.tv.TvFocus
import ui.tv.rememberStableRequester

/** A tile's width when there is room for it; [profileTileWidth] narrows it towards [MinTileWidth] to fit more. */
private val TileWidth = 180.dp

/** As narrow as a tile goes: a short name still reads on one line, and a kid's "Kids · FSK 12" within the tile. */
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

// Fixed, not derived from content: a kids tile carries one more line (its limit)
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
    line: String? = profile.kidsTag,
) {
    TileCard(onClick, focusRequester, tag, width) {
        // The shared, two-letter `initialsOf` poster art already falls back
        // to — not the phone dialog's own single-letter `initialOf` — so a
        // second surface never reimplements what one function already
        // covers. The two only ever visibly differ on a two-word name.
        TvProfileAvatar(letters = initialsOf(profile.name))
        Text(profile.name, style = TvType.bodyMedium.copy(fontSize = 14.tvSp), modifier = Modifier.padding(top = Spacing.small))
        // The web's tile line: a kid's own limit, never a fixed one — or Owner, on Who's managing?.
        line?.let {
            Text(it, style = TvType.bodyMedium.copy(fontSize = 12.tvSp), color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = Spacing.extraSmall))
        }
    }
}

/**
 * [profiles]' tiles in one row the D-pad moves across — Who's watching?'s and
 * Who's managing?'s alike. A LazyRow, not a plain Row: in a Row, enough
 * fixed-width tiles to run past the safe width squeeze a later one down to
 * nothing rather than scroll to it. contentPadding carries the Overscan
 * margin without clipping a focused edge tile's growth. The tiles narrow to
 * fit the safe width first, so a household's profiles are all in view at
 * once, as on the phone; only past the narrowest does the row scroll. Centred:
 * with few enough tiles that the row does not scroll, they still read as one
 * group rather than pinned to the left edge. [focused]'s tile takes
 * [requester]; [state] keeps the row's scroll for whoever outlives the row.
 */
@Composable
internal fun TvProfileTileRow(
    profiles: List<Profile>,
    state: LazyListState,
    focused: String?,
    requester: FocusRequester,
    tagOf: (index: Int, profile: Profile) -> String,
    onChoose: (String) -> Unit,
    lineOf: (Profile) -> String? = { it.kidsTag },
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = Spacing.large)) {
        val tileWidth = profileTileWidth(profiles.size, maxWidth - Overscan.horizontal * 2, Spacing.medium)
        LazyRow(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Overscan.horizontal),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium, Alignment.CenterHorizontally),
        ) {
            itemsIndexed(items = profiles, key = { _, profile -> profile.id }) { index, profile ->
                TvProfileTile(profile, { onChoose(profile.id) }, requester.takeIf { focused == profile.id }, tagOf(index, profile), tileWidth, lineOf(profile))
            }
        }
    }
}

/** The focusable, fixed-height card a tile is drawn on. */
@Composable
private fun TileCard(
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    tag: String,
    width: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .width(width)
                .height(TileHeight)
                .testTag(tag)
                .focusRequester(rememberStableRequester(focusRequester)),
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
            content = content,
        )
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
                // `TileCard` above), leaving `surfaceVariant`
                // here as a genuinely different tone rather than the two
                // resolving to the same value — tv-material's own default
                // card container is `surfaceVariant` too, which is what made
                // this circle disappear into its card before.
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(letters, style = TvType.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 32.tvSp, lineHeight = 1.em), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** `.who-card h1` (`library-controls.css:4`): Geist 600, `clamp(1.75rem, 4vw, 2.5rem)` at 1280px, its 40 ceiling — Who's watching and Manage profiles. */
internal val WhoHeading: TextStyle @Composable @ReadOnlyComposable get() = TvType.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 40.tvSp, lineHeight = 1.2.em, letterSpacing = (-0.035).em)

/** `.who-note` (`library-controls.css:17`): what the picker and its PIN say beside the tiles. */
internal val WhoNote: TextStyle @Composable @ReadOnlyComposable get() = TvType.bodyMedium.copy(fontSize = 14.tvSp)
