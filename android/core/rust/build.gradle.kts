plugins {
    alias(libs.plugins.app.android.library)
}

android {
    namespace = "com.mediagram.android.core.rust"

    lint {
        // The generated bindings choose their cleaner at runtime: they call
        // Class.forName("java.lang.ref.Cleaner") and fall back to JNA's
        // cleaner when it throws, so java.lang.ref.Cleaner is never loaded
        // below API 33. Lint cannot see through a reflective guard and
        // reports the calls inside that branch as NewApi. Scoped to this
        // module, which contains nothing but generated code; every
        // hand-written module still has NewApi on.
        disable += "NewApi"
    }
}

dependencies {
    // The generated bindings call into the native library through JNA; the
    // `@aar` classifier pulls the Android-packaged variant instead of the
    // desktop jar.
    implementation("${libs.findLibrary("jna").get().get()}@aar")
    implementation(libs.findLibrary("kotlinx.coroutines.android").get())
}

// The cross-compiled .so is not committed (see .gitignore), so a clean
// checkout has bindings but no library behind them. Kotlin compiles,
// the APK assembles, installs and launches — and then throws
// UnsatisfiedLinkError the first time a screen resolves CoreClient. This
// makes that a build failure instead of a runtime one.
//
// The guard refuses rather than running the build script itself. The .so
// and the committed Kotlin bindings are one pair, generated together by
// scripts/generate-android-bindings.sh; rebuilding only the .so from
// whatever the Rust sources currently say would happily produce a library
// whose exported surface no longer matches the bindings compiled against
// it, which fails at the same place, later, and less legibly.
//
// It hangs off the native-library merge, the only step that needs the
// file. Unit tests and lint pull no such task, so a Kotlin-only build
// still needs no Rust toolchain.
val packagedAbis = listOf("arm64-v8a", "x86_64")
val jniLibsDir = layout.projectDirectory.dir("src/main/jniLibs")

val verifyNativeCore by tasks.registering {
    description = "Fails unless the native core has been cross-compiled for every packaged ABI."
    val abiLibraries = packagedAbis.associateWith { jniLibsDir.file("$it/libmediagram_core.so").asFile }
    inputs.files(abiLibraries.values).withPropertyName("nativeCore").optional()
    doLast {
        val missing = abiLibraries.filterValues { !it.isFile }.keys
        if (missing.isNotEmpty()) {
            throw GradleException(
                "The native core is missing for ${missing.joinToString(", ")}. " +
                    "An APK without it launches and then dies with UnsatisfiedLinkError. " +
                    "Build it from the repository root with scripts/build-android-core.sh " +
                    "(or scripts/generate-android-bindings.sh, which also refreshes the Kotlin bindings); " +
                    "both need ANDROID_NDK_HOME and cargo-ndk.",
            )
        }
    }
}

tasks
    .matching { it.name.startsWith("merge") && (it.name.endsWith("NativeLibs") || it.name.endsWith("JniLibFolders")) }
    .configureEach { dependsOn(verifyNativeCore) }
