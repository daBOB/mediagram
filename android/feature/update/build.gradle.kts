// The app replacing itself with the newest release pinned in the library
// channel. No composables: the System screen renders its status line.
plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.hilt)
}

android {
    namespace = "com.mediagram.android.feature.update"
}

dependencies {
    implementation(project(":core:data"))

    testImplementation(project(":core:testing"))
}
