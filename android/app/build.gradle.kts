import org.gradle.api.artifacts.VersionCatalogsExtension

// Gradle 9.5's implicit "libs" catalog (see settings.gradle.kts) does not wire
// type-safe `libs.xxx` accessors into this script for dependencies{}, only for
// plugins{}. Look library/bundle entries up through the catalog API directly.
val catalog = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
fun lib(alias: String) = catalog.findLibrary(alias).get()
fun bundle(name: String) = catalog.findBundle(name).get()

plugins {
    alias(libs.plugins.app.android.application)
    alias(libs.plugins.app.android.application.compose)
}

android {
    namespace = "com.mediagram.android"

    defaultConfig {
        applicationId = "com.mediagram.android"
        versionCode = 1
        versionName = "0.1.0"
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

dependencies {
    implementation(project(":ui-mobile"))
    implementation(project(":ui-tv"))
    implementation(project(":core:model"))

    implementation(lib("androidx-core"))
    implementation(lib("androidx-activity-compose"))
    implementation(lib("androidx-lifecycle-runtime-ktx"))
    implementation(bundle("compose"))

    testImplementation(bundle("unit-test"))
    testImplementation(lib("mockk"))
}
