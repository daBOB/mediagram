package ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

@Composable
private fun AuthenticatedApp(packageSettings: PackageSettings) {
    val loginViewModel: LoginViewModel = hiltViewModel()
    val loginState by loginViewModel.state.collectAsStateWithLifecycle()
    var hasPackageCredentials by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(loginState) {
        if (loginState is LoginUiState.Authorized) {
            hasPackageCredentials = packageSettings.read() != null
        }
    }

    when {
        loginState !is LoginUiState.Authorized -> LoginScreen(
            state = loginState,
            onSubmitPhone = loginViewModel::submitPhone,
            onSubmitCode = loginViewModel::submitCode,
            onSubmitPassword = loginViewModel::submitPassword,
        )

        hasPackageCredentials != true -> SettingsScreen(
            onSave = { url, key ->
                scope.launch {
                    packageSettings.write(url, key)
                    hasPackageCredentials = true
                }
            },
        )

        else -> {
            val catalogViewModel: CatalogViewModel = hiltViewModel()
            val catalogState by catalogViewModel.state.collectAsStateWithLifecycle()
            CatalogScreen(state = catalogState)
        }
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
