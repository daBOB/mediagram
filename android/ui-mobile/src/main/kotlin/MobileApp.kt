package ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogViewModel
import designsystem.MediagramTheme
import designsystem.Spacing
import kotlinx.coroutines.launch
import login.LoginUiState
import login.LoginViewModel
import settings.PackageSettings

/**
 * Starts at login when unauthorized, at settings when authorized with no
 * package credentials, and at the catalog otherwise. [hasApiCredentials]
 * gates the whole authenticated tree: when the Telegram application
 * identity itself is missing, this never reaches a screen that would
 * construct the native core.
 */
@Composable
fun MobileApp(hasApiCredentials: Boolean, packageSettings: PackageSettings) {
    MediagramTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (!hasApiCredentials) {
                MissingCredentialsMessage()
            } else {
                AuthenticatedApp(packageSettings)
            }
        }
    }
}

/** Whether the package credentials read has completed, distinct from having completed and found none. */
private sealed interface CredentialsState {
    data object Loading : CredentialsState
    data object Missing : CredentialsState
    data object Present : CredentialsState
}

@Composable
private fun AuthenticatedApp(packageSettings: PackageSettings) {
    val loginViewModel: LoginViewModel = hiltViewModel()
    val loginState by loginViewModel.state.collectAsStateWithLifecycle()
    var credentialsState by remember { mutableStateOf<CredentialsState>(CredentialsState.Loading) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(loginState) {
        if (loginState is LoginUiState.Authorized) {
            credentialsState = if (packageSettings.read() != null) {
                CredentialsState.Present
            } else {
                CredentialsState.Missing
            }
        }
    }

    when {
        loginState !is LoginUiState.Authorized -> LoginScreen(
            state = loginState,
            onSubmitPhone = loginViewModel::submitPhone,
            onSubmitCode = loginViewModel::submitCode,
            onSubmitPassword = loginViewModel::submitPassword,
        )

        credentialsState is CredentialsState.Loading -> LoadingIndicator()

        credentialsState is CredentialsState.Missing -> SettingsScreen(
            onSave = { url, key ->
                scope.launch {
                    packageSettings.write(url, key)
                    credentialsState = CredentialsState.Present
                }
            },
        )

        else -> CatalogAndPlayer()
    }
}

/** The catalog, and whichever set it opened — the first screen pair with a real back-stack need. */
@Composable
private fun CatalogAndPlayer() {
    val catalogViewModel: CatalogViewModel = hiltViewModel()
    val catalogState by catalogViewModel.state.collectAsStateWithLifecycle()
    var openedSetId by remember { mutableStateOf<String?>(null) }

    val setId = openedSetId
    if (setId != null) {
        BackHandler { openedSetId = null }
        PlayerScreen(setId = setId, onBack = { openedSetId = null })
    } else {
        CatalogScreen(state = catalogState, onOpen = { openedSetId = it })
    }
}

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MissingCredentialsMessage() {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.large), contentAlignment = Alignment.Center) {
        Text(
            text = "Missing Telegram API credentials. Add MEDIAGRAM_API_ID and " +
                "MEDIAGRAM_API_HASH to local.properties, then rebuild.",
        )
    }
}
