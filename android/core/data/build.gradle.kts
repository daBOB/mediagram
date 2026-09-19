plugins {
    alias(libs.plugins.app.android.library)
}

android {
    namespace = "com.mediagram.android.core.data"
}

dependencies {
    implementation(project(":core:rust"))
    implementation(project(":core:model"))
}
