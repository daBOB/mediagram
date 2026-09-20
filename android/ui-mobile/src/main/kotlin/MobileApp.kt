package ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogViewModel
import designsystem.MediagramTheme
import designsystem.Spacing
import login.LoginUiState
import login.LoginViewModel
import setup.SetupUiState
import setup.SetupViewModel

/**
 * The whole app hangs off one question — which setup step is outstanding —
 * and [SetupViewModel] answers it from storage every time it is asked
 * rather than from a remembered position, so a launch part-way through
 * setup lands back on the step that is still missing.
 */
@Composable
fun MobileApp() {
    MediagramTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val setupViewModel: SetupViewModel = hiltViewModel()
            val setupState by setupViewModel.state.collectAsStateWithLifecycle()

            // A session can be invalidated while this app is in the
            // background — signed out from another device, or from
            // Telegram's own session list — and nothing here would hear
            // about it. Coming back to the foreground is the moment to ask
            // again, rather than finding out through a catalog that will
            // not load.
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { setupViewModel.recheck() }

            if (setupState is SetupUiState.Ready) {
                CatalogAndPlayer(onStartOver = setupViewModel::startOver)
            } else {
                // The app draws edge to edge. Every setup screen is prose
                // and controls a person has to read and reach, and without
                // this the row along the bottom sits underneath the
                // navigation bar, where it cannot be tapped at all.
                Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    SetupStep(setupState, setupViewModel)
                }
            }
        }
    }
}

@Composable
private fun SetupStep(state: SetupUiState, viewModel: SetupViewModel) {
    when (state) {
        SetupUiState.Checking -> LoadingIndicator()

        is SetupUiState.NeedsApplication -> TelegramApplicationScreen(
            error = state.error,
            onSubmit = viewModel::submitApplication,
        )

        SetupUiState.NeedsSignIn -> WithStartOver(viewModel::startOver) {
            SignIn(onAuthorized = viewModel::recheck)
        }

        is SetupUiState.NeedsLibrary -> WithStartOver(viewModel::startOver) {
            LibraryScreen(
                choices = state.choices,
                error = state.error,
                onChoose = viewModel::chooseLibrary,
                onLookAgain = viewModel::listLibraries,
            )
        }

        // Nothing here can be answered by trying the same thing again, so
        // the only control offered is the one that clears what broke.
        is SetupUiState.Failed -> WithStartOver(viewModel::startOver) {
            CentredText(state.message)
        }

        // Rendered by the caller, which has a whole screen pair to give it.
        SetupUiState.Ready -> Unit
    }
}

/**
 * Sign-in is its own ViewModel, reused as-is: the per-step retry that keeps
 * a rejected code on screen lives there, and the setup flow only needs to
 * know when it is finished so it can ask what is outstanding next.
 */
@Composable
private fun SignIn(onAuthorized: () -> Unit) {
    val loginViewModel: LoginViewModel = hiltViewModel()
    val loginState by loginViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(loginState) {
        if (loginState is LoginUiState.Authorized) onAuthorized()
    }

    LoginScreen(
        state = loginState,
        onSubmitPhone = loginViewModel::submitPhone,
        onSubmitCode = loginViewModel::submitCode,
        onSubmitPassword = loginViewModel::submitPassword,
    )
}

/**
 * Signing this device out is reachable from every screen that has anything
 * stored to take back — including a catalog that is working perfectly,
 * because handing a tablet on is not a malfunction. Only the first step,
 * which has nothing stored yet, goes without.
 */
@Composable
private fun WithStartOver(onStartOver: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(modifier = Modifier.weight(1f)) { content() }
        StartOverAction(onConfirm = onStartOver)
    }
}

/** The catalog, and whichever set it opened — the first screen pair with a real back-stack need. */
@Composable
private fun CatalogAndPlayer(onStartOver: () -> Unit) {
    val catalogViewModel: CatalogViewModel = hiltViewModel()
    val catalogState by catalogViewModel.state.collectAsStateWithLifecycle()
    // rememberSaveable, not remember: the Activity is fully destroyed and
    // recreated on rotation (there is no android:configChanges), and the
    // singleton player/ViewModel survive that regardless — without this,
    // rotating away from an open set would drop back to the catalog while
    // the film kept playing underneath it.
    var openedSetId by rememberSaveable { mutableStateOf<String?>(null) }

    val setId = openedSetId
    if (setId != null) {
        // The player gets the whole window; a film is the one thing here
        // that wants the space under the system bars.
        BackHandler { openedSetId = null }
        PlayerScreen(setId = setId, onBack = { openedSetId = null })
    } else {
        WithStartOver(onStartOver) {
            CatalogScreen(state = catalogState, onOpen = { openedSetId = it })
        }
    }
}

@Composable
private fun CentredText(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(Spacing.large),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message)
    }
}

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
