plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.android.library.compose)
    alias(libs.plugins.app.android.mobile.screen)
}

android {
    namespace = "com.mediagram.android.ui.mobile"
}

dependencies {
    implementation(project(":feature:catalog"))
    implementation(project(":feature:player"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
}
