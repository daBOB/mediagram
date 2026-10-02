// The app replacing itself with the newest release pinned in the library
// channel. A process-lifetime service like WatchSync, so it lives in core and
// the System feature reads it without importing another feature. No
// composables: the System screen renders its status line.
plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.hilt)
}

android {
    namespace = "com.mediagram.android.core.update"
}

dependencies {
    implementation(project(":core:data"))

    testImplementation(project(":core:testing"))
}
