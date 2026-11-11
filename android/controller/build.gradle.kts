// Spieltisch: the game-night controller for phones and tablets. A web view on
// the TV box's phone page plus what a browser cannot do: keep the screen on,
// vibrate, find the box on the Wi-Fi. No Telegram, no Rust core, no library.
import convention.versionCodeOf

plugins {
    alias(libs.plugins.app.android.application)
    alias(libs.plugins.app.android.application.compose)
}

/** Spieltisch's version lives in crates/spieltisch-core/Cargo.toml; this app adds no manifest of its own. */
val projectVersion: String =
    rootProject
        .file("../crates/spieltisch-core/Cargo.toml")
        .readLines()
        .first { it.startsWith("version = ") }
        .substringAfter('"')
        .substringBefore('"')

/** This machine's release key, from ~/.gradle/gradle.properties; absent elsewhere, so a release built there is unsigned. */
val releaseStoreFile: String? = providers.gradleProperty("mediagram.signing.storeFile").orNull

android {
    namespace = "com.mediagram.controller"

    defaultConfig {
        applicationId = "com.mediagram.controller"
        versionName = projectVersion
        versionCode = versionCodeOf(projectVersion)
    }

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
            // The page reaches the bridge by reflection; nothing here is worth shrinking.
            isMinifyEnabled = false
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(libs.findLibrary("androidx.compose.material3").get())
    implementation(libs.findLibrary("kotlinx.coroutines.android").get())
    implementation(libs.findLibrary("play.services.code.scanner").get())

    // AppBridge keeps the page's memory in SharedPreferences and answers with org.json,
    // both of which the plain unit-test android.jar only stubs.
    testImplementation(libs.findLibrary("robolectric").get())
    testImplementation(libs.findLibrary("androidx.junit").get())
}
