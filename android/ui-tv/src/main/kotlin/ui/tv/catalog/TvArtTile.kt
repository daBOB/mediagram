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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import designsystem.TvTypeScale
import java.io.File
import ui.catalog.ArtTileScrim
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
 * treatment. Sizes are the ten-foot step up from the phone's (a 24sp genre
 * name where the phone sets 21.6sp, the 16sp floor for the count), the same
 * step every other television card takes.
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
                        .padding(horizontal = if (destination) 22.dp else 18.dp, vertical = if (destination) 20.dp else 16.dp),
            ) {
                Text(
                    text = if (destination) name.uppercase() else name,
                    style =
                        if (destination) {
                            TvTypeScale.title.copy(fontSize = 28.sp, lineHeight = 1.05.em, letterSpacing = (-0.015).em)
                        } else {
                            TvTypeScale.title.copy(fontSize = 24.sp, lineHeight = 1.1.em, letterSpacing = (-0.01).em)
                        },
                    color = ink,
                    maxLines = if (destination) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = TvTypeScale.body.copy(fontSize = TvTypeScale.eyebrow, letterSpacing = if (destination) 0.em else 0.08.em),
                    color = ink.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = if (destination) 6.dp else 4.dp),
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
        modifier = modifier.heightIn(min = 52.dp),
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
        Box(modifier = Modifier.heightIn(min = 52.dp).padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
            Text(text = text, style = TvTypeScale.body)
        }
    }
}
