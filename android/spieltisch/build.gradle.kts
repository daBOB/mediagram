// Spieltisch: the game night on the TV box as an app of its own. It hosts the
// night in its own native core (crates/spieltisch-core), shows the games as a
// poster shelf and each game's TV page in a web view. No library, no Telegram.
import convention.versionCodeOf

plugins {
    alias(libs.plugins.app.android.application)
    alias(libs.plugins.app.android.application.compose)
}

/** This machine's release key, from ~/.gradle/gradle.properties; absent elsewhere, so a release built there is unsigned. */
val releaseStoreFile: String? = providers.gradleProperty("mediagram.signing.storeFile").orNull

android {
    namespace = "com.mediagram.spieltisch"

    defaultConfig {
        applicationId = "com.mediagram.spieltisch"
        versionName = "0.181.0"
        versionCode = versionCodeOf(versionName!!)
        // Only a release build updates itself; debug builds are installed by adb.
        resValue("bool", "self_update", "false")
    }

    buildFeatures { resValues = true }

    // game-avatars/*.glb are already compressed (meshopt, KTX2): store them, read them as they are
    androidResources { noCompress += listOf("glb") }

    testOptions.unitTests.isIncludeAndroidResources = true

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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The TV boxes' ABIs: arm64, and armv7 for the boxes that run a 32-bit userspace.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            resValue("bool", "self_update", "true")
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    // Self-update, with a source of its own: this module has no Hilt, no :core:data and no media core.
    implementation(project(":core:update"))
    implementation(libs.findLibrary("androidx.tv.material").get())
    implementation(libs.findLibrary("coil.compose").get())
    implementation(libs.findLibrary("androidx.lifecycle.runtime.compose").get())
    // The generated bindings call the native core through JNA; `@aar` is the Android-packaged variant.
    implementation("${libs.findLibrary("jna").get().get()}@aar")
    implementation(libs.findLibrary("kotlinx.coroutines.android").get())

    testImplementation(libs.findLibrary("robolectric").get())
    testImplementation(libs.findLibrary("androidx.compose.ui.test.junit4").get())
    debugImplementation(libs.findLibrary("androidx.compose.ui.test.manifest").get())
}

// MediaAppContractTest reads the media app's manifest and provider; a change there alone must rerun it.
tasks.withType<Test>().configureEach {
    inputs
        .files("../app/src/main/AndroidManifest.xml", "../feature/games/src/main/kotlin/GameNightProvider.kt")
        .withPropertyName("mediaAppContract")
}

// The .so is not committed, so a clean checkout has bindings and no library behind them: the APK
// would install and die with UnsatisfiedLinkError on its first call. This makes that a build failure.
// It refuses rather than building the library itself, since the .so and the committed bindings are one
// pair (scripts/generate-spieltisch-bindings.sh). Only the native-library merge needs the file, so
// unit tests and lint still build with no Rust toolchain.
val packagedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
val jniLibsDir = layout.projectDirectory.dir("src/main/jniLibs")

val verifyNativeCore by tasks.registering {
    description = "Fails unless Spieltisch's native core has been cross-compiled for every packaged ABI."
    val abiLibraries = packagedAbis.associateWith { jniLibsDir.file("$it/libspieltisch_core.so").asFile }
    inputs.files(abiLibraries.values).withPropertyName("nativeCore").optional()
    doLast {
        val missing = abiLibraries.filterValues { !it.isFile }.keys
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Spieltisch's native core is missing for ${missing.joinToString(", ")}. " +
                    "Build it from the repository root with scripts/build-spieltisch-core.sh " +
                    "(or scripts/generate-spieltisch-bindings.sh, which also refreshes the Kotlin bindings).",
            )
        }
    }
}

tasks
    .matching { it.name.startsWith("merge") && (it.name.endsWith("NativeLibs") || it.name.endsWith("JniLibFolders")) }
    .configureEach { dependsOn(verifyNativeCore) }
