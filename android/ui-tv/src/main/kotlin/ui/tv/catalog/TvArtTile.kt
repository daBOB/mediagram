package ui.tv.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import designsystem.TvType
import designsystem.tvDp
import designsystem.tvSp
import java.io.File
import ui.common.catalog.ArtTileScrim
import ui.tv.TvFocus
import ui.tv.catalog.home.OnImage

/**
 * A picture card that names what it opens across its own art — the
 * television twin of the phone's `ArtTile`, after the web's `.genre-tile`
 * and `.destination`: a genre, a franchise or a list, whose art is borrowed
 * from one member, so the name has to sit on the face rather than under it
 * the way a plate's does. With no art the tile is the sunk ground with the
 * name in ink, as the web's `:not(:has(img))` rule draws it.
 *
 * A `Card` at [TvFocus.FeatureCardShape] — the rounded picture-card corner
 * Home's feature cards already take — with this surface's one focus
 * treatment. Sizes are the web's px through the television's scale, as on
 * every other television card.
 */
@Composable
internal fun TvArtTile(
    name: String,
    meta: String,
    art: String?,
    aspectRatio: Float,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    destination: Boolean = false,
) {
    val ink = if (art != null) OnImage else MaterialTheme.colorScheme.onSurface
    Card(
        onClick = onOpen,
        modifier = modifier.aspectRatio(aspectRatio).semantics(mergeDescendants = true) {},
        shape = TvFocus.cardShape(TvFocus.FeatureCardShape),
        scale = TvFocus.cardScale(),
        border = TvFocus.cardBorder(TvFocus.FeatureCardShape),
        glow = TvFocus.cardGlow(),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (art != null) {
                AsyncImage(model = File(art), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                ArtTileScrim(destination, Modifier.matchParentSize())
            }
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        // `.destination` 20 by 22, `.genre-tile` 16 by 18 (`departments.css:98`, `catalog.css:140`).
                        .padding(horizontal = if (destination) 22.tvDp else 18.tvDp, vertical = if (destination) 20.tvDp else 16.tvDp),
            ) {
                Text(
                    text = if (destination) name.uppercase() else name,
                    style =
                        if (destination) {
                            TvType.headlineSmall.copy(fontSize = 25.6.tvSp, lineHeight = 1.05.em, letterSpacing = (-0.015).em)
                        } else {
                            TvType.headlineSmall.copy(fontSize = 21.6.tvSp, lineHeight = 1.1.em, letterSpacing = (-0.01).em)
                        },
                    color = ink,
                    maxLines = if (destination) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = if (destination) TvType.labelMedium else TvType.labelMedium.copy(fontSize = 12.tvSp, letterSpacing = 0.08.em),
                    color = ink.copy(alpha = 0.8f),
                    // `.destination-meta` 6, `.genre-tile-count` 4 (`departments.css:105`, `catalog.css:147`).
                    modifier = Modifier.padding(top = if (destination) 6.tvDp else 4.tvDp),
                )
            }
        }
    }
}

/**
 * A round outline link set on the page itself — the web's `.dept-row
 * .make` ("＋ New list" under Your lists) and `.dept-all` ("All N films →"
 * at the foot of Movies), the phone's `PagePill`: the rule
 * colour at rest, this surface's accent ring and scale once the remote is
 * on it. [modifier] carries the caller's own `focusRequester`.
 */
@Composable
internal fun TvPagePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = TvFocus.PillShape
    val ink = MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        // `.dept-all`: at least 52 tall, 28 in at either end (`departments.css:72-74`).
        modifier = modifier.heightIn(min = 52.tvDp),
        shape = TvFocus.surfaceShape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = ink,
                focusedContainerColor = Color.Transparent,
                focusedContentColor = ink,
                pressedContainerColor = Color.Transparent,
                pressedContentColor = ink,
            ),
        scale = TvFocus.surfaceScale(),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, MaterialTheme.colorScheme.borderVariant), shape = shape),
                focusedBorder = Border(BorderStroke(TvFocus.BorderWidth, MaterialTheme.colorScheme.primary), shape = shape),
            ),
        glow = TvFocus.surfaceGlow(),
    ) {
        Box(modifier = Modifier.heightIn(min = 52.tvDp).padding(horizontal = 28.tvDp), contentAlignment = Alignment.Center) {
            Text(text = text, style = TvType.bodyMedium.copy(fontWeight = FontWeight.Medium))
        }
    }
}
