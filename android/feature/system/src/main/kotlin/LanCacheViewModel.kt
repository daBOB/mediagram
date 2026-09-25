package system

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import playback.LanCacheSettings
import playback.LanCacheTokenStatus
import playback.LanChunkProtocol
import playback.LanServer
import playback.LanServerSource
import settings.LanCacheTokenSettings
import javax.inject.Inject

/** Matches the manifest's own `<uses-permission>` — see AndroidManifest.xml for why it is widened at all. */
private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/**
 * The LAN cache block of Settings: on/off, the manual address override, the
 * token, and the connection status those three plus discovery add up to.
 * Enabling the block from off is what should trigger the permission prompt
 * — [ui.settings.LanCacheBlock] reads [state] to decide whether to show it,
 * and calls [permissionResolved] once the launcher returns either way so
 * the status row re-reads immediately rather than waiting on the next
 * unrelated recomposition.
 */
@HiltViewModel
class LanCacheViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settings: LanCacheSettings,
        private val tokenSettings: LanCacheTokenSettings,
        private val tokenStatus: LanCacheTokenStatus,
        private val locator: LanServerSource,
        private val client: LanChunkProtocol,
    ) : ViewModel() {
        private val requests = MutableStateFlow(0)

        val state: StateFlow<LanCacheUiState?> =
            combine(requests, locator.server, locator.searching, tokenStatus.rejected) { _, server, searching, rejected ->
                Triple(server, searching, rejected)
            }.map { (server, searching, rejected) -> snapshot(server, searching, rejected) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** Settings opening: a fresh discovery pass, since the manual address or the network may have changed since the last one. */
        fun open() {
            locator.discover()
            requests.update { it + 1 }
        }

        fun setEnabled(value: Boolean) = act { settings.setEnabled(value) }

        fun setManualAddress(address: String) =
            act {
                settings.setManualAddress(address.ifBlank { null })
                locator.discover()
            }

        fun saveToken(token: String) =
            act {
                tokenSettings.write(token)
                tokenStatus.clear()
            }

        /** The permission launcher returned, granted or not — worth an immediate re-read rather than the next 5-second window. */
        fun permissionResolved() = requests.update { it + 1 }

        private fun act(work: suspend () -> Unit) {
            viewModelScope.launch {
                work()
                requests.update { it + 1 }
            }
        }

        private suspend fun snapshot(
            server: LanServer?,
            searching: Boolean,
            tokenRejected: Boolean,
        ): LanCacheUiState {
            val connection =
                when {
                    !hasLocalNetworkPermission() -> LanCacheConnection.NEEDS_PERMISSION
                    server != null -> LanCacheConnection.CONNECTED
                    searching -> LanCacheConnection.SEARCHING
                    else -> LanCacheConnection.NOT_FOUND
                }
            return LanCacheUiState(
                enabled = settings.enabled(),
                hasToken = tokenSettings.read() != null,
                manualAddress = settings.manualAddress().orEmpty(),
                connection = connection,
                connectedHost = server?.host,
                heldBytes = server?.let { client.status(it.baseUrl)?.heldBytes },
                tokenRejected = tokenRejected,
            )
        }

        private fun hasLocalNetworkPermission(): Boolean =
            ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
    }
