package ui.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.EVERYONE_HERE
import catalog.profile.MANAGE_PROFILES
import catalog.profile.ManageProfilesViewModel
import catalog.profile.everyoneHere
import designsystem.Spacing
import ui.common.settings.ProfilesVisit
import ui.tv.TvTextRow
import ui.tv.profile.TvManageScreen

/**
 * Settings › Profiles, television-side — the web Settings page's Profiles
 * tab (`settings-profiles.js`): everyone in the household at a glance, each
 * name beside its role, then Manage profiles. Seeing the list needs no PIN;
 * changing anything asks who you are and that grown-up's PIN.
 *
 * Where the phone draws Manage inline under the list, the television opens
 * it as a panel of its own ([TvProfilesPanel]) from the "Manage profiles" row:
 * its PIN pad and its name questions take the whole screen here wherever they
 * open, and one screen at a time keeps nothing behind them competing for the
 * remote. That row is [entryFocusRequester]'s target, so the remote lands on
 * it entering the section and coming back from the panel; the list above is
 * one stop of its own, for a household long enough to scroll.
 */
@Composable
internal fun TvProfilesSection(
    focusInContent: Boolean,
    entryFocusRequester: FocusRequester,
    onManage: () -> Unit,
) {
    val profiles by hiltViewModel<ManageProfilesViewModel>().profiles.collectAsStateWithLifecycle()
    LaunchedEffect(focusInContent) { if (focusInContent) entryFocusRequester.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        TvInfoBlock(
            heading = EVERYONE_HERE,
            rows = everyoneHere(profiles).map { (profile, line) -> profile.name to line },
            focusable = true,
        )
        TvTextRow(MANAGE_PROFILES, onClick = onManage, focusRequester = entryFocusRequester)
    }
}

/**
 * The picker's own Manage profiles, taking the whole screen as one visit
 * ([ProfilesVisit]): "Who are you?" on the way in and again on the way back
 * from the background, the PIN dropped on the way out. Done and Back close
 * the panel, and the panel closing is what ends the visit.
 */
@Composable
internal fun TvProfilesPanel(onDone: () -> Unit) {
    ProfilesVisit(open = true)
    TvManageScreen(hiltViewModel(), onClose = onDone)
}
