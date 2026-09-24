plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.android.library.compose)
}

android {
    namespace = "com.mediagram.android.ui.tv"
}

dependencies {
    implementation(project(":ui-common"))
    implementation(project(":feature:catalog"))
    implementation(project(":feature:player"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
}
