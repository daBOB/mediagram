// ViewModels and UiState only, no composables — ui-mobile and ui-tv render
// this module's state independently.
plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.hilt)
}

android {
    namespace = "com.mediagram.android.feature.player"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    // Only for the ExoPlayer instance behind PlayerHandle and the DI module
    // that provides it — no composable here ever touches media3 directly.
    implementation(project(":core:playback"))

    testImplementation(libs.findLibrary("mockk").get())
    // DefaultPlayerHandle.open() builds a real android.net.Uri (via
    // playback.setUri); the plain unit-test android.jar stub throws for
    // it, so its test runs under Robolectric rather than the bare JVM.
    testImplementation(libs.findLibrary("robolectric").get())
}
