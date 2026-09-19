// ViewModels and UiState only, no composables — ui-mobile and ui-tv render
// this module's state independently.
plugins {
    alias(libs.plugins.app.android.library)
}

android {
    namespace = "com.mediagram.android.feature.catalog"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:model"))
}
