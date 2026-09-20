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
}
