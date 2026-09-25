package ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import system.LanCacheConnection
import system.LanCacheUiState
import system.LanCacheViewModel
import ui.components.Block
import ui.formatting.humanSize

private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/**
 * Settings' home-cache-server block: bound to [LanCacheViewModel], and the
 * one place the runtime permission prompt is requested. `ACCESS_LOCAL_NETWORK`
 * does not exist below API 37, so the prompt is skipped there entirely —
 * launching it would be a no-op on this project's own target device, a
 * tablet on API 36, but it costs nothing to be explicit about why.
 * Enabling is not the trigger: [LanCacheUiState.enabled] defaults to
 * `true`, so an off→on toggle would rarely fire. Saving a token — pairing
 * is the moment the feature becomes worth having it — and the status
 * row's own "Grant" action both are.
 */
@Composable
internal fun LanCacheBlock() {
    val viewModel: LanCacheViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.permissionResolved()
        }
    val requestPermission: () -> Unit = { if (Build.VERSION.SDK_INT >= 37) launcher.launch(ACCESS_LOCAL_NETWORK) }
    LaunchedEffect(Unit) { viewModel.open() }
    LanCacheBlockContent(
        state = state,
        onSetEnabled = viewModel::setEnabled,
        onSaveManualAddress = viewModel::setManualAddress,
        onSaveToken = { token ->
            viewModel.saveToken(token)
            requestPermission()
        },
        onGrantPermission = requestPermission,
    )
}

/** Stateless so a test drives every branch — connection wording, the rejection notice, the switch, the Grant action — without a real ViewModel or permission launcher. */
@Composable
internal fun LanCacheBlockContent(
    state: LanCacheUiState?,
    onSetEnabled: (Boolean) -> Unit,
    onSaveManualAddress: (String) -> Unit,
    onSaveToken: (String) -> Unit,
    onGrantPermission: () -> Unit,
) {
    if (state == null) return
    var address by remember(state.manualAddress) { mutableStateOf(state.manualAddress) }
    var token by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Block(heading = "Home cache server", rows = listOf("Status" to lanCacheStatusLine(state)))
        if (state.connection == LanCacheConnection.NEEDS_PERMISSION) {
            TextButton(onClick = onGrantPermission) { Text("Grant local network access") }
        }
        if (state.tokenRejected) {
            Text("Pairing token rejected.", color = MaterialTheme.colorScheme.error)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            modifier = Modifier.toggleable(value = state.enabled, onValueChange = onSetEnabled),
        ) {
            Text("Use the home cache server")
            Switch(checked = state.enabled, onCheckedChange = null)
        }
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Server address (optional — leave blank to rely on discovery)") },
        )
        state.addressError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSaveManualAddress(address) }) { Text("Save address") }
        Text(if (state.hasToken) "A pairing token is stored." else "No pairing token is stored.")
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Pairing token") },
            visualTransformation = PasswordVisualTransformation(),
        )
        state.tokenError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSaveToken(token); token = "" }) { Text("Save token") }
    }
}

/** "Searching" / "Connected to host, holding X" / "Not found" / "Needs local network permission". */
internal fun lanCacheStatusLine(state: LanCacheUiState): String =
    when (state.connection) {
        LanCacheConnection.NEEDS_PERMISSION -> "Needs local network permission"
        LanCacheConnection.NOT_FOUND -> "Not found"
        LanCacheConnection.SEARCHING -> "Searching"
        LanCacheConnection.CONNECTED -> {
            val host = state.connectedHost ?: "server"
            val held = state.heldBytes?.let { ", holding ${humanSize(it)}" }.orEmpty()
            "Connected to $host$held"
        }
    }
