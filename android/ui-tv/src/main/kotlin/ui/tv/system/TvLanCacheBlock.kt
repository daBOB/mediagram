package ui.tv.system

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import system.LanCacheConnection
import system.LanCacheViewModel
import system.lanCacheStatusLine
import ui.tv.TvTextRow
import ui.tv.catalog.TvQuietLine

private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/**
 * The phone's home-cache-server block on a television: the status, the
 * switch, and the address and pairing token — each of those two a row that
 * opens its own text question, since a television types on a screen of its
 * own rather than into a field in a list. Back from either lands on the row
 * that opened it.
 *
 * The permission prompt is asked where the phone asks it: when a token is
 * saved (in [TvLanCachePanel]) and from the Grant row. `ACCESS_LOCAL_NETWORK`
 * exists only from API 37, so below that neither asks anything.
 */
@Composable
internal fun TvLanCacheBlock(
    returningFrom: TvSettingsPanel?,
    onOpen: (TvSettingsPanel) -> Unit,
) {
    val viewModel: LanCacheViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestPermission = rememberLocalNetworkRequest(viewModel::permissionResolved)
    val address = remember { FocusRequester() }
    val token = remember { FocusRequester() }
    LaunchedEffect(Unit) { viewModel.open() }
    val current = state ?: return

    // Keyed on the first composition with rows to land on: the state is read
    // asynchronously, and a request made before the rows exist is lost.
    LaunchedEffect(Unit) {
        when (returningFrom) {
            TvSettingsPanel.LanAddress -> address.requestFocus()
            TvSettingsPanel.LanToken -> token.requestFocus()
            else -> Unit
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        TvInfoBlock(heading = "Home cache server", rows = listOf("Status" to lanCacheStatusLine(current)))
        if (current.connection == LanCacheConnection.NEEDS_PERMISSION) {
            TvTextRow(text = "Grant local network access", onClick = requestPermission)
        }
        if (current.tokenRejected) TvQuietLine("Pairing token rejected.")
        TvTextRow(
            text = "Use the home cache server — ${if (current.enabled) "on" else "off"}",
            onClick = { viewModel.setEnabled(!current.enabled) },
            modifier =
                Modifier.semantics {
                    role = Role.Switch
                    toggleableState = ToggleableState(current.enabled)
                },
        )
        TvTextRow(
            text = "Server address — ${current.manualAddress.ifBlank { "found on the network" }}",
            onClick = { onOpen(TvSettingsPanel.LanAddress) },
            focusRequester = address,
        )
        TvTextRow(
            text = if (current.hasToken) "Pairing token — stored" else "Pairing token — none",
            onClick = { onOpen(TvSettingsPanel.LanToken) },
            focusRequester = token,
        )
    }
}

/** The runtime prompt for `ACCESS_LOCAL_NETWORK` on API 37 and up; a no-op below, where the permission does not exist. */
@Composable
internal fun rememberLocalNetworkRequest(onResolved: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResolved() }
    return { if (Build.VERSION.SDK_INT >= 37) launcher.launch(ACCESS_LOCAL_NETWORK) }
}
