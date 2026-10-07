// ViewModels, UiState and the System page's rows, no composables —
// ui-mobile and ui-tv both render this module's state, so the two surfaces'
// System and storage pages say the same things.
plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.hilt)
}

android {
    namespace = "com.mediagram.android.feature.system"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    // The cache and LAN-cache settings, occupancy and volumes this module
    // reports and edits, plus PlaybackCounters; nothing here touches
    // ExoPlayer directly.
    implementation(project(":core:playback"))
    implementation(project(":core:update"))

    testImplementation(libs.findLibrary("mockk").get())
    testImplementation(project(":core:testing"))
    // SystemViewModelTest and LanCacheViewModel's tests need a real
    // android.content.Context (ApplicationProvider, permission checks); the
    // plain unit-test android.jar stub has none, so they run under
    // Robolectric. androidx-junit brings ApplicationProvider itself.
    testImplementation(libs.findLibrary("robolectric").get())
    testImplementation(libs.findLibrary("androidx.junit").get())
}
