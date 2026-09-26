package designsystem

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** This device's theme and accent together — Settings › Appearance's whole answer. */
data class Appearance(
    val theme: ThemeChoice = ThemeChoice.Default,
    val accent: Accent = Accent.Default,
)

/**
 * Appearance is a property of this screen, not of the library or the
 * profile watching it — the same rule the web keeps it under this
 * browser's own storage for, in `lib/appearance-boot.js`.
 */
interface AppearanceSettings {
    val appearance: StateFlow<Appearance>

    fun chooseTheme(theme: ThemeChoice)

    fun chooseAccent(accent: Accent)
}

/** In-memory implementation for tests; nothing here ever touches disk. */
class InMemoryAppearanceSettings(initial: Appearance = Appearance()) : AppearanceSettings {
    private val _appearance = MutableStateFlow(initial)
    override val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    override fun chooseTheme(theme: ThemeChoice) {
        _appearance.value = _appearance.value.copy(theme = theme)
    }

    override fun chooseAccent(accent: Accent) {
        _appearance.value = _appearance.value.copy(accent = accent)
    }
}

/**
 * Plain preferences, not the encrypted ones the setup flow uses: a theme
 * and an accent are not a secret, and a keystore failure should never be
 * able to cost a viewer their Appearance choice.
 */
class SharedPreferencesAppearanceSettings(context: Context) : AppearanceSettings {
    private val preferences = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    private val _appearance =
        MutableStateFlow(
            Appearance(
                theme = ThemeChoice.fromStorageKey(preferences.getString(KEY_THEME, null)),
                accent = Accent.fromStorageKey(preferences.getString(KEY_ACCENT, null)),
            ),
        )
    override val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    override fun chooseTheme(theme: ThemeChoice) {
        _appearance.value = _appearance.value.copy(theme = theme)
        preferences.edit().putString(KEY_THEME, theme.storageKey).apply()
    }

    override fun chooseAccent(accent: Accent) {
        _appearance.value = _appearance.value.copy(accent = accent)
        preferences.edit().putString(KEY_ACCENT, accent.storageKey).apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "appearance_settings"
        const val KEY_THEME = "theme"
        const val KEY_ACCENT = "accent"
    }
}
