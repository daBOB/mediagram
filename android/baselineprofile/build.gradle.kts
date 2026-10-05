plugins {
    alias(libs.plugins.app.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.mediagram.android.baselineprofile"
    targetProjectPath = ":app"
}

// Generation runs on a device that is already signed in, not a managed
// device: the journey needs a real library, and a fresh emulator has none.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.findLibrary("androidx.junit").get())
    implementation(libs.findLibrary("androidx.test.uiautomator").get())
    implementation(libs.findLibrary("androidx.benchmark.macro.junit4").get())
}
