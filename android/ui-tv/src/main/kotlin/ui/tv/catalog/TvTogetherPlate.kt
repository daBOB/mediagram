package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.line
import designsystem.Spacing
import designsystem.TvType
import designsystem.tvSp
import ui.common.catalog.TogetherJoin
import ui.tv.TvFocus

/**
 * Home's watch-together band: while a session this profile may join is live,
 * one plate reading "Watching together: Title · TV, Tablet · Join" — the web's
 * `.together-join` line, made a stop the remote can land on.
 *
 * [focus] sits on the band, not the plate, so it is there whether or not a
 * plate is: a requester that comes and goes with the plate would reset the
 * focus target it names. A session that ends while the remote is on the
 * plate keeps the plate drawn until the remote has moved on, down the page
 * or else up to the cover, since a focused plate simply removed drops the
 * remote on the bar.
 */
@Composable
internal fun TvTogetherBand(
    together: TogetherJoin?,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
    plate: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf(together) }
    SideEffect { if (together != null) last = together }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(together == null, focused) {
        if (together != null || !focused) return@LaunchedEffect
        if (!focusManager.moveFocus(FocusDirection.Down)) focusManager.moveFocus(FocusDirection.Up)
    }
    Box(modifier.focusRequester(focus).onFocusChanged { focused = it.hasFocus }) {
        // Kept for the remote after its session ended, the plate joins nothing.
        (together ?: last?.takeIf { focused })?.let { shown ->
            TvTogetherPlate(shown, plate, onJoin = together?.join ?: {})
        }
    }
}

@Composable
private fun TvTogetherPlate(
    together: TogetherJoin,
    modifier: Modifier,
    onJoin: () -> Unit,
) {
    // `.together-join` (`together.css:62-70`): 0.8125rem.
    val style = TvType.bodyMedium.copy(fontSize = 13.tvSp)
    val color = MaterialTheme.colorScheme.error
    Card(
        onClick = onJoin,
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = TvFocus.cardShape(),
        scale = TvFocus.cardScale(),
        border = TvFocus.cardBorder(),
        glow = TvFocus.cardGlow(),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = together.offer.line,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = "Join",
                style = style.copy(fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline),
                color = color,
            )
        }
    }
}
