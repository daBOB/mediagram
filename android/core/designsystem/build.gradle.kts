plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.android.library.compose)
}

android {
    namespace = "com.mediagram.android.core.designsystem"
}

dependencies {
    // MediagramTheme is M3; the shared compose bundle deliberately leaves
    // material3 out so ui-tv never sees it, so the phone theme's own module
    // declares it itself.
    implementation(libs.findLibrary("androidx.compose.material3").get())
}
