// The kids and section rules of a game night, and the provider Spieltisch reads the night from.
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
