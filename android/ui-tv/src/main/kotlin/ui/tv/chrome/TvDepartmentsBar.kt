package ui.tv.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import com.mediagram.android.core.designsystem.R
import designsystem.Overscan
import designsystem.tvDp
import ui.tv.profile.TvChosenProfile

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
    val ink = barInk(blend)
    val downModifier = Modifier.focusProperties { down = downTarget }
    val requesterOf = rememberPillRequesters(pills, selected, selectedPillFocus)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TvDepartmentsBarHeight + Overscan.vertical)
                .background(color = barBackground(blend))
                .padding(top = Overscan.vertical, start = TvContentGutter - PillRingRoom, end = TvContentGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Filling, so search and the avatar stand at the bar's far end, as the
        // web's `.library-toolbar { margin-left: auto }` does (`shell.css:91`).
        // The start padding above gives the pill row's ring room back, so the pills sit where they did.
        TvPillRow(pills, selected, onSelect, ink, downTarget, requesterOf, modifier = Modifier.weight(1f))
        // `.library-toolbar` gap 16 (`shell.css:91`).
        Row(horizontalArrangement = Arrangement.spacedBy(16.tvDp), verticalAlignment = Alignment.CenterVertically) {
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
