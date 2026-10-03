plugins {
    alias(libs.plugins.app.android.application)
    alias(libs.plugins.app.android.application.compose)
    alias(libs.plugins.app.hilt)
}

/**
 * The versionCode Android compares before installing an update, derived from
 * versionName so a release can never forget to raise it and two machines
 * can never hand out the same one: 0.93.0 → 93000.
 */
fun versionCodeOf(name: String): Int {
    val parts = name.split(".").map { it.toIntOrNull() ?: error("versionName $name is not major.minor.patch") }
    require(parts.size == 3) { "versionName $name is not major.minor.patch" }
    val (major, minor, patch) = parts
    require(minor < 1000 && patch < 1000) { "versionName $name: minor and patch must stay below 1000" }
    return major * 1_000_000 + minor * 1_000 + patch
}

/** This machine's release key, from ~/.gradle/gradle.properties; absent elsewhere, so a release built there is unsigned. */
val releaseStoreFile: String? = providers.gradleProperty("mediagram.signing.storeFile").orNull

android {
    namespace = "com.mediagram.android"

    defaultConfig {
        applicationId = "com.mediagram.android"
        versionName = "0.96.0"
        versionCode = versionCodeOf(versionName!!)
        // Only a release build updates itself; debug and benchmark builds are installed by adb.
        resValue("bool", "self_update", "false")
    }

    buildFeatures { resValues = true }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("mediagram.signing.storePassword").get()
                keyAlias = providers.gradleProperty("mediagram.signing.keyAlias").get()
                keyPassword = providers.gradleProperty("mediagram.signing.keyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // The two ABIs real devices have: the tablet (arm64) and the TV box (armv7 only).
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            resValue("bool", "self_update", "true")
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
        // A build as fast as a release is meant to be — not debuggable, so
        // ART compiles it ahead of time instead of interpreting it, and
        // minified by R8. `release` above is minified the same way, so this
        // measures what a release delivers. Signed with the debug
        // key, so it installs over a debug install on a device that is
        // already signed in, keeping that session instead of asking for a
        // new SMS code. Only for measuring on a real device: a debug build
        // runs Compose several times slower, which says nothing about what
        // a viewer gets. Profileable from the shell (its own manifest) so
        // gfxinfo and Perfetto can still read it.
        create("benchmark") {
            initWith(getByName("release"))
            // Measuring builds are installed by adb and never replace themselves; any ABI, for emulators too.
            resValue("bool", "self_update", "false")
            ndk { abiFilters.clear() }
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    lint {
        // PreloadService posts a plain notification without POST_NOTIFICATIONS
        // by design — see the app manifest's own note beside that permission,
        // and PreloadService's class doc. NotificationPermission is a
        // manifest-level check that does not honour a @SuppressLint at the
        // call site the way MissingPermission does. A baseline (not
        // `disable +=`) captures only this one already-known finding, so a
        // NotificationPermission issue anywhere else in the app is still
        // caught rather than silenced project-wide.
        baseline = file("lint-baseline.xml")
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
    // Provides the CoreProvider DI wiring in di/CoreModule.kt and the
    // PackageSettings field MainActivity injects to route between screens.
    implementation(project(":core:data"))
    // The self-updater, and the player whose state it waits on.
    implementation(project(":core:update"))
    implementation(project(":feature:player"))
    // PlayerHandle.player is a media3 Player; isPlaying is read in di/UpdateModule.kt.
    implementation(libs.findLibrary("androidx.media3.exoplayer").get())
}
