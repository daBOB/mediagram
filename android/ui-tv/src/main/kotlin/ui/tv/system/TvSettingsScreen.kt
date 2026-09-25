package ui.tv.system

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import setup.SettingsViewModel
import ui.tv.setup.TvApplicationScreen
import ui.tv.setup.TvConfirmDialog
import ui.tv.setup.TvLibraryChoiceScreen

/** A part of Settings that takes the whole screen while it is open. */
internal enum class TvSettingsPanel { Library, Application }

/**
 * The phone's Settings on a television: the Telegram connection — who,
 * which library, which datacentre, and changing any of it — then the disk
 * cache, over the same [SettingsViewModel] and in the same order.
 *
 * Changing the library and the application identity reuse the very
 * screens setup asks them on, laid over this one, and Back from either
 * returns here with the remote on the row that opened it. A finished
 * change closes its panel the way the phone's does. What signing out and a
 * changed library lead to is `SettingsOutcomes`' to act on, in the library
 * around this screen, exactly as on the phone.
 */
@Composable
internal fun TvSettingsScreen() {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var panel by rememberSaveable { mutableStateOf<TvSettingsPanel?>(null) }
    var lastPanel by rememberSaveable { mutableStateOf<TvSettingsPanel?>(null) }
    var openedBeforeAction by rememberSaveable { mutableStateOf(state.completedActionId) }
    var askingSignOut by remember { mutableStateOf(false) }

    // Read afresh on every visit: the ViewModel outlives this screen, and a
    // row read before a sign-out and a new sign-in would name the old account.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // Independent of the library's acknowledgement: both surfaces must see
    // success, even when this form was detached when the action finished.
    LaunchedEffect(state.completedActionId) {
        if (state.completedActionId > openedBeforeAction) panel = null
    }

    val open = { which: TvSettingsPanel ->
        viewModel.clearNotice()
        openedBeforeAction = state.completedActionId
        panel = which
        lastPanel = which
    }

    when (panel) {
        TvSettingsPanel.Library -> {
            BackHandler { panel = null }
            TvLibraryChoiceScreen(
                choices = state.choices,
                error = state.notice,
                // One install at a time; a second press waits for the first.
                onChoose = { handle -> if (!state.busy) viewModel.chooseLibrary(handle) },
                onLookAgain = viewModel::listLibraries,
            )
        }

        TvSettingsPanel.Application -> {
            BackHandler { panel = null }
            if (state.profileReloadNeeded) {
                TvProfileReload(state, viewModel::retryProfiles, takesFocus = true)
            } else {
                TvApplicationScreen(
                    error = state.notice,
                    onSubmit = viewModel::changeApplication,
                    initialApiId = state.apiId?.toString().orEmpty(),
                )
            }
        }

        null ->
            TvSettingsRows(
                state = state,
                returningFrom = lastPanel,
                onChangeLibrary = {
                    open(TvSettingsPanel.Library)
                    viewModel.listLibraries()
                },
                onChangeApplication = { open(TvSettingsPanel.Application) },
                onSignOut = { askingSignOut = true },
                onRetryProfiles = viewModel::retryProfiles,
            )
    }

    if (askingSignOut) {
        TvConfirmDialog(
            title = "Sign out?",
            body = SIGN_OUT_BODY,
            confirmLabel = "Sign out",
            confirm = {
                askingSignOut = false
                viewModel.signOut()
            },
            cancel = { askingSignOut = false },
        )
    }
}

/**
 * The phone's words, worded against start over's: this ends the login and
 * takes the account's catalog with it, but keeps what is the device's own,
 * so signing back in is all that is left to do.
 */
private const val SIGN_OUT_BODY =
    "This signs this device out of Telegram and removes the library it " +
        "was reading. The api_id, api_hash and TMDB key stay; signing in " +
        "again is all it takes to come back."
