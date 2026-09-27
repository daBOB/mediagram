package ui.tv.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import designsystem.Overscan
import designsystem.Spacing
import setup.SettingsUiState
import setup.telegramRows
import ui.tv.TvTextRow
import ui.tv.catalog.TvQuietLine

/**
 * Settings › Telegram, television-side: the account ledger, the actions
 * that change it, and the account's active sessions — the phone's own
 * `ui.settings.TelegramSection`, stacked in one column rather than beside
 * Sessions, since a ten-foot page reads top to bottom.
 *
 * [entryFocusRequester] is Change library, this section's own first
 * control, whether landed on fresh (a Right or OK on the index's Telegram
 * row) or coming back from the library panel; the application id panel
 * keeps its own separate return stop.
 */
@Composable
internal fun TvTelegramSection(
    state: SettingsUiState,
    focusInContent: Boolean,
    returningFrom: TvSettingsPanel?,
    entryFocusRequester: FocusRequester,
    onChangeLibrary: () -> Unit,
    onChangeApplication: () -> Unit,
    onSignOut: () -> Unit,
    onRetryProfiles: () -> Unit,
    onLoadSessions: () -> Unit,
    onRevokeSession: (String) -> Unit,
) {
    val application = remember { FocusRequester() }
    // Keyed on focusInContent alone, not also on returningFrom: the hub
    // clears its own lastPanel back to null shortly after a panel closes
    // (so a later plain re-entry does not replay a stale panel's own
    // landing), which recomposes this section a second time with
    // returningFrom already null — keying on it too would run this a
    // second time and steal focus right back off the row it just landed on.
    LaunchedEffect(focusInContent) {
        if (returningFrom == TvSettingsPanel.Application) {
            application.requestFocus()
        } else if (focusInContent) {
            entryFocusRequester.requestFocus()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        TvInfoBlock(heading = "Telegram", rows = telegramRows(state))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            TvTextRow(text = "Change library", onClick = onChangeLibrary, focusRequester = entryFocusRequester, enabled = !state.busy)
            TvTextRow(text = "Application id and hash…", onClick = onChangeApplication, focusRequester = application, enabled = !state.busy)
            TvTextRow(text = "Sign out", onClick = onSignOut, enabled = !state.busy)
            if (state.profileReloadNeeded) {
                TvProfileReload(state, onRetryProfiles, takesFocus = false)
            } else {
                state.notice?.let { TvQuietLine(it) }
            }
        }
        TvSessionsBlock(state.sessions, state.sessionsError, onLoadSessions, onRevokeSession)
    }
}

/**
 * What a changed application identity leaves when its profiles could not
 * be read back: the notice, what to do, and the retry. [takesFocus] when it
 * is the whole panel, where nothing else could take the remote.
 */
@Composable
internal fun TvProfileReload(
    state: SettingsUiState,
    onRetry: () -> Unit,
    takesFocus: Boolean,
) {
    val retry = remember { FocusRequester() }
    if (takesFocus) LaunchedEffect(Unit) { retry.requestFocus() }
    Column(
        // Only when this is the whole screen, not a notice inside Telegram's
        // own already-padded section: the full-screen panel draws directly
        // over the frame a television may crop at its edge.
        modifier = if (takesFocus) Modifier.padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical) else Modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        state.notice?.let { TvQuietLine(it) }
        TvQuietLine(if (state.busy) "Loading profiles…" else "Reload profiles to continue.")
        TvTextRow(text = "Try again", onClick = onRetry, focusRequester = retry, enabled = !state.busy)
    }
}
