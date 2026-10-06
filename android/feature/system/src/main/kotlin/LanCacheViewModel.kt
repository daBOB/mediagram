package system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

/**
 * The LAN cache block of Settings: on/off, the manual address override, the
 * token, and the connection status those three plus discovery add up to.
 *
 * The permission prompt is not tied to the on/off switch — [enabled]
 * defaults to `true`, so an off→on toggle would rarely if ever fire.
 * [ui.settings.LanCacheBlock] instead requests it when a token is saved
 * (pairing is the moment the feature becomes worth having it) and from an
 * explicit "Grant" action on the status row, and calls [permissionResolved]
 * once the launcher returns either way so that row re-reads immediately.
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
        private val _addressError = MutableStateFlow<String?>(null)
        private val _tokenError = MutableStateFlow<String?>(null)
        private val _failure = MutableStateFlow<String?>(null)
        private var lastState: LanCacheUiState? = null

        /** A save that failed stays said through the re-read it triggers, until the next save or [open]. */
        private var saveFailed = false

        /** The token store or these settings could not be read or saved; the block keeps its last good state meanwhile. */
        val failure: StateFlow<String?> = _failure.asStateFlow()

        val state: StateFlow<LanCacheUiState?> =
            combine(
                requests,
                locator.server,
                locator.searching,
                tokenStatus.rejected,
                combine(_addressError, _tokenError) { a, t -> a to t },
            ) { _, server, searching, rejected, (addressError, tokenError) ->
                // Caught inside the combine, not after stateIn: a throw here
                // would end the only collector, and Settings with it.
                try {
                    snapshot(server, searching, rejected, addressError, tokenError).also {
                        lastState = it
                        _failure.value = FAILURE.takeIf { saveFailed }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    _failure.value = FAILURE
                    lastState
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** Settings opening: a fresh discovery pass, since the manual address or the network may have changed since the last one. */
        fun open() {
            saveFailed = false
            locator.discover()
            requests.update { it + 1 }
        }

        fun setEnabled(value: Boolean) = act { settings.setEnabled(value) }

        /** Saves [address] if it parses; `false` when it was refused and [LanCacheUiState.addressError] says why. */
        fun setManualAddress(address: String): Boolean =
            normalizeManualAddress(address).fold(
                onSuccess = { normalized ->
                    _addressError.value = null
                    act {
                        settings.setManualAddress(normalized)
                        locator.discover()
                    }
                    true
                },
                onFailure = { e ->
                    _addressError.value = e.message
                    false
                },
            )

        /** Saves [token] if it is well formed; `false` when it was refused and [LanCacheUiState.tokenError] says why. */
        fun saveToken(token: String): Boolean =
            normalizePairingToken(token).fold(
                onSuccess = { normalized ->
                    _tokenError.value = null
                    act {
                        tokenSettings.write(normalized)
                        tokenStatus.clear()
                    }
                    true
                },
                onFailure = { e ->
                    _tokenError.value = e.message
                    false
                },
            )

        /** A fresh question for the address or token: the reason a previous answer was refused no longer applies. */
        fun clearErrors() {
            _addressError.value = null
            _tokenError.value = null
        }

        /** The permission launcher returned, granted or not — worth an immediate re-read rather than the next 5-second window. */
        fun permissionResolved() = requests.update { it + 1 }

        private fun act(work: suspend () -> Unit) {
            viewModelScope.launch {
                saveFailed =
                    try {
                        work()
                        false
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        _failure.value = FAILURE
                        true
                    }
                requests.update { it + 1 }
            }
        }

        private suspend fun snapshot(
            server: LanServer?,
            searching: Boolean,
            tokenRejected: Boolean,
            addressError: String?,
            tokenError: String?,
        ): LanCacheUiState {
            val connection =
                when {
                    !hasLocalNetworkPermission() -> LanCacheConnection.NEEDS_PERMISSION
                    server != null -> LanCacheConnection.CONNECTED
                    searching -> LanCacheConnection.SEARCHING
                    else -> LanCacheConnection.NOT_FOUND
                }
            // One read of the server's status for all three figures, so the
            // held amount, the budget it is measured against and the chunk
            // count always describe the same moment.
            val status = server?.let { client.status(it.baseUrl) }
            return LanCacheUiState(
                enabled = settings.enabled(),
                hasToken = tokenSettings.read() != null,
                manualAddress = settings.manualAddress().orEmpty(),
                connection = connection,
                connectedHost = server?.host,
                heldBytes = status?.heldBytes,
                budgetBytes = status?.budgetBytes,
                chunks = status?.chunks,
                tokenRejected = tokenRejected,
                addressError = addressError,
                tokenError = tokenError,
            )
        }

        private fun hasLocalNetworkPermission(): Boolean =
            localNetworkPermissionGranted(
                sdkInt = Build.VERSION.SDK_INT,
                // The permission's name is only read where it exists.
                granted =
                    Build.VERSION.SDK_INT >= 37 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED,
            )

        private companion object {
            const val FAILURE = "Home cache server settings could not be read or saved. Try again."
        }
    }
