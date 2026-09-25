package ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import setup.SettingsEvent
import setup.SettingsViewModel

/**
 * Acts on retained Settings completions whenever the library screen returns.
 * A sign-out offline takes up to ten
 * seconds and a library install longer; a viewer who backs out meanwhile must
 * still land signed out, or on the new library's shelves. Completions remain
 * pending during profile picking or Activity recreation, and are acknowledged
 * only after handling. The ViewModel is
 * the Activity's, so this is the same one the Settings screen drives.
 */
@Composable
fun SettingsOutcomes(
    onLibraryChanged: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val settings: SettingsViewModel = hiltViewModel()
    val libraryChanged by rememberUpdatedState(onLibraryChanged)
    val signedOut by rememberUpdatedState(onSignedOut)
    LaunchedEffect(settings) {
        settings.completions.collect { pending ->
            for (completion in pending) {
                when (completion.event) {
                    SettingsEvent.LibraryChanged -> libraryChanged()
                    SettingsEvent.SignedOut -> signedOut()
                    SettingsEvent.ApplicationChanged -> Unit
                }
                settings.acknowledgeCompletion(completion.id)
            }
        }
    }
}
