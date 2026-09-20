plugins {
    alias(libs.plugins.app.android.application)
    alias(libs.plugins.app.android.application.compose)
    alias(libs.plugins.app.hilt)
}

android {
    namespace = "com.mediagram.android"

    defaultConfig {
        applicationId = "com.mediagram.android"
        versionCode = 1
        versionName = "0.8.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

// androidx-core, lifecycle-runtime-ktx, the compose bundle and the unit-test
// deps live in the app.android.application / app.android.application.compose
// convention plugins applied above. Only this module's own project graph
// belongs here.
dependencies {
    implementation(project(":ui-mobile"))
    implementation(project(":ui-tv"))
    implementation(project(":core:model"))
    // Provides the Core/CoreClient DI wiring in di/CoreModule.kt and the
    // PackageSettings field MainActivity injects to route between screens.
    implementation(project(":core:data"))
}
