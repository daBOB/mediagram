package designsystem

import android.app.UiModeManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
    fun choosingBackdropSurvivesAFreshInstance() {
        SharedPreferencesAppearanceSettings(context).chooseBackdrop(Backdrop.SOLID)

        val reopened = SharedPreferencesAppearanceSettings(context).appearance.value

        assertEquals(Backdrop.SOLID, reopened.backdrop)
    }

    @Test
    fun anUnrecognisedStoredValueFallsBackToTheDefault() {
        context.getSharedPreferences("appearance_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("theme", "sepia")
            .putString("accent", "magenta")
            .putString("backdrop", "sepia")
            .apply()

        assertEquals(Appearance(), SharedPreferencesAppearanceSettings(context).appearance.value)
    }

    /** The cold-start window follows the choice, not the device, only if the system is told it. */
    @Test
    fun theChosenThemeBecomesTheAppsOwnNightMode() {
        val uiModeManager = context.getSystemService(UiModeManager::class.java)
        val settings = SharedPreferencesAppearanceSettings(context)

        settings.chooseTheme(ThemeChoice.DARK)
        assertEquals(UiModeManager.MODE_NIGHT_YES, shadowOf(uiModeManager).applicationNightMode)

        settings.chooseTheme(ThemeChoice.LIGHT)
        assertEquals(UiModeManager.MODE_NIGHT_NO, shadowOf(uiModeManager).applicationNightMode)

        settings.chooseTheme(ThemeChoice.AUTO)
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, shadowOf(uiModeManager).applicationNightMode)
    }
}
