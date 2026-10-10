plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.android.library.compose)
}

android {
    namespace = "com.mediagram.android.core.designsystem"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    // MediagramTheme is M3; the shared compose bundle deliberately leaves
    // material3 out so ui-tv never sees it, so the phone theme's own module
    // declares it itself.
    implementation(libs.findLibrary("androidx.compose.material3").get())
    // WindowCompat: MediagramTheme sets the system bar icon appearance
    // itself, once the resolved theme is known, rather than leaving it to
    // MainActivity's pre-Compose guess.
    implementation(libs.findLibrary("androidx.core").get())

    // SharedPreferencesAppearanceSettingsTest opens a real SharedPreferences
    // file; the plain unit-test android.jar stub returns nothing for it, so
    // this runs under Robolectric. androidx-junit brings ApplicationProvider.
    testImplementation(libs.findLibrary("robolectric").get())
    testImplementation(libs.findLibrary("androidx.junit").get())

    // The motion modifiers are tested by composing them and reading their
    // layer-mapped bounds on a paused test clock, as ui-mobile's tests do.
    // setContent comes from activity-compose, which ui-test-junit4 only
    // brings at runtime.
    testImplementation(libs.findLibrary("androidx.activity.compose").get())
    testImplementation(libs.findLibrary("androidx.compose.ui.test.junit4").get())
    debugImplementation(libs.findLibrary("androidx.compose.ui.test.manifest").get())
}
