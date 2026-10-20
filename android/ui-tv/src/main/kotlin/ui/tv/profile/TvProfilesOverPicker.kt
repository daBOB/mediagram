package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import catalog.profile.DONE
import designsystem.Overscan
import designsystem.Spacing
import ui.common.settings.SettingsSection
import ui.tv.TvTextRow
import ui.tv.settings.TvProfilesPage
import ui.tv.settings.TvSettingsPanel

/**
 * The Profiles page with nobody chosen — everyone here and the Manage profiles
 * row, no You — under the page's own name, as the web's picker shows it, the
 * remote on the row the picker's own button named. Done and Back leave it for
 * the picker. Switch household takes the page's place, Back from it landing
 * on its row; once a household is joined it leaves for the picker, which
 * then shows that household, as the web's does.
 */
@Composable
internal fun TvProfilesOverPicker(
    onManage: () -> Unit,
    onDone: () -> Unit,
) {
    var panel by remember { mutableStateOf<TvSettingsPanel?>(null) }
    var from by remember { mutableStateOf(TvSettingsPanel.Profiles) }
    if (panel == TvSettingsPanel.Join) {
        BackHandler { panel = null }
        TvJoinHousehold(hiltViewModel()) { onDone() }
        return
    }
    BackHandler(onBack = onDone)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
    ) {
        Text(SettingsSection.PROFILES.title, style = WhoHeading, modifier = Modifier.padding(bottom = Spacing.large))
        TvProfilesPage(chosen = null, focusInContent = true, returningFrom = from, remember { FocusRequester() }) { row ->
            from = row
            if (row == TvSettingsPanel.Profiles) onManage() else panel = row
        }
        TvTextRow(DONE, onClick = onDone, modifier = Modifier.padding(top = Spacing.medium))
    }
}
