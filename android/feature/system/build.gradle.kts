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
}
