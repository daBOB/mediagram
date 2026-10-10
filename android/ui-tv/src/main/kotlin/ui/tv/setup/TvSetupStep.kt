package ui.tv.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvType
import setup.SetupUiState
import setup.SetupViewModel
import ui.tv.TvLibraryLoading
import ui.tv.TvTextRow

/**
 * The television counterpart to `ui.MobileApp`'s private `SetupStep`: the
 * same [SetupUiState] switch, the same [SetupViewModel] calls, rendered as
 * TV screens instead of phone ones. Nothing here decides anything the
 * ViewModel did not already decide — this only picks which screen draws.
 */
@Composable
fun TvSetupStep(
    state: SetupUiState,
    viewModel: SetupViewModel,
) {
    when (state) {
        // The app's first paint, as the web's is (`index.html:119-121`): every
        // launch but the first goes on to open the library, under the same wait.
        SetupUiState.Checking -> TvLibraryLoading()

        is SetupUiState.NeedsApplication ->
            TvApplicationScreen(error = state.error, onSubmit = viewModel::submitApplication)

        SetupUiState.NeedsSignIn ->
            TvWithStartOver(onStartOver = viewModel::startOver, focusStartOver = false) {
                TvSignInScreen(onAuthorized = viewModel::recheck)
            }

        is SetupUiState.NeedsLibrary ->
            TvWithStartOver(onStartOver = viewModel::startOver, focusStartOver = false) {
                TvLibraryChoiceScreen(
                    choices = state.choices,
                    error = state.error,
                    onChoose = viewModel::chooseLibrary,
                    onLookAgain = viewModel::listLibraries,
                )
            }

        // Nothing here can be answered by trying the same thing again, so
        // the only control offered is the one that clears what broke — and
        // with no field to land the remote in, that control takes the
        // initial focus itself.
        is SetupUiState.Failed ->
            TvWithStartOver(onStartOver = viewModel::startOver, focusStartOver = true) {
                TvCentredMessage(state.message)
            }

        // Rendered by the caller, which has a whole screen pair to give it.
        SetupUiState.Ready -> Unit
    }
}

/**
 * The row's own tag, for the androidTest set that proves it opens
 * [TvConfirmDialog] with the same focus and Back behaviour that dialog
 * already carries on its own.
 */
internal const val TvStartOverRowTag = "tv-start-over-row"

/**
 * Signing this device out is reachable from every setup step that has
 * anything stored to take back — the same rule `ui.MobileApp.WithStartOver`
 * follows, ported to a remote: a text row rather than a bottom bar button,
 * since a television has no bar to put one in yet. [content] owns its own
 * [Overscan] margin the way every TV screen does; this only reserves the
 * row's own line beneath it, padded to the same horizontal margin so the
 * two read as one column.
 */
@Composable
internal fun TvWithStartOver(
    onStartOver: () -> Unit,
    focusStartOver: Boolean,
    content: @Composable () -> Unit,
) {
    var asking by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    if (focusStartOver) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) { content() }
        TvStartOverRow(
            onClick = { asking = true },
            focusRequester = if (focusStartOver) focusRequester else null,
        )
    }

    if (asking) {
        TvConfirmDialog(
            title = "Start over?",
            body = START_OVER_BODY,
            confirmLabel = "Start over",
            confirm = {
                asking = false
                onStartOver()
            },
            cancel = { asking = false },
        )
    }
}

/**
 * Same warning as the phone's `StartOverConfirmation` — this names what
 * goes, not just that something will, and both surfaces ask in the same
 * words so the choice reads the same wherever it is made.
 */
internal const val START_OVER_BODY =
    "This signs this device out of Telegram and forgets the api_id and " +
        "api_hash, the library address and its key, the library itself, the " +
        "TMDB key, and where you left off on this device. All of it has to be " +
        "entered again."

@Composable
private fun TvStartOverRow(
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    TvTextRow(
        text = "Start over",
        onClick = onClick,
        // Bottom padding matches Overscan.vertical, not Spacing.medium: this
        // is the last row on the screen, so its own bottom margin is the
        // frame's safe-area edge, the same as every other screen's last row.
        modifier =
            Modifier
                .testTag(TvStartOverRowTag)
                .padding(start = Overscan.horizontal, end = Overscan.horizontal, top = Spacing.medium, bottom = Overscan.vertical),
        focusRequester = focusRequester,
    )
}

@Composable
internal fun TvCentredMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message, style = TvType.bodyLarge)
    }
}
