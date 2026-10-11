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
import ui.common.RailItem
import ui.tv.profile.TvChosenProfile

/**
 * Google TV style's bar: the viewer's avatar and search at the start, the
 * pills, and a gear at the end that opens Settings — that home screen's own
 * order. The rest of the rail's rows are in the avatar's menu.
 */
@Composable
internal fun TvTopBar(
    pills: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    profile: TvChosenProfile,
    onMenu: () -> Unit,
    onSettings: () -> Unit,
    downTarget: FocusRequester,
    selectedPillFocus: FocusRequester,
    searchFocus: FocusRequester,
    avatarFocus: FocusRequester,
    settingsFocus: FocusRequester,
    modifier: Modifier = Modifier,
    blend: Float = 1f,
) {
    val ink = barInk(blend)
    val requesterOf = rememberPillRequesters(pills, selected, selectedPillFocus)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TvDepartmentsBarHeight + Overscan.vertical)
                .background(barBackground(blend))
                .padding(top = Overscan.vertical, start = Overscan.horizontal, end = Overscan.horizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.tvDp),
    ) {
        TvAvatar(profile = profile, onClick = onMenu, modifier = Modifier.focusProperties { down = downTarget }.focusRequester(avatarFocus))
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
        TvPillRow(pills, selected, onSelect, ink, downTarget, requesterOf, modifier = Modifier.weight(1f))
        TvRoundIconButton(
            icon = painterResource(RailItem.SETTINGS.icon),
            description = RailItem.SETTINGS.label,
            ink = ink,
            onClick = onSettings,
            modifier =
                Modifier
                    .focusProperties {
                        down = downTarget
                        if (pills.isNotEmpty()) left = requesterOf(pills.lastIndex)
                    }.focusRequester(settingsFocus),
        )
    }
}
