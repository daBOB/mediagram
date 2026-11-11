// The game night's ViewModel and the kids decision, no composables — the TV
// screen renders the finished state — and the provider Spieltisch reads the night from.
plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.hilt)
}

android {
    namespace = "com.mediagram.android.feature.games"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:model"))

    testImplementation(project(":core:testing"))
    // GameNightProvider's tests need real cursors, files and permission checks.
    testImplementation(libs.findLibrary("robolectric").get())
}
