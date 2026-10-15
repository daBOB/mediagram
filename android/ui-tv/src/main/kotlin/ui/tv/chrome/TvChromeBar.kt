package ui.tv.chrome

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import designsystem.LocalTvStyle
import designsystem.TvStyle
import dev.chrisbanes.haze.HazeState
import ui.common.RailItem
import ui.tv.profile.TvChosenProfile

/** The bar the current style draws over the page; [modifier] reaches whichever it is, so its focus is tracked once. */
@Composable
internal fun TvChromeBar(
    pills: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    profile: TvChosenProfile,
    onMenu: () -> Unit,
    onRailSelect: (RailItem) -> Unit,
    focus: TvChromeFocus,
    modifier: Modifier,
    blend: Float,
    frost: HazeState?,
) {
    when (LocalTvStyle.current) {
        TvStyle.MEDIAGRAM ->
            TvDepartmentsBar(
                pills = pills,
                selected = selected,
                onSelect = onSelect,
                onSearch = onSearch,
                profile = profile,
                onMenu = onMenu,
                downTarget = focus.contentFocus,
                selectedPillFocus = focus.selectedPillFocus,
                searchFocus = focus.searchFocus,
                avatarFocus = focus.avatarFocus,
                blend = blend,
                frost = frost,
                modifier = modifier,
            )
        TvStyle.APPLE ->
            TvCentredTabBar(
                pills = pills,
                selected = selected,
                onSelect = onSelect,
                onSearch = onSearch,
                profile = profile,
                onMenu = onMenu,
                downTarget = focus.contentFocus,
                selectedPillFocus = focus.selectedPillFocus,
                searchFocus = focus.searchFocus,
                avatarFocus = focus.avatarFocus,
                blend = blend,
                modifier = modifier,
            )
        TvStyle.GOOGLE ->
            TvTopBar(
                pills = pills,
                selected = selected,
                onSelect = onSelect,
                onSearch = onSearch,
                profile = profile,
                onMenu = onMenu,
                onSettings = { onRailSelect(RailItem.SETTINGS) },
                downTarget = focus.contentFocus,
                selectedPillFocus = focus.selectedPillFocus,
                searchFocus = focus.searchFocus,
                avatarFocus = focus.avatarFocus,
                settingsFocus = focus.settingsFocus,
                blend = blend,
                modifier = modifier,
            )
    }
}
