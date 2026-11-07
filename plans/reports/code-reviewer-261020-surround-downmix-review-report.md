# Review: 5d874aaf "5.1 audio downmixes to stereo where the output can't take surround"

Verdict: **Approved** (no Critical; one Important follow-up that does not block this commit).
Checked against media3 1.10.1 sources (dl.google.com sources jar) and the repo at the commit.

## Critical
None.

## Important
1. Same bug remains on the FFmpeg path. `PlayerFactory.kt:176-186` swaps only `MediaCodecAudioRenderer`.
   `NoLosslessPassthroughSink` (`NoLosslessPassthroughSink.kt:28-31`) sends DTS/TrueHD to `FfmpegAudioRenderer` on every
   device, and that renderer outputs 6-ch PCM into the same AudioTrack path. On the Xiaomi tablet a DTS 5.1 title will
   most likely still hit the MIUI forced-direct fold-down. The commit title ("5.1 audio downmixes...") overclaims.
   Fix as a follow-up: a sink-level `ChannelMixingAudioProcessor` (6 to 2) applied only when `playsSurround` is false
   and not TV, or state the AAC/MediaCodec-only scope in the message. Not blocking, since AAC was the reported case.

## Minor
1. `StereoDownmixAudioRenderer.kt:60` / `PlayerFactory.kt:~181` - decision is made once per codec init. Verified: media3
   updates capabilities on route change (`AudioTrackAudioOutputProvider.onAudioCapabilitiesChanged`) but does not
   reinit the codec. Safe direction (stereo then ARC) stays stereo until next title. Bad direction (ARC/USB 5.1 then
   unplugged to speakers mid-title) keeps 6 ch on the speaker, so the bug returns until the next title. Rare; the
   comment states it. Acceptable.
2. API 32 non-TV with an ARC receiver: `AudioCapabilities.getCapabilitiesInternal` only resolves the default routed
   device on API 33+ (`Api33.getDefaultRoutedDeviceForAttributes`); on 32 the route is known only after the first
   AudioTrack, which is after codec config. It is read as stereo and downmixed. Narrow (non-TV, API 32 exactly). The
   `API 32+` gate in the code is right for the key; the cost is only this corner.
3. Miracast/remote-submix routes read as stereo (`SpeakerLayoutUtil` default), so casting the tablet now sends a
   stereo downmix where it used to send 6 ch (the debugger noted Miracast "fine"). Behaviour change, probably benign
   (mirroring mixes anyway). Worth a line in the risks if cast-to-TV work depends on surround.
4. `PlayerFactory.kt:~181` `out[out.indexOfFirst {...}] = ...` throws `ArrayIndexOutOfBounds(-1)` at player build if a
   media3 bump stops adding the renderer. The factory test would catch it, but `check(i >= 0)` gives a readable
   failure. Also `PlayerFactory.kt` is now 204 lines (limit 200); the renderers factory could move to its own file.
5. `StereoDownmixAudioRenderer.kt:50` widens `getMediaFormat` to public purely for the test. Fine, but `internal`
   would be enough (Kotlin `internal override` of a protected member is not allowed, so public is the only option; the
   comment already says so).
6. Tests (`StereoDownmixAudioRendererTest.kt`): the capabilities are real media3 objects, which is good (not a mock of
   the logic). Gaps: (a) capabilities reach the renderer through a `ForwardingAudioSink` override, so the real
   sink-to-provider-to-route path, including the null-before-first-use case (treated as stereo), is untested; (b) runs
   at SDK 35 only, so the reported API 33 and the `< 32` no-op branch have no coverage; (c) the factory test checks the
   subclass type but the FFmpeg-after-platform ordering is covered only by the existing `PlayerFactoryTest.kt:177`
   (still passes, 314/314 per debugger). Cheap addition: an SDK 31 case asserting the key is 99/absent.

## Answers to the review questions
1. Root cause and layer: correct. `MediaCodecAudioRenderer.getMediaFormat` sets `KEY_MAX_OUTPUT_CHANNEL_COUNT=99` on
   API 32+ with no route check (verified in source, ~line 1090); the platform AAC decoder defaults to 8 anyway, so 6 ch
   reach the sink regardless. Overriding `getMediaFormat` is the documented extension point and
   `DefaultRenderersFactory.buildAudioRenderers` is the documented factory hook; the in-place swap keeps the
   extension renderers (it uses `getCodecAdapterFactory()`, the same factory media3 passes, so async-queueing flags
   carry over). Discarded instance is inert: `audioSink.setListener` runs in `onEnabled` (line 710). Not a hack.
   Device confirmation (channelMask 0x3, no "Force direct") is still owed; the vendor-decoder honouring of the key is
   from AOSP source, not observed.
2. Rule: sound. `Integer.bitCount(mask) > 2` over `spatializerChannelMasks + speakerLayoutChannelMasks` mirrors media3's
   own IAMF layout choice. Bluetooth is always stereo in media3 (correct, A2DP/LE is 2 ch). ARC/eARC/USB use real PCM
   profiles. TV exemption uses `UI_MODE_TYPE_TELEVISION`, same signal as media3 `Util.isTv`, and covers every TV route
   (plain HDMI, built-in, BT), so a TV box is unchanged. Failure mode is conservative: unknown caps equal stereo on
   non-TV only. Phone on a USB-C HDMI dongle into an AVR gets stereo (media3 reads `TYPE_HDMI` as stereo); documented.
   Passthrough on non-TV is not offered by media3 (direct-playback profiles are TV/automotive only), and on TV the
   renderer takes the early return, so AC-3/E-AC-3 passthrough is untouched.
3. Freshness: capabilities come from the live provider (`DefaultAudioSink.getAudioCapabilities`) at each codec init, with
   `updateAudioCapabilitiesReceiver` having run during format-support checks before configure. Not stale at init;
   see Minor 1 and 2 for mid-title and API 32.
4. Ordering: unchanged. Swap keeps index; FFmpeg still follows; `NoLosslessPassthroughSink` still wraps the sink that
   both renderers get (`ForwardingAudioSink` forwards `getAudioCapabilities`).
5. Tests: see Minor 6.
6. Quality: comments explain why, no plan labels, new files under 200 lines; `PlayerFactory.kt` crosses 200 (Minor 4).

## Unresolved
- Should the FFmpeg/DTS path get a sink-level downmix (Important 1)?
