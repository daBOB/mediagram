package ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import catalog.TogetherOffer
import catalog.title
import designsystem.Spacing
import model.together.barLine
import ui.chrome.CatalogueTextButton
import ui.common.FrameKind
import ui.common.player.TransportIcon
import ui.common.player.TransportIcons

/** The bar's height, so the page above it and the notices over it can leave room. */
internal val CAST_BAR_HEIGHT = 56.dp

/**
 * The cast to show a bar for: one the TV plays, on a title this profile may
 * see (the offer is already `null` for any other), while the remote is not
 * open, as the web's `cast-bar.js`. The player fills the window and has its
 * own card, so the bar steps aside there too.
 */
internal fun castBarOffer(
    offer: TogetherOffer?,
    top: FrameKind?,
): TogetherOffer? = offer?.takeIf { it.cast && top != FrameKind.REMOTE && top != FrameKind.PLAYER }

/**
 * "Playing on TV · Title" with a play/pause, at the foot of the catalog
 * while a cast plays. The words open the remote; the button moves the
 * session where it stands, without joining it. Drawn on the catalog's raised
 * surface, as the web's bar is, so it reads on the light theme as well.
 */
@Composable
internal fun CastBar(
    offer: TogetherOffer?,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (offer == null) return
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier.fillMaxWidth(), color = colors.surface, contentColor = colors.onSurface) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = colors.outlineVariant)
            Row(
                modifier = Modifier.heightIn(min = CAST_BAR_HEIGHT).padding(start = Spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Text(
                    text = barLine(offer.title),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clickable(onClick = onOpen).padding(vertical = Spacing.medium),
                )
                PlayPause(offer.playing, onPlayPause)
            }
        }
    }
}

@Composable
private fun PlayPause(
    playing: Boolean,
    onClick: () -> Unit,
) {
    CatalogueTextButton(
        onClick = onClick,
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = if (playing) "Pause" else "Play" },
    ) {
        TransportIcon(
            icon = if (playing) TransportIcons.Pause else TransportIcons.Play,
            tint = MaterialTheme.colorScheme.onSurface,
            size = MaterialTheme.typography.headlineMedium.fontSize,
        )
    }
}
