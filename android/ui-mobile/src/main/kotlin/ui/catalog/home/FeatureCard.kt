package ui.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import catalog.Feature
import catalog.label
import coil3.compose.AsyncImage
import designsystem.Eyebrow
import designsystem.Radius
import designsystem.hoverMotion
import model.Kind
import ui.chrome.linkClickable
import java.io.File

@Composable
internal fun FeatureCard(
    feature: Feature,
    index: Int,
    width: Dp,
    modifier: Modifier,
    onOpenTitle: (String) -> Unit,
) {
    val set = feature.set
    val series = set.kind == Kind.EPISODE && set.show != null
    val title = if (series) requireNotNull(set.show) else set.title
    val art = set.backdropPath ?: set.posterPath
    val posterOnly = set.backdropPath == null
    // The second card keeps its own case and runs a size larger — `home.css:179-180`.
    val secondCard = index == 1
    val source = remember { MutableInteractionSource() }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radius.card))
                .background(Color(0xFF141416))
                // A feature is a link (`home-features.js:41`), and links don't shrink.
                .linkClickable(source) { onOpenTitle(set.setId) },
    ) {
        if (art != null) {
            AsyncImage(
                model = File(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // A poster fallback crops toward its own top third rather
                // than a backdrop's own centre-right focus — `.poster-crop`
                // (`home.css:159`).
                alignment = if (posterOnly) Alignment.TopCenter else Alignment.CenterEnd,
                // `matchParentSize()`, not `fillMaxSize()`: this never
                // *asks* for height (that would fight the text below for
                // it, the way it once did) — it only ever matches whatever
                // the card's own text already resolved the box to.
                // `.feature:hover .feature-image` zooms to 1.045 over 1600ms (`home.css:157,160`),
                // inside the card's clip, for a hovering mouse or stylus only.
                modifier = Modifier.matchParentSize().hoverMotion(source, durationMs = 1600, zoom = 1.045f),
            )
        }
        Box(
            modifier =
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(0f to Color(0xE0080809), 0.48f to Color(0x85080809), 1f to Color(0x14080809)),
                ),
        )
        Box(modifier = Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color(0x00080809), 1f to Color(0xB3080809))))

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .widthIn(max = 416.dp)
                    .padding(vertical = 36.dp, horizontal = fluid(24f, 0.026f, 40f, width.value).dp),
        ) {
            Text(text = feature.kind.label.uppercase(), style = Eyebrow, color = OnImage2, modifier = Modifier.padding(bottom = 18.dp))
            val titleSize = if (secondCard) fluid(32f, 0.03f, 49.6f, width.value) else fluid(28.8f, 0.028f, 46.4f, width.value)
            Text(
                text = if (secondCard) title else title.uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = titleSize.sp,
                    lineHeight = 0.98.em,
                    letterSpacing = (-0.025).em,
                ),
                color = OnImage,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Box(modifier = Modifier.padding(top = 20.dp, bottom = 16.dp).width(32.dp).height(1.dp).background(Color(0x8CF6F2EA)))
            val deck = set.tagline?.ifEmpty { null } ?: set.genres.take(3).joinToString(", ").ifEmpty { null }
            if (deck != null) {
                Text(
                    text = deck,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 1.4.em),
                    color = OnImage2,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 384.dp),
                )
            }
        }
    }
}
