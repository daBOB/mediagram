// ViewModel and UiState only, no composables — ui-mobile renders this
// module's state. There is no ui-tv counterpart yet: the System screen is
// reached through the mobile app bar's overflow menu, which television has
// not grown.
plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.hilt)
}

android {
    namespace = "com.mediagram.android.feature.system"
}

dependencies {
    implementation(project(":core:data"))
    // Only for PlaybackCounters — nothing here touches ExoPlayer directly.
    implementation(project(":core:playback"))

    testImplementation(libs.findLibrary("mockk").get())
    // SystemViewModelTest builds a real android.content.Context via
    // ApplicationProvider; the plain unit-test android.jar stub has no such
    // context to hand back, so this one runs under Robolectric rather than
    // the bare JVM. androidx-junit brings ApplicationProvider itself.
    testImplementation(libs.findLibrary("robolectric").get())
    testImplementation(libs.findLibrary("androidx.junit").get())
}
