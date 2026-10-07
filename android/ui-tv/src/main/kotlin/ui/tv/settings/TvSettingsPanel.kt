package ui.tv.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import setup.SettingsUiState
import setup.SettingsViewModel
import ui.tv.setup.TvApplicationScreen
import ui.tv.setup.TvLibraryChoiceScreen

/** A part of Settings that takes the whole screen while it is open. */
internal enum class TvSettingsPanel { Library, Application, LanAddress, LanToken }

/**
 * One of [TvSettingsPanel]'s four full-screen questions — [TvSettingsScreen]
 * composes this in place of [TvSettingsPanes] whenever a panel is open, and
 * [onClose] is what actually clears its own `panel` state back to `null`.
 */
@Composable
internal fun TvSettingsPanelScreen(
    panel: TvSettingsPanel,
    state: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    when (panel) {
        TvSettingsPanel.Library ->
            TvLibraryChoiceScreen(
                choices = state.choices,
                error = state.notice,
                // One install at a time; a second press waits for the first.
                onChoose = { handle -> if (!state.busy) settingsViewModel.chooseLibrary(handle) },
                onLookAgain = settingsViewModel::listLibraries,
            )

        TvSettingsPanel.Application ->
            if (state.profileReloadNeeded) {
                TvProfileReload(state, settingsViewModel::retryProfiles, takesFocus = true)
            } else {
                TvApplicationScreen(
                    error = state.notice,
                    onSubmit = settingsViewModel::changeApplication,
                    initialApiId = state.apiId?.toString().orEmpty(),
                )
            }

        TvSettingsPanel.LanAddress, TvSettingsPanel.LanToken ->
            TvLanCachePanel(panel = panel, onDone = onClose)
    }
}
