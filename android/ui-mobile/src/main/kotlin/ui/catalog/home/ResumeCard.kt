package ui.catalog.home

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import catalog.SetCard
import catalog.factsLine
import catalog.initialsOf
import coil3.compose.AsyncImage
import designsystem.hoverMotion
import model.Kind
import model.episodeLabel
import ui.common.catalog.rememberRowState
import java.io.File

/** The web's `--progress` — a fixed accent for a resume card's own bar, distinct from the theme's accent (`theme.css:102`). */
private val ProgressBlue = Color(0xFF6FB7E8)

/** The Continue band's own scrolling row of cards — `.resume-strip` (`home.css:214-223`). */
@Composable
internal fun ResumeRow(
    cards: List<SetCard>,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Rests with a card at its start, as the web's strip does (`home.css:220,234`); other rows coast.
    val state = rememberRowState(cards.map { it.set.setId })
    LazyRow(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        items(items = cards, key = { it.set.setId }) { card ->
            ResumeCard(card = card, onClick = { onPlay(card.set.setId) })
        }
    }
}

@Composable
private fun ResumeCard(
    card: SetCard,
    onClick: () -> Unit,
) {
    val set = card.set
    val episode = set.kind != Kind.MOVIE && set.show != null
    val name = if (episode) requireNotNull(set.show) else set.title
    val art = set.backdropPath ?: set.posterPath
    val source = remember { MutableInteractionSource() }

    Box(
        modifier =
            Modifier
                .width(208.dp)
                .aspectRatio(16f / 8.4f)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
    ) {
        if (art != null) {
            // `.resume-card:hover .resume-art img` zooms to 1.04 over 1200ms (`home.css:245-246`), inside the clip.
            AsyncImage(
                model = File(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().hoverMotion(source, durationMs = 1200, zoom = 1.04f),
            )
        } else {
            Text(
                text = initialsOf(name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(modifier = Modifier.matchParentSize().background(Brush.verticalGradient(0.4f to Color(0x00080809), 1f to Color(0xE6080809))))
        // Not drawn — a caption naming the picture is what this card's own
        // doc says it deliberately leaves out — but still said, for
        // TalkBack: a viewer who cannot see the progress bar still needs
        // "1h left" or "Next up" to know where this card picks up.
        if (card.caption.isNotEmpty()) {
            Spacer(modifier = Modifier.size(0.dp).semantics { contentDescription = card.caption })
        }

        Column(modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp).padding(top = 26.dp, bottom = 10.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
                color = OnImage, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub =
                if (episode) {
                    listOfNotNull(episodeLabel(set).ifEmpty { null }, set.title).joinToString(" · ")
                } else {
                    // The web's own "1995 · 2h 4m" — a bare year, the way
                    // this once read, drops the runtime the web keeps.
                    factsLine(set.year, set.durationSecs) ?: ""
                }
            if (sub.isNotEmpty()) {
                Text(
                    text = sub, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = OnImage2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 88.dp).padding(top = 3.dp),
                )
            }
        }
        card.progress?.let { progress ->
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 16.dp)
                        .fillMaxWidth(0.52f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x38F6F2EA)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(ProgressBlue),
                )
            }
        }
    }
}
