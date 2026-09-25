# FFmpeg audio decoder for DTS and TrueHD — 2026-09-26

Branch `feat/android-tv-ui`. Commits: `1d2a4b03` build, `0e733d7b` feat, `0dca854d` docs.
No version bump, as the brief asked.

## Versions
- Media3 1.10.1 (the pinned version). `decoder_ffmpeg` sources were taken from tag `1.10.1`.
- FFmpeg 6.0.1, which is what the Media3 README recommends ("release/6.0"). The tarball
  sha256 `9b16b873…29fc9623` is pinned in the script.
- NDK 28.2.13676358, API level 24 (the app's minSdk).

## Configure flags (LGPL 2.1+, confirmed by configure's `License:` line)
These are Media3's `build_ffmpeg.sh` options unchanged: `--target-os=android --enable-static
--disable-shared --disable-doc --disable-programs --disable-everything --disable-avdevice
--disable-avformat --disable-swscale --disable-postproc --disable-avfilter --disable-symver
--enable-swresample --extra-ldexeflags=-pie --disable-v4l2-m2m --disable-vulkan`.

I added three flags: `--enable-pic`, `--disable-zlib` and
`--enable-decoder=dca --enable-decoder=truehd --enable-decoder=mlp`.

The per-ABI arch, cpu and flag settings match Media3's script (armv7 uses softfp and
fix-cortex-a8; x86 and x86_64 use `--disable-asm`). No gpl, version3 or nonfree flags are set.

The decoder set is dca, truehd and mlp only. Media3's docs do not recommend ac3 or eac3 as a
fallback, and the box already passes E-AC3 through, so I left them out.

## Link step
The link step is done in the script rather than by Gradle or CMake:
- `clang++ -shared ffmpeg_jni.cc -lswresample -lavcodec -lavutil -landroid -llog -lm`
- `-static-libstdc++ -Wl,--no-undefined,-Bsymbolic,--exclude-libs,ALL,--gc-sections`
- `-Wl,-z,max-page-size=16384`, then `llvm-strip`.

The script then checks that every LOAD segment is aligned to `0x4000` (all four ABIs pass).
Each library needs only libandroid, liblog, libm, libdl and libc, and exports the 9 JNI entry
points.

## Module layout
- `android/core/ffmpeg/` holds:
  - `src/main/java/androidx/media3/decoder/ffmpeg/`: FfmpegLibrary, FfmpegAudioDecoder,
    FfmpegAudioRenderer, FfmpegDecoderException and package-info. They are unmodified. The
    experimental video renderer is left out.
  - `src/main/jni/ffmpeg_jni.cc`, unmodified.
  - `proguard-rules.txt`, used as consumer rules.
  - `licenses/` (the LGPL text and a README).
  - `src/androidTest/FfmpegLibraryTest.kt`.
  - `src/main/jniLibs/` is gitignored, following the existing `android/**/jniLibs/` rule.
  - `verifyFfmpeg` is attached to the merge-native-libs tasks, the same pattern as core:rust.
    Unit tests and lint still need no NDK.
- Lint in the module ignores warnings and disables `UnsafeOptInUsageError`. That check fires
  on Media3's own `@UnstableApi` inside the vendored code.
- `core:playback` → `implementation(project(":core:ffmpeg"))`. `PlayerFactory.renderersFactory()`
  returns `DefaultRenderersFactory(context).setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)`
  and is passed to `ExoPlayer.Builder`. There is one place for it, and phone and TV share it.
- Why ON and not PREFER: the platform renderer stays first, so passthrough and hardware still
  win. FFmpeg takes DTS only because MediaCodec declines it. This is confirmed on the box, so
  PREFER is not needed.
- Catalog additions: `androidx-media3-decoder`, `androidx-annotation` 1.10.0 and
  `checker-qual` 3.43.0 (compileOnly).
- `scripts/check.sh` now also compiles `:core:ffmpeg:compileDebugAndroidTestKotlin`.

## APK size (debug, universal, .so stored uncompressed)
| ABI | libffmpegJNI.so |
|---|---|
| arm64-v8a | 1,059,192 B |
| armeabi-v7a | 950,900 B |
| x86_64 | 1,076,776 B |
| x86 | 1,095,880 B |

- The universal APK grows by about 4.18 MB, to 87.2 MB.
- A per-ABI split would grow by about 0.95–1.1 MB.
- The dex delta (5 small classes) is negligible.

## Tests
- `PlayerFactoryTest.ffmpegAudioFollowsThePlatformAudioRenderer` (Robolectric) passes. It checks
  that the renderers include `FfmpegAudioRenderer`, placed after `MediaCodecAudioRenderer`.
- `FfmpegLibraryTest` passed 3/3 on the emulator Google_TV_1080p (x86, API 34):
  - `isAvailable()` is true.
  - `supportsFormat` is true for DTS, DTS-HD and TrueHD.
  - It is false for AAC.
- `./gradlew testDebugUnitTest :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin
  :core:ffmpeg:compileDebugAndroidTestKotlin :app:assembleDebug` passes (BUILD SUCCESSFUL).
  That is the Android part of `scripts/check.sh` plus assemble.

## Real box (Skyworth HPR302, armeabi-v7a, Android 14)
- `:app:installDebug` installed over the existing app (it did not uninstall or clear data).
  `primaryCpuAbi=armeabi-v7a`.
- Profile **TV test**. Title: **Das ist das Ende** (`1080p · mkv · h264 · dts`). I pressed
  Resume at 9:14 and let it play for about 20 s (00:41:33–00:41:53).
- Logcat: `DefaultRenderersFactory: Loaded FfmpegAudioRenderer.` The only MediaCodec component
  the app created was `c2.realtek.video.avc.decoder`, so no platform audio codec was used and
  the audio went through FFmpeg. The app logged no E or F lines, and there was no
  UnsatisfiedLinkError.
- `dumpsys media.audio_flinger`: the app's (pid 28992) track was active, with PCM 16-bit,
  channel mask `0x3F` (5.1), 48 kHz, and the mixer out of standby. Before this change the
  mixer stayed in standby and there was no track.
- `dumpsys audio`: the app's AudioTrack was `state:started` with `mutedState:none`.
- The media session was PLAYING at 572 s.
- **DTS sound: yes, renderer FfmpegAudioRenderer.**
- Afterwards I stopped playback and switched the box back to profile **andre**.
- The emulator I started for the test has been shut down again.

## Concerns
- The `.so` is not committed. A clean checkout must run `scripts/build-android-ffmpeg.sh` or
  the APK build fails, which is intended and matches the Rust core.
- The FFmpeg decoder outputs 5.1 PCM. On the HDMI monitor the platform downmixes it; I did
  not check this against an AVR.
- AC-3 and E-AC-3 are not included. A phone without an AC-3 decoder would still play those
  silently. Adding them means adding two decoder names; the size cost has not been measured.
- I did not listen to the audio on the box; sound is inferred from the track and mixer state.
- Unresolved: none.
