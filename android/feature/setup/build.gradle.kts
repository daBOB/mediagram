// ViewModel, UiState and input checks only, no composables — ui-mobile and
// ui-tv render this module's state independently, and a television has the
// same three questions to ask as a tablet.
plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.hilt)
}

android {
    namespace = "com.mediagram.android.feature.setup"
}

dependencies {
    implementation(project(":core:data"))
}
