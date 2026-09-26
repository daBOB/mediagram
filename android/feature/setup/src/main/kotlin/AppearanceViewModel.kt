package setup

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import designsystem.Accent
import designsystem.Appearance
import designsystem.AppearanceSettings
import designsystem.ThemeChoice
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Settings › Appearance's whole ViewModel: [AppearanceSettings] already
 * holds and persists the choice, so this only hands its `StateFlow` to
 * whichever surface is asking — the phone's settings screen, the
 * television's, and the root `MediagramTheme`/`TvTheme` composition every
 * other screen sits under — and turns a tap into the write [state] then
 * reflects back, the way every other Settings row here works.
 */
@HiltViewModel
class AppearanceViewModel
    @Inject
    constructor(
        private val appearanceSettings: AppearanceSettings,
    ) : ViewModel() {
        val state: StateFlow<Appearance> = appearanceSettings.appearance

        fun chooseTheme(theme: ThemeChoice) = appearanceSettings.chooseTheme(theme)

        fun chooseAccent(accent: Accent) = appearanceSettings.chooseAccent(accent)
    }
