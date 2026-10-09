package ui.tv.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.MaterialTheme
import com.mediagram.android.core.designsystem.R
import designsystem.Overscan
import designsystem.Spacing
import ui.tv.profile.TvChosenProfile

/** The bar's own ink over a hero, while [blend] is below `1f`. */
private val TvOverCoverInk = Color(0xFFF6F2EA)

/**
 * The departments pill bar across the top of [TvLibraryChrome]: Home, the
 * shelves, Collections — [pills], the web's `nav.departments` at the web's
 * own sizes, without the counts the web sets beside them (the rail's footer
 * carries the totals) — then search and the viewer's own avatar, which
 * opens [onMenu]: the trimmed menu, with the profile switch in it, where the
 * tablet has a ⋮ beside the avatar. Both are this television's own, written
 * down in `docs/system-architecture.md`; without them seven departments,
 * search, the avatar and ⋮ ran past the box's 960dp. The tablet's own
 * `DepartmentsBar` (`ui-mobile/.../ChromeControls.kt`) in tv-material: [blend] runs from
 * translucent over a hero (`0f`) to opaque (`1f`), and comes from
 * `rememberTvCatalogBlend` — Home's cover and the department heroes bleed
 * under the bar, every other tab draws it opaque.
 *
 * [selected] is `-1` on a kept wall (My List/Continue): the rail
 * chose it directly, and no pill in this row is the current one.
 *
 * [downTarget] is where Down from any control on this row leads —
 * [TvLibraryChrome]'s own content box, so a pill press that (deliberately)
 * leaves the remote on the pill still has somewhere for Down to take it
 * into the page it just switched to. Set per control below rather than once
 * on this whole row: [androidx.compose.ui.focus.FocusProperties] set on a
 * container is not inherited by its focusable children the way layout or
 * draw modifiers are, so a caller that set it only here would find Down
 * doing nothing from any actual pill.
 *
 * Should the pills still run past the row — a longer department name, a
 * larger font scale — the row scrolls, and [fadeClippedEdges] fades only an
 * end that clips something, so a cut pill reads as more to come.
 *
 * [selectedPillFocus], [searchFocus] and [avatarFocus] are how a caller sends
 * the remote back to one specific control here — a restore key naming
 * "search was opened from here", or simply "whichever pill is current" —
 * the same reason [TvIndexRow] takes one for a rail row.
 */
@Composable
internal fun TvDepartmentsBar(
    pills: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    profile: TvChosenProfile,
    onMenu: () -> Unit,
    downTarget: FocusRequester,
    selectedPillFocus: FocusRequester,
    searchFocus: FocusRequester,
    avatarFocus: FocusRequester,
    modifier: Modifier = Modifier,
    blend: Float = 1f,
) {
    val solidBg = MaterialTheme.colorScheme.background
    val solidInk = MaterialTheme.colorScheme.onBackground
    val bg = lerp(TvBarOverCoverBg, solidBg, blend)
    val ink = lerp(TvOverCoverInk, solidInk, blend)
    val downModifier = Modifier.focusProperties { down = downTarget }
    // Each pill's own requester, held here rather than inside the pill so Search can name the last one.
    val pillRequesters = remember { HashMap<String, FocusRequester>() }

    fun requesterOf(index: Int): FocusRequester = if (index == selected) selectedPillFocus else pillRequesters.getOrPut(pills[index]) { FocusRequester() }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TvDepartmentsBarHeight + Overscan.vertical)
                .background(color = bg)
                .padding(top = Overscan.vertical, start = Spacing.medium - PillRingRoom, end = Overscan.horizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Filling, so search and the avatar stand at the bar's far end, as the
        // web's `.library-toolbar { margin-left: auto }` does (`shell.css:91`).
        val scrollWidth = Modifier.weight(1f)
        // A focused pill grows past its own bounds and the scroll clips at its
        // edges, so the row's ends are padded by [PillRingRoom] for the first
        // and last pill's ring; the start padding above gives that room back,
        // so the pills sit where they did. Padded above and below by the same,
        // too: the fade's offscreen layer clips to the row, where the scroll
        // alone let the ring spill over. A pill the row scrolls to between
        // its ends stops clear of the fade by its ring room as well, since
        // its grow and ring reach past its own bounds.
        val scroll = rememberScrollState()
        val fades by remember(scroll) { derivedStateOf { EdgeFades(start = scroll.canScrollBackward, end = scroll.canScrollForward) } }
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRoomOnBothSides(EdgeFadeWidth + PillRingRoom)) {
        Row(
            modifier = scrollWidth.fadeClippedEdges(fades).horizontalScroll(scroll).padding(PillRingRoom),
            horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
        ) {
            pills.forEachIndexed { index, pill ->
                key(pill) {
                    // One requester on every pill, every recomposition: [ui.tv.rememberStableRequester]'s KDoc says why.
                    val pillModifier = downModifier.focusRequester(requesterOf(index))
                    TvPill(title = pill, active = index == selected, ink = ink, onClick = { onSelect(index) }, modifier = pillModifier)
                }
            }
        }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall), verticalAlignment = Alignment.CenterVertically) {
            // Left goes to the last pill by name, not by where it is drawn: scrolled out of the
            // row's view, Tutorials and Collections were no candidates for a geometric search,
            // which skipped them for the nearest pill still in sight. The pill taking the remote
            // scrolls the row to it.
            val lastPill = pills.lastIndex
            TvRoundIconButton(
                icon = painterResource(R.drawable.core_designsystem_ic_search), description = "Search", ink = ink, onClick = onSearch,
                modifier =
                    Modifier
                        .focusProperties {
                            down = downTarget
                            if (lastPill >= 0) left = requesterOf(lastPill)
                        }.focusRequester(searchFocus),
            )
            TvAvatar(profile = profile, onClick = onMenu, modifier = downModifier.focusRequester(avatarFocus))
        }
    }
}

/**
 * Clear room kept at the row's ends for a focused pill's [ui.tv.TvFocus.Scale]
 * growth, 4% of its width on each side — under 8 dp for the widest pill at
 * the box's density.
 */
private val PillRingRoom = Spacing.small

/** Brings an item into view as if it were [roomPx] wider on each side, so nothing it draws past its bounds lands on a clipped edge. */
private class RoomOnBothSidesBringIntoViewSpec(
    private val roomPx: Float,
    private val fallback: BringIntoViewSpec,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float = fallback.calculateScrollDistance(offset - roomPx, size + 2 * roomPx, containerSize)
}

@Composable
private fun rememberRoomOnBothSides(room: Dp): BringIntoViewSpec {
    val density = LocalDensity.current
    val fallback = LocalBringIntoViewSpec.current
    return remember(density, fallback, room) { RoomOnBothSidesBringIntoViewSpec(with(density) { room.toPx() }, fallback) }
}

/** The bar's own translucent background over a hero, while [blend] is below `1f`. */
private val TvBarOverCoverBg = Color(0x590A0A0B)
