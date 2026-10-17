package ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import model.ReportWords
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import designsystem.Spacing
import kotlin.math.roundToInt
import ui.chrome.CatalogueIconButton
import ui.chrome.CatalogueTextButton
import ui.common.player.SCRIM_ALPHA

/** The top gradient the web keeps behind its own bar: [SCRIM_ALPHA] black at the top edge, clear by the bottom of the bar. */
private val TOP_GRADIENT = Brush.verticalGradient(listOf(Color.Black.copy(alpha = SCRIM_ALPHA), Color.Transparent))

/**
 * The slim bar along the top: back, what is playing, and the controls the
 * web keeps in its own top bar — My List, Kids and Add to list ([marks]) and
 * Notes ([onNotes], when the open title has notes). Picture-in-picture rides
 * in the card instead. In watch together [label] names who is in the
 * session ("Together · TV, Tablet").
 *
 * The arrow stays up regardless of the card's fade — Android's own way
 * back, not something the web has. Everything else fades with [showTitle],
 * on the card's own clock ([HudFade]), and lets taps through to the picture
 * while it fades out. Beside the arrow, the title over a row of the marks and
 * Notes that wraps rather than squeezes on a narrow phone. Inset by the
 * status bar's band even while it is hidden, so the bar does not jump on
 * fullscreen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerTopBar(
    title: String,
    showTitle: Boolean,
    onBack: () -> Unit,
    onNotes: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    reportSent: Boolean = false,
    reportSending: Boolean = false,
    modifier: Modifier = Modifier,
    marks: @Composable () -> Unit = {},
    /** Where the bar ends in the root, so a menu knows how far up it may reach. */
    onBottom: (Int) -> Unit = {},
    label: String? = null,
    /** The episode list standing down the right: the bar runs under its header, so nothing may sit at its right end. */
    sidebarBeside: Boolean = false,
) {
    // Read when drawn, so the scrim's fade redraws the bar and recomposes nothing.
    val scrim by animateFloatAsState(if (showTitle) 1f else 0f, HudFade, label = "top scrim")
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind { drawRect(TOP_GRADIENT, alpha = scrim) }
                .onGloballyPositioned { onBottom(it.boundsInRoot().bottom.roundToInt()) }
                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
        verticalAlignment = Alignment.Top,
    ) {
        CatalogueIconButton(onClick = onBack, modifier = Modifier.padding(Spacing.small)) {
            Text(text = "←", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
        AnimatedVisibility(
            visible = showTitle,
            modifier = Modifier.weight(1f),
            enter = fadeIn(HudFade),
            exit = fadeOut(HudFade),
            label = "top bar",
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(top = Spacing.medium, end = Spacing.small)) {
                    TitleAndLabel(title, label, roomBeside = !sidebarBeside)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small), itemVerticalAlignment = Alignment.CenterVertically) {
                        marks()
                        if (onNotes != null) NotesButton(onNotes)
                        if (onReport != null) ReportButton(onReport, enabled = !reportSent && !reportSending, sent = reportSent)
                    }
                }
                if (!showTitle) TapsReachThePicture()
            }
        }
    }
}

/*
 * The web's top-bar buttons are `.ghost`: on hover the border goes to the stage
 * accent and the text to `--stage-ink`, both over `--hover`
 * (`playback.css:97-99`). These have no outline and their text already rests at
 * full ink, so the text takes the accent instead: a known look difference.
 */
@Composable
private fun NotesButton(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    CatalogueTextButton(onClick = onClick, interactionSource = source) {
        Text(text = "Notes", color = toolInk(source, enabled = true, rest = Color.White))
    }
}

@Composable
private fun ReportButton(onClick: () -> Unit, enabled: Boolean, sent: Boolean) {
    val source = remember { MutableInteractionSource() }
    val words = if (sent) ReportWords.SENT else ReportWords.BUTTON
    CatalogueTextButton(onClick = onClick, enabled = enabled, interactionSource = source, modifier = Modifier.semantics { contentDescription = words }) {
        Text(text = words, color = toolInk(source, enabled = enabled, rest = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)))
    }
}

/** Where the web's bar sets the label beside the title rather than under it: its 768px breakpoint. */
private val LABEL_BESIDE_FROM = 768.dp

/**
 * The title, and watch together's [label] — the web's `.together-label`:
 * beside the title on a wide window, each with half the room, and on its own
 * line under it on a narrow one, and under it too while the episode list
 * stands beside the picture ([roomBeside] false), as the web's goes beside
 * its sidebar.
 */
@Composable
private fun TitleAndLabel(
    title: String,
    label: String?,
    roomBeside: Boolean,
) {
    val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= LABEL_BESIDE_FROM
    if (label != null && wide && roomBeside) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            if (title.isNotEmpty()) TitleText(title, Modifier.weight(1f))
            TogetherLabel(label, Modifier.weight(1f), TextAlign.End)
        }
    } else {
        if (title.isNotEmpty()) TitleText(title)
        label?.let { TogetherLabel(it) }
    }
}

@Composable
private fun TitleText(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        color = Color.White,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Set in capitals, as the web's `text-transform` sets it; a screen reader hears it as written. */
@Composable
private fun TogetherLabel(
    label: String,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = label.uppercase(),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
        modifier = modifier.semantics { contentDescription = label },
    )
}
