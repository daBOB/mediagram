plugins {
    alias(libs.plugins.app.android.application)
    alias(libs.plugins.app.android.application.compose)
    alias(libs.plugins.app.hilt)
}

android {
    namespace = "com.mediagram.android"

    defaultConfig {
        applicationId = "com.mediagram.android"
        versionCode = 18
        versionName = "0.66.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // A build as fast as a release is meant to be — not debuggable, so
        // ART compiles it ahead of time instead of interpreting it, and
        // minified by R8. `release` above is not minified yet, so what this
        // measures is the ceiling a minified release would reach, not what
        // today's release delivers. Signed with the debug
        // key, so it installs over a debug install on a device that is
        // already signed in, keeping that session instead of asking for a
        // new SMS code. Only for measuring on a real device: a debug build
        // runs Compose several times slower, which says nothing about what
        // a viewer gets. Profileable from the shell (its own manifest) so
        // gfxinfo and Perfetto can still read it.
        create("benchmark") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
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
