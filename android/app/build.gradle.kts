plugins {
    alias(libs.plugins.app.android.application)
}

android {
    namespace = "com.mediagram.android"

    defaultConfig {
        applicationId = "com.mediagram.android"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":ui-mobile"))
    implementation(project(":ui-tv"))
    implementation(project(":core:model"))
}
