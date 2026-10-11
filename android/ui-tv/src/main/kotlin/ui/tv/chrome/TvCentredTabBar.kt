package ui.tv.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.mediagram.android.core.designsystem.R
import designsystem.Overscan
import designsystem.tvDp
import ui.tv.TvFocus
import ui.tv.profile.TvChosenProfile

/**
 * Apple TV style's bar: search, the pills and the avatar in one dark
 * translucent pill centred over the page, where mediagram has the pills at
 * the start and the rail beside them. No blur behind it — blur measured too
 * slow on the box. The band it sits in fades from translucent over a hero to
 * the page's ground ([barBackground]), as every bar style's does, so a page
 * scrolled under it never shows through.
 */
@Composable
internal fun TvCentredTabBar(
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
    val requesterOf = rememberPillRequesters(pills, selected, selectedPillFocus)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TvDepartmentsBarHeight + Overscan.vertical)
                .background(barBackground(blend))
                .padding(top = Overscan.vertical, start = Overscan.horizontal, end = Overscan.horizontal),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.background(TabBarFill, TvFocus.PillShape).padding(horizontal = 6.tvDp, vertical = 4.tvDp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvRoundIconButton(
                icon = painterResource(R.drawable.core_designsystem_ic_search),
                description = "Search",
                ink = ink,
                onClick = onSearch,
                modifier =
                    Modifier
                        .focusProperties {
                            down = downTarget
                            if (pills.isNotEmpty()) right = requesterOf(0)
                        }.focusRequester(searchFocus),
            )
            TvPillRow(pills, selected, onSelect, ink, downTarget, requesterOf, modifier = Modifier.weight(1f, fill = false))
            TvAvatar(
                profile = profile,
                onClick = onMenu,
                modifier =
                    Modifier
                        .focusProperties {
                            down = downTarget
                            // By name, not by where it is drawn: a pill scrolled out of view is no candidate for a geometric search.
                            if (pills.isNotEmpty()) left = requesterOf(pills.lastIndex)
                        }.focusRequester(avatarFocus),
            )
        }
    }
}

/** The tab bar's own pill: Apple's dark translucent material, without the blur. */
private val TabBarFill = Color(0xB31C1C1E)
