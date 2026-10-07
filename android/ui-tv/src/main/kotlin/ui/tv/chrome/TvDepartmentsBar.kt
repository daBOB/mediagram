package ui.tv.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.MaterialTheme
import com.mediagram.android.core.designsystem.R
import designsystem.Overscan
import designsystem.Spacing
import ui.tv.profile.TvChosenProfile

/** One pill's own title and count, bundled so a caller cannot hand [TvPill] one out of step with the other. */
internal data class TvDepartmentPill(val title: String, val count: Int?)

/** The bar's own ink over a hero — unused while nothing calls this with `blend < 1`; see [blend]'s own doc below. */
private val TvOverCoverInk = Color(0xFFF6F2EA)

/**
 * The departments pill bar across the top of [TvLibraryChrome]: Home, the
 * shelves, Collections — [pills], the web's `nav.departments` in this
 * catalogue's ten-foot form — then search, the viewer's own avatar and the
 * trimmed menu's ⋮. Drawn opaque over the content below it, the tablet's
 * own `DepartmentsBar` (`ui-mobile/.../ChromeControls.kt`) without its
 * translucent-over-a-hero opening state: Home has no cover on this surface
 * yet to bleed under it, so every caller passes [blend] as `1f` for now —
 * the parameter stays so whoever draws that cover only has to pass a real
 * value here rather than add one.
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
 * [selectedPillFocus], [searchFocus] and [menuFocus] are how a caller sends
 * the remote back to one specific control here — a restore key naming
 * "search was opened from here", or simply "whichever pill is current" —
 * the same reason [TvIndexRow] takes one for a rail row.
 */
@Composable
internal fun TvDepartmentsBar(
    pills: List<TvDepartmentPill>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    profile: TvChosenProfile,
    onMenu: () -> Unit,
    downTarget: FocusRequester,
    selectedPillFocus: FocusRequester,
    searchFocus: FocusRequester,
    menuFocus: FocusRequester,
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

    fun requesterOf(index: Int): FocusRequester = if (index == selected) selectedPillFocus else pillRequesters.getOrPut(pills[index].title) { FocusRequester() }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TvDepartmentsBarHeight + Overscan.vertical)
                .background(color = bg)
                .padding(top = Overscan.vertical, start = Spacing.medium, end = Overscan.horizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
        ) {
            pills.forEachIndexed { index, pill ->
                key(pill.title) {
                    // One requester on every pill, every recomposition: [ui.tv.rememberStableRequester]'s KDoc says why.
                    val pillModifier = downModifier.focusRequester(requesterOf(index))
                    TvPill(title = pill.title, count = pill.count, active = index == selected, ink = ink, onClick = { onSelect(index) }, modifier = pillModifier)
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
            TvAvatar(profile = profile, modifier = downModifier)
            TvRoundIconButton(
                icon = painterResource(R.drawable.core_designsystem_ic_menu_more), description = "Menu", ink = ink, onClick = onMenu,
                modifier = downModifier.focusRequester(menuFocus),
            )
        }
    }
}

/** The bar's own translucent opening state over a hero — unused while every caller passes `blend = 1f`; see its own doc above. */
private val TvBarOverCoverBg = Color(0x590A0A0B)
