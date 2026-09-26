package ui.tv.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import setup.SettingsUiState
import setup.telegramRows
import ui.tv.TvTextRow
import ui.tv.catalog.TvPage
import ui.tv.catalog.TvQuietLine

/**
 * Settings' own page: the Telegram block, the three things that change it,
 * the cache and where it lives. The remote lands on Change library, or on the row whose
 * panel was just left. Waiting on an answer draws the actions faint rather
 * than hiding them: what is on offer does not change, only when.
 */
@Composable
internal fun TvSettingsRows(
    state: SettingsUiState,
    returningFrom: TvSettingsPanel?,
    onChangeLibrary: () -> Unit,
    onChangeApplication: () -> Unit,
    onSignOut: () -> Unit,
    onRetryProfiles: () -> Unit,
) {
    val library = remember { FocusRequester() }
    val application = remember { FocusRequester() }
    LaunchedEffect(Unit) { (if (returningFrom == TvSettingsPanel.Application) application else library).requestFocus() }

    // Arriving on the first row leaves the heading where it is rather than
    // scrolling it up into the overscan edge — the rule every catalogue page keeps.
    TvPage {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
            Text(text = "Settings", style = TvTypeScale.title)
            TvInfoBlock(heading = "Telegram", rows = telegramRows(state))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                TvTextRow(text = "Change library", onClick = onChangeLibrary, focusRequester = library, enabled = !state.busy)
                TvTextRow(text = "Application id and hash…", onClick = onChangeApplication, focusRequester = application, enabled = !state.busy)
                TvTextRow(text = "Sign out", onClick = onSignOut, enabled = !state.busy)
                if (state.profileReloadNeeded) {
                    TvProfileReload(state, onRetryProfiles, takesFocus = false)
                } else {
                    state.notice?.let { TvQuietLine(it) }
                }
            }
            TvCacheBudgetBlock()
            TvCacheVolumeBlock()
        }
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
        modifier = if (takesFocus) Modifier.padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical) else Modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        state.notice?.let { TvQuietLine(it) }
        TvQuietLine(if (state.busy) "Loading profiles…" else "Reload profiles to continue.")
        TvTextRow(text = "Try again", onClick = onRetry, focusRequester = retry, enabled = !state.busy)
    }
}
