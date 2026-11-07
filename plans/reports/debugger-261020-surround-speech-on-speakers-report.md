# Debugger report — 5.1 speech "under water" on Xiaomi Pad 5 Pro speakers

Date: 2026-10-20 · Branch: fix/surround-speech-on-speakers · Commit: 5d874aaf

## Executive summary
- Symptom: GoT S1E1 (5.1 AAC) on the Xiaomi Pad 5 Pro (MIUI 14 / API 33) has muffled dialogue; music/effects fine. Stereo titles fine. Same title fine over Miracast to the TV.
- Root cause: nothing asks the AAC decoder for stereo, so the 5.1 track reaches the output as 6-channel PCM. On this MIUI build a 6-ch AudioTrack is forced onto a DIRECT output (bypasses AudioFlinger's mixer and its proper downmix), and the fold-down there mangles the centre (dialogue) channel.
- Fix: the platform audio renderer (`StereoDownmixAudioRenderer`, a `MediaCodecAudioRenderer` subclass) sets `MediaFormat.KEY_MAX_OUTPUT_CHANNEL_COUNT = 2` unless the output renders surround itself. The decoder (FDK AAC) then downmixes, using the stream's downmix levels or the standard ones (centre -3 dB into L/R). Only stereo reaches MIUI's output.

## Evidence chain
1. Device (from controller): `c2.android.aac.decoder`; AudioTrack `channelMask 0x3f` (5.1), `ch 6`; MIUI `set(): Force direct flag, flags(0x8)` (0x8 = AUDIO_OUTPUT_FLAG_DIRECT); no spatializer (`mHasSpatializerEffect:false`).
2. media3 1.10.1 `MediaCodecAudioRenderer.getMediaFormat` (sources jar from dl.google.com, lines ~1090-1092): `if (SDK_INT >= 32) mediaFormat.setInteger(KEY_MAX_OUTPUT_CHANNEL_COUNT, 99)`. No route check at all. Only IAMF picks a layout from `AudioCapabilities` (lines ~1095-1112).
3. AOSP android13-release `C2SoftAacDec.cpp`: `MAX_CHANNEL_COUNT 8` is the default for `C2_PARAMKEY_MAX_CHANNEL_COUNT` (lines 46, 101-104), passed to `AAC_PCM_MAX_OUTPUT_CHANNELS` (lines 389-392). So even without media3's 99 the decoder outputs all 6 channels on API 33. `CCodecConfig.cpp` maps `KEY_MAX_OUTPUT_CHANNEL_COUNT` to that same param (line 931). So setting 2 gives a real decoder downmix.
4. media3 `AudioTrackAudioOutputProvider.getFormatSupportLevel`: 16-bit PCM is "Always supported" at any channel count. The sink never pushes back. media3 assumes the platform mixer downmixes, and MIUI's forced-direct path breaks that assumption.
5. Unit test mutation: with the cap removed (pre-fix behaviour), `surroundIsDownmixedToStereoForOutputsThatPlayTwoChannels` FAILS (decoder asked for 99 on the speaker route).

## Hypotheses tested
- H1 (confirmed): 6-ch PCM to a stereo/direct output, bad vendor fold-down. Evidence 1-5 above.
- H2 (eliminated): a spatializer effect / virtualizer mangling centre. dumpsys shows no spatializer effect and it is not enabled.
- H3 (eliminated as the cause): DefaultTrackSelector channel constraint. It only *prefers* stereo tracks (`isAudioFormatWithinAudioChannelCountConstraints`). With one 5.1 track it is still selected (`exceedAudioConstraintsIfNecessary`), so it can't prevent 6-ch decode.
- H4 (eliminated): a container or track defect. The same file plays correctly through the mixer path (Miracast) and on the TV.

## Fix
- `android/core/playback/src/main/kotlin/StereoDownmixAudioRenderer.kt` (new): overrides `getMediaFormat`. On API ≥ 32 (`S_V2`, the key's API level), not a TV, and the output doesn't play surround, it sets `KEY_MAX_OUTPUT_CHANNEL_COUNT = 2`. "Plays surround" means any of media3's `AudioCapabilities.spatializerChannelMasks` or `speakerLayoutChannelMasks` has more than 2 channels. This is the same inputs media3's own IAMF layout choice uses (`IamfUtil.getOutputChannelMaskForCurrentConfiguration`). If the capabilities are unknown, it counts them as stereo.
- TV clause: media3's `SpeakerLayoutUtil` doesn't read plain `TYPE_HDMI` and returns stereo for it. A TV box on HDMI would wrongly downmix without the clause. media3's DefaultTrackSelector exempts TVs the same way. The box's behaviour is unchanged.
- `PlayerFactory.kt`: `PlaybackRenderersFactory.buildAudioRenderers` calls super, then swaps the `MediaCodecAudioRenderer` in place for the subclass, with the same arguments and the same slot ahead of FFmpeg. The media3 constructor has no side effects (listener is set in `onEnabled`), so the discarded instance is inert.

| Route | Result |
|---|---|
| built-in speaker, wired headphones, BT A2DP, USB headset | 2 ch |
| HDMI ARC/eARC reporting 5.1 PCM, USB 5.1 interface | all channels |
| spatializer on | all channels |
| TV device (any route) | all channels (unchanged) |

## Tests
- New `android/core/playback/src/test/kotlin/StereoDownmixAudioRendererTest.kt` (Robolectric, `@Config(sdk=[35])`, which follows the project precedent; Robolectric's AudioProfileBuilder fails on 36). It has 5 tests: stereo routes ×4 → 2; HDMI ARC 5.1 → ≥6; TV on plain HDMI → ≥6; spatializer on → ≥6; the factory builds the subclass. Capabilities are real media3 `AudioCapabilities.getCapabilities(...)` read off Robolectric `AudioDeviceInfo`s.
- Mutation checks: removing the cap fails the stereo test; removing the TV clause fails the TV test; ignoring capabilities fails the HDMI-ARC and spatializer tests.
- `./gradlew :core:playback:testDebugUnitTest :core:playback:lint :app:compileDebugKotlin`: BUILD SUCCESSFUL. 314 tests passed, 0 failed, 0 skipped (re-run with `--rerun` on the committed tree: 314/314).

## Files changed
- android/core/playback/src/main/kotlin/StereoDownmixAudioRenderer.kt (new)
- android/core/playback/src/main/kotlin/PlayerFactory.kt
- android/core/playback/src/test/kotlin/StereoDownmixAudioRendererTest.kt (new)
- No version bump (controller does it).

## Risks
- The decision is made when the codec is configured. A route change mid-title (plugging into a 5.1 USB DAC or ARC, a spatializer toggle) applies from the next codec init, usually the next title.
- A non-TV phone on a USB-C to plain HDMI dongle into an AVR now gets stereo PCM: media3 reads `TYPE_HDMI` as stereo. media3's own track selector already treats that case as stereo.
- Applies to every format the platform renderer decodes, not only AAC. Decoders that don't read the key (AOSP FLAC/Opus/Vorbis) are unaffected. Vendor Dolby decoders that do read it now produce their own stereo downmix on stereo routes.
- API 24-31 unchanged: the key is not set there, and older decoders don't read it.

## Unresolved questions
1. Device check owed (controller): on d54bc94f, expect `AudioTrack ... channelMask 0x3` (stereo) and no MIUI "Force direct" line for GoT S1E1; dialogue clear.
2. Sibling path not covered: DTS/TrueHD 5.1 decoded by FFmpeg (`FfmpegAudioRenderer`) still outputs 6-ch PCM. On this MIUI tablet's speakers it likely hits the same forced-direct fold-down. A sink-level downmix (media3 `ChannelMixingAudioProcessor` with `ChannelMixingMatrix.createForConstantGain(6, 2)`) would cover it. Should that follow?
3. Do API 29-31 devices with a similar vendor direct path exist in this household? If so, `KEY_AAC_MAX_OUTPUT_CHANNEL_COUNT` would be the key to set there.
