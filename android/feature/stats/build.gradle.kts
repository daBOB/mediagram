// The Stats page's ViewModel, its read and its page state, no composables —
// ui-mobile and ui-tv each render the same finished strings, so the two
// pages cannot word a line differently.
plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.hilt)
}

android {
    namespace = "com.mediagram.android.feature.stats"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:model"))

    testImplementation(project(":core:testing"))
}
