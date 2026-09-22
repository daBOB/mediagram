package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import setup.SettingsEvent
import setup.SettingsViewModel

/**
 * Acts on what Settings changed, from the library screen that is always
 * there rather than from Settings itself. A sign-out offline takes up to ten
 * seconds and a library install longer; a viewer who backs out meanwhile must
 * still land signed out, or on the new library's shelves. The ViewModel is
 * the Activity's, so this is the same one the Settings screen drives.
 */
@Composable
internal fun SettingsOutcomes(onLibraryChanged: () -> Unit, onSignedOut: () -> Unit) {
    val settings: SettingsViewModel = hiltViewModel()
    LaunchedEffect(settings) {
        settings.events.collect { event ->
            when (event) {
                SettingsEvent.LibraryChanged -> onLibraryChanged()
                SettingsEvent.SignedOut -> onSignedOut()
                SettingsEvent.ApplicationChanged -> Unit
            }
        }
    }
}
