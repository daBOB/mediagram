// ViewModels and UiState only, no composables — ui-mobile and ui-tv render
// this module's state independently.
//
// What belongs here and what does not: this module owns the library. Which
// set is open, whether it is preparing or has failed, stopping when a screen
// is left for good — anything that needs CoreClient. It does not own
// transport. Whether the player is playing, where the playhead is, how long
// the set runs, and seeking by an increment are facts ExoPlayer already
// keeps, and the surfaces read them through media3's own Compose state
// holders against the Player this module exposes. A copy of them routed
// through here could only be the same number later, or a different one.
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
