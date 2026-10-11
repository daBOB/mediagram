package ui.tv.catalog.home

import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.SetCard
import catalog.factsLine
import catalog.initialsOf
import coil3.compose.AsyncImage
import designsystem.TvType
import designsystem.tvDp
import designsystem.tvSp
import java.io.File
import model.Kind
import model.episodeLabel
import ui.tv.TvFocus
import ui.tv.catalog.DisplaySemiBold
import ui.tv.catalog.TvOfflineBadge
import ui.tv.rememberStableRequester

/** The card itself, for a test to measure its caption against. */
internal const val TvResumeCardTag = "tv-resume-card"

/** The card's progress bar. */
internal const val TvResumeProgressTag = "tv-resume-progress"

/** The web's `--progress` — the phone's own resume card reuses the same fixed accent rather than the theme's own. */
private val ProgressBlue = Color(0xFF6FB7E8)

/** ~240dp wide, 16:8.4 — the phone's own resume card shape, at TV's fixed width rather than a fluid one. */
private val ResumeCardWidth = 240.dp
private const val ResumeCardAspect = 16f / 8.4f

/**
 * One card on the Continue band's own strip — the television twin of the
 * phone's `ResumeCard`, landscape rather than [ui.tv.catalog.TvPlate]'s
 * poster crop: a resume card is cut from the same backdrop the cover uses,
 * and squeezing it to 2:3 would both crop most of the picture and, several
 * of these across a 960dp screen, leave less room for the rest of Home than
 * the poster shape needs.
 */
@Composable
internal fun TvResumeCard(
    card: SetCard,
    onOpen: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val set = card.set
    val episode = set.kind != Kind.MOVIE && set.show != null
    val name = if (episode) requireNotNull(set.show) else set.title
    val art = set.backdropPath ?: set.posterPath
    Card(
        onClick = onOpen,
        modifier =
            modifier
                .width(ResumeCardWidth)
                .testTag(TvResumeCardTag)
                .semantics(mergeDescendants = true) {}
                .focusRequester(rememberStableRequester(focusRequester)),
        shape = TvFocus.cardShape(),
        scale = TvFocus.cardScale(),
        border = TvFocus.cardBorder(),
        glow = TvFocus.cardGlow(),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(ResumeCardAspect)) {
            if (art != null) {
                AsyncImage(model = File(art), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            } else {
                Text(text = initialsOf(name), style = ResumeInitial, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
            }
            // Not drawn as its own line — the phone's own `ResumeCard` never
            // shows a caption at all, and the tablet's own doc says it holds
            // that same line back on purpose ("1h left"/"Next up" would
            // repeat what the progress bar already draws). TV keeps the
            // words themselves, but only for TalkBack, on the same zero-size
            // node the phone's own card uses.
            if (card.caption.isNotEmpty()) {
                Spacer(modifier = Modifier.size(0.dp).semantics { contentDescription = card.caption })
            }
            val sub =
                if (episode) {
                    listOfNotNull(episodeLabel(set).ifEmpty { null }, set.title).joinToString(" · ")
                } else {
                    factsLine(set.year, set.durationSecs) ?: ""
                }
            TvResumeCaption(name = name, sub = sub, progress = card.progress, held = card.held, modifier = Modifier.align(Alignment.BottomStart))
        }
    }
}

/**
 * `.resume-text` (`home.css:260-273`): the caption over the picture's foot,
 * on a scrim of its own height only. The episode line is capped at 42% of
 * the caption and the progress bar sits at the far end of that same line,
 * 52% of the card wide, so the two never meet; the offline badge takes a
 * line of its own below, never the bar's.
 */
@Composable
private fun TvResumeCaption(
    name: String,
    sub: String,
    progress: Float?,
    held: Boolean,
    modifier: Modifier = Modifier,
) {
    // `.resume-text`: padded 26 12 10, a gap of 3 (`home.css:265-267`).
    Column(
        modifier = modifier.fillMaxWidth().background(CaptionScrim).padding(start = 12.tvDp, end = 12.tvDp, top = 26.tvDp, bottom = 10.tvDp),
        verticalArrangement = Arrangement.spacedBy(3.tvDp),
    ) {
        Text(text = name, style = ResumeName, color = OnImage, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub.isNotEmpty() || progress != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sub.isNotEmpty()) {
                    Text(text = sub, style = ResumeSub, color = OnImage2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(SubShare))
                }
                Spacer(Modifier.weight(1f))
                progress?.let { TvResumeBar(it) }
            }
        }
        // Not part of the phone's own `ResumeCard` — this app's
        // cache-or-stream choice is TV's own, not the tablet's,
        // reason to add it: the badge already told a viewer this on
        // every other plate Continue carried before this band
        // replaced Home's own plain row, and pulling it here would
        // have been a silent loss of something a viewer relied on
        // to know before pressing Watch now.
        if (held) TvOfflineBadge()
    }
}

/** `.resume-art .watched` (`home.css:249-259`). */
@Composable
private fun TvResumeBar(progress: Float) {
    Box(modifier = Modifier.testTag(TvResumeProgressTag).width(ResumeCardWidth * BarShare).height(3.dp).background(Color(0x38F6F2EA), RoundedCornerShape(2.dp))) {
        Box(modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(ProgressBlue, RoundedCornerShape(2.dp)))
    }
}

/** `.resume-sub{max-width:42%}` and `.watched{width:52%}`: the shares of the line that keep the two apart. */
private const val SubShare = 0.42f
private const val BarShare = 0.52f

/** The caption block's own scrim, 0.9 at its foot to nothing at its top. */
private val CaptionScrim = Brush.verticalGradient(0f to Color(0x00080809), 0.4f to Color(0x99080809), 1f to Color(0xE6080809))

private val ResumeName: TextStyle @Composable @ReadOnlyComposable get() = TvType.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 14.tvSp)
private val ResumeSub: TextStyle @Composable @ReadOnlyComposable get() = TvType.bodyMedium.copy(fontSize = 12.tvSp)

/** `.resume-initial`: Fraunces 600 at 2rem. */
private val ResumeInitial: TextStyle @Composable @ReadOnlyComposable get() = DisplaySemiBold.copy(fontSize = 32.tvSp)
