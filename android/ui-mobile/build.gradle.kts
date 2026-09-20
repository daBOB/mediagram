plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.android.library.compose)
    alias(libs.plugins.app.android.mobile.screen)
}

android {
    namespace = "com.mediagram.android.ui.mobile"
}

dependencies {
    implementation(project(":feature:catalog"))
    implementation(project(":feature:player"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    // PlayerScreen renders the ViewModel's own ExoPlayer through
    // PlayerSurface directly; feature:player exposes the player but keeps
    // it an implementation dependency, so this module needs its own.
    implementation(project(":core:playback"))
    implementation(libs.findLibrary("androidx.activity.compose").get())
}
