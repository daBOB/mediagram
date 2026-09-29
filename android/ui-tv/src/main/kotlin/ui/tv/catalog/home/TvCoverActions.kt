package ui.tv.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.tv.TvFocus
import ui.tv.TvTextRow

/**
 * The cover's own stops, in the web's own order — Watch now, + My List,
 * Details, then one dot per film — the television twin of the phone
 * `CoverSlide`'s action row and `CoverPager`. Drawn once, outside
 * [TvHomeCover]'s own `Crossfade`, and bound to whichever film is on
 * screen: no control here ever fades with the picture, so the remote is
 * never left resting on a control for a film that has already faded out.
 *
 * No pause button, unlike the phone's `CoverPager`: focus resting anywhere
 * on the cover already holds its rotation ([TvHomeCover]'s own doc), so a
 * toggle here could only ever be pressed while rotation is already held —
 * a control with nothing left for it to do. A deliberate, documented
 * difference from the phone, not a silent gap.
 *
 * [upExit] is where Up leads from every stop in this row — explicit on
 * each control, the same pattern the departments bar's own pills carry
 * their `down` target on ([ui.tv.chrome.TvDepartmentsBar]'s own
 * `downModifier`), rather than depending on the chrome's own content-region
 * `onExit` to catch it: this row is the page's own first, so a plain
 * geometric search for "whatever sits above" can land on a pill that is
 * not the selected one, once a viewer has scrolled the bar sideways — the
 * bar's own contract (proven for Back already) is that leaving content
 * upward always lands on the *selected* pill, wherever it now sits.
 */
@Composable
internal fun TvCoverActions(
    title: String,
    watchlisted: Boolean,
    dotCount: Int,
    current: Int,
    onPlay: () -> Unit,
    onToggleWatchlist: (Boolean) -> Unit,
    onDetails: () -> Unit,
    onFocusDot: (Int) -> Unit,
    watchNowFocus: FocusRequester,
    upExit: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val up = Modifier.focusProperties { up = upExit }
    Row(
        modifier = modifier.padding(start = Spacing.extraLarge, bottom = Spacing.extraLarge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        TvCoverPill(
            text = "▶  Watch now",
            description = "Watch now: $title",
            filled = true,
            onClick = onPlay,
            modifier = up.focusRequester(watchNowFocus),
        )
        TvCoverPill(
            text = if (watchlisted) "✓ My List" else "+ My List",
            description = if (watchlisted) "Remove from My List" else "Add to My List",
            filled = false,
            onClick = { onToggleWatchlist(!watchlisted) },
            modifier = up,
        )
        TvTextRow(text = "Details", onClick = onDetails, modifier = up)
        if (dotCount > 1) {
            Row(modifier = Modifier.padding(start = Spacing.small), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                for (index in 0 until dotCount) {
                    TvCoverDot(selected = index == current, index = index, count = dotCount, onFocused = { onFocusDot(index) }, modifier = up)
                }
            }
        }
    }
}

@Composable
private fun TvCoverPill(
    text: String,
    description: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = if (filled) Color(0xFF0A0A0B) else OnImage
    Surface(
        onClick = onClick,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        shape = TvFocus.surfaceShape(TvFocus.PillShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (filled) OnImage else Color(0x47080809),
                contentColor = ink,
                focusedContainerColor = if (filled) OnImage else Color(0x47080809),
                focusedContentColor = ink,
                pressedContainerColor = if (filled) OnImage else Color(0x47080809),
                pressedContentColor = ink,
            ),
        scale = TvFocus.surfaceScale(),
        border = TvFocus.surfaceBorder(shape = TvFocus.PillShape),
        glow = TvFocus.surfaceGlow(),
    ) {
        Box(modifier = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.small)) {
            Text(text = text, style = TvTypeScale.body.copy(color = ink))
        }
    }
}

/**
 * One film's own bar in the cover's pager — a focused dot shows its film
 * (focus-selects, the Settings-index rule [ui.tv.system.TvSettingsIndex]
 * already draws for a section row): [onFocused] alone drives the picture
 * shown, before OK is ever pressed. A [Surface], like every other stop
 * here, so the same accent ring reads across the whole action row — OK
 * still does nothing more than focusing already did, so [onClick] is empty
 * rather than repeating [onFocused].
 */
@Composable
private fun TvCoverDot(
    selected: Boolean,
    index: Int,
    count: Int,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = {},
        modifier =
            modifier
                .size(32.dp)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .semantics {
                    contentDescription = "Cover story ${index + 1} of $count"
                    this.selected = selected
                },
        shape = TvFocus.surfaceShape(TvFocus.PillShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        scale = TvFocus.surfaceScale(),
        border = TvFocus.surfaceBorder(shape = TvFocus.PillShape),
        glow = TvFocus.surfaceGlow(),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .size(width = 20.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) OnImage else Color(0x52F6F2EA)),
            )
        }
    }
}
