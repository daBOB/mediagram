package ui.tv.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import system.LanCacheViewModel
import ui.tv.setup.TvTextQuestion

/**
 * The home cache server's address or pairing token, asked as one of the
 * television's text questions — the phone's two fields, each on a screen
 * of its own. An accepted answer closes the question through [onDone]; a
 * refused one keeps it open with the reason under the field, the phone's
 * error in the phone's place. Saving a token also asks for local network
 * access, as the phone does at the same moment.
 */
@Composable
internal fun TvLanCachePanel(
    panel: TvSettingsPanel,
    onDone: () -> Unit,
) {
    val viewModel: LanCacheViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestPermission = rememberLocalNetworkRequest(viewModel::permissionResolved)
    if (panel == TvSettingsPanel.LanAddress) {
        var address by remember(state?.manualAddress) { mutableStateOf(state?.manualAddress.orEmpty()) }
        TvTextQuestion(
            heading = "Home cache server address",
            explanation = "Optional. Leave it blank to find the server on the network by itself.",
            label = "Server address",
            value = address,
            onValue = { address = it },
            onSubmit = { if (viewModel.setManualAddress(address)) onDone() },
            error = state?.addressError,
        )
    } else {
        var token by remember { mutableStateOf("") }
        TvTextQuestion(
            heading = "Pair with the home cache server",
            explanation = if (state?.hasToken == true) "A pairing token is stored." else "No pairing token is stored.",
            label = "Pairing token",
            value = token,
            onValue = { token = it },
            onSubmit = {
                if (viewModel.saveToken(token)) {
                    token = ""
                    requestPermission()
                    onDone()
                }
            },
            secret = true,
            error = state?.tokenError,
        )
    }
}
