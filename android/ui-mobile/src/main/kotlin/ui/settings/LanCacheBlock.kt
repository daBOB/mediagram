package ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.LocalCatalogueTones
import designsystem.Radius
import designsystem.Spacing
import system.LanCacheConnection
import system.LanCacheUiState
import system.LanCacheViewModel
import system.lanCacheRows
import ui.components.Block
import ui.components.LedgerEntry

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
    val tones = LocalCatalogueTones.current
    Column {
        Block(
            heading = "Home cache server",
            rows =
                lanCacheRows(state).map { (label, value) ->
                    LedgerEntry(label, value, held = label == "Status" && state.connection == LanCacheConnection.CONNECTED)
                },
        )
        if (state.connection == LanCacheConnection.NEEDS_PERMISSION) {
            Row(modifier = Modifier.padding(top = Spacing.small)) {
                LinePill(text = "Grant local network access", onClick = onGrantPermission)
            }
        }
        if (state.tokenRejected) {
            Text(
                "Pairing token rejected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.small),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .toggleable(value = state.enabled, onValueChange = onSetEnabled, role = Role.Switch)
                    .padding(vertical = Spacing.small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Use the home cache server", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = state.enabled,
                onCheckedChange = null,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedBorderColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = tones.quiet,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedBorderColor = tones.quiet,
                    ),
            )
        }
        HorizontalDivider(color = tones.ruleSoft)
        SettingsField(
            label = "Server address",
            value = address,
            onValueChange = { address = it },
            placeholder = "Found automatically",
            note = state.addressError ?: "Optional. Leave blank to find the server on the network.",
            noteIsError = state.addressError != null,
            actionLabel = "Save",
            actionDescription = "Save address",
            onAction = { onSaveManualAddress(address) },
        )
        SettingsField(
            label = "Pairing token",
            value = token,
            onValueChange = { token = it },
            password = true,
            note = state.tokenError ?: if (state.hasToken) "A pairing token is stored." else "No pairing token is stored.",
            noteIsError = state.tokenError != null,
            actionLabel = if (state.hasToken) "Replace" else "Save",
            actionDescription = "Save token",
            onAction = { onSaveToken(token); token = "" },
        )
    }
}

/** A labeled field beside its own inline action pill, and a note beneath it — Storage's Server address and Pairing token, the only two on this screen shaped this way. */
@Composable
private fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    note: String,
    noteIsError: Boolean,
    actionLabel: String,
    actionDescription: String,
    onAction: () -> Unit,
    placeholder: String? = null,
    password: Boolean = false,
) {
    Column(modifier = Modifier.padding(top = Spacing.large)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                // Material3's own default text style for a field's typed text,
                // its label and its placeholder is bodyLarge — Newsreader on
                // this catalogue, its one kept serif exception. A field is
                // interface chrome, not a sentence, so all three ask for Geist
                // explicitly instead, the way the mockup's own `.input` does.
                label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                placeholder = placeholder?.let { text -> { Text(text, style = MaterialTheme.typography.bodyMedium) } },
                textStyle = MaterialTheme.typography.bodyMedium,
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                singleLine = true,
                shape = RoundedCornerShape(Radius.control),
                modifier = Modifier.weight(1f),
            )
            LinePill(text = actionLabel, onClick = onAction, contentDescription = actionDescription, modifier = Modifier.padding(top = 8.dp))
        }
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = if (noteIsError) MaterialTheme.colorScheme.error else LocalCatalogueTones.current.quiet,
            modifier = Modifier.padding(top = Spacing.small),
        )
    }
}
