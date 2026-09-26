package designsystem

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [SharedPreferencesAppearanceSettings] round-trips through a real `SharedPreferences` file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedPreferencesAppearanceSettingsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun defaultsToAutoAndCoralWhenNothingIsStored() {
        assertEquals(Appearance(), SharedPreferencesAppearanceSettings(context).appearance.value)
    }

    @Test
    fun choosingThemeAndAccentSurvivesAFreshInstance() {
        SharedPreferencesAppearanceSettings(context).apply {
            chooseTheme(ThemeChoice.LIGHT)
            chooseAccent(Accent.BLUE)
        }

        val reopened = SharedPreferencesAppearanceSettings(context).appearance.value

        assertEquals(Appearance(ThemeChoice.LIGHT, Accent.BLUE), reopened)
    }

    @Test
    fun anUnrecognisedStoredValueFallsBackToTheDefault() {
        context.getSharedPreferences("appearance_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("theme", "sepia")
            .putString("accent", "magenta")
            .apply()

        assertEquals(Appearance(), SharedPreferencesAppearanceSettings(context).appearance.value)
    }
}
