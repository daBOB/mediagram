plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.hilt)
    alias(libs.plugins.app.android.security.crypto)
}

android {
    namespace = "com.mediagram.android.core.data"
}

dependencies {
    implementation(project(":core:rust"))
    implementation(project(":core:model"))

    androidTestImplementation(libs.findLibrary("kotlinx.coroutines.test").get())
}
