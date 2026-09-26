package setup

import designsystem.Accent
import designsystem.Appearance
import designsystem.InMemoryAppearanceSettings
import designsystem.ThemeChoice
import kotlin.test.Test
import kotlin.test.assertEquals

/** [AppearanceViewModel] is a thin pass-through to [designsystem.AppearanceSettings]; this pins that it forwards both writes and reads it back unchanged. */
class AppearanceViewModelTest {
    @Test
    fun startsAtWhateverAppearanceSettingsAlreadyHeld() {
        val settings = InMemoryAppearanceSettings(Appearance(ThemeChoice.LIGHT, Accent.TEAL))
        val model = AppearanceViewModel(settings)

        assertEquals(Appearance(ThemeChoice.LIGHT, Accent.TEAL), model.state.value)
    }

    @Test
    fun choosingThemeAndAccentReachesTheSharedSettings() {
        val settings = InMemoryAppearanceSettings()
        val model = AppearanceViewModel(settings)

        model.chooseTheme(ThemeChoice.LIGHT)
        model.chooseAccent(Accent.BLUE)

        assertEquals(Appearance(ThemeChoice.LIGHT, Accent.BLUE), model.state.value)
        assertEquals(Appearance(ThemeChoice.LIGHT, Accent.BLUE), settings.appearance.value)
    }
}
