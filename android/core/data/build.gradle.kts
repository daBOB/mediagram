plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.hilt)
}

android {
    namespace = "com.mediagram.android.core.data"
}

dependencies {
    implementation(project(":core:rust"))
    implementation(project(":core:model"))
}
