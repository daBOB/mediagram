// Media3's FFmpeg audio decoder extension, vendored. Media3 does not publish
// decoder_ffmpeg to Maven, so its Java sources (the audio half of
// libraries/decoder_ffmpeg at the 1.10.1 tag, unmodified, Apache-2.0) live
// here, and the native half is built by scripts/build-android-ffmpeg.sh
// into src/main/jniLibs. The package stays androidx.media3.decoder.ffmpeg:
// DefaultRenderersFactory finds FfmpegAudioRenderer by that exact class
// name, reflectively, and adds it only when the name resolves.
//
// Its only job is DTS and TrueHD on devices with no decoder for them — see
// PlayerFactory in core:playback, which switches it on.
plugins {
    alias(libs.plugins.app.android.library)
}

android {
    namespace = "com.mediagram.android.core.ffmpeg"

    defaultConfig {
        consumerProguardFiles("proguard-rules.txt")
    }

    lint {
        // Upstream code, kept byte-for-byte so a Media3 bump is a plain copy;
        // findings in it are Media3's to fix, not this project's.
        ignoreWarnings = true
        // Inside Media3 its own @UnstableApi needs no opt-in; out here every
        // upstream use of it reads as an unmarked opt-in. The code is
        // unchanged Media3 code built against the Media3 version it came from.
        disable += "UnsafeOptInUsageError"
    }
}

dependencies {
    // FfmpegAudioRenderer extends exoplayer's DecoderAudioRenderer, and
    // DefaultRenderersFactory hands it the same types; exposed as api so
    // consumers compile against the one media3 they already pin.
    api(libs.findLibrary("androidx.media3.exoplayer").get())
    api(libs.findLibrary("androidx.media3.decoder").get())
    // The upstream sources' own nullness annotations, as Media3's build.gradle has them.
    implementation(libs.findLibrary("androidx.annotation").get())
    compileOnly(libs.findLibrary("checker.qual").get())
}

// The .so is built, not committed (android/**/jniLibs/ is gitignored), so a
// clean checkout compiles and assembles an APK whose DTS films are silent
// again — FfmpegLibrary.isAvailable() just answers false. Refused here
// instead, the same way core:rust refuses an APK without its native core.
// Hung off the native-library merge, so unit tests and lint still need no NDK.
val packagedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
val jniLibsDir = layout.projectDirectory.dir("src/main/jniLibs")

val verifyFfmpeg by tasks.registering {
    description = "Fails unless the FFmpeg decoder library has been built for every packaged ABI."
    val abiLibraries = packagedAbis.associateWith { jniLibsDir.file("$it/libffmpegJNI.so").asFile }
    inputs.files(abiLibraries.values).withPropertyName("ffmpeg").optional()
    doLast {
        val missing = abiLibraries.filterValues { !it.isFile }.keys
        if (missing.isNotEmpty()) {
            throw GradleException(
                "The FFmpeg audio decoder is missing for ${missing.joinToString(", ")}. " +
                    "Without it DTS and TrueHD play silently on devices that cannot decode them. " +
                    "Build it from the repository root with scripts/build-android-ffmpeg.sh " +
                    "(needs ANDROID_NDK_HOME).",
            )
        }
    }
}

tasks
    .matching { it.name.startsWith("merge") && (it.name.endsWith("NativeLibs") || it.name.endsWith("JniLibFolders")) }
    .configureEach { dependsOn(verifyFfmpeg) }
