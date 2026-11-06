# Player deinterlace toggle (web + Android)

Branch `feat/deinterlace` (from `feat/artwork-fetch-progress`), worktree `../mediagram-deinterlace`. Built as 0.144.0; merged to main as 0.148.0 (2026-11-06; spieltisch had used 0.143–0.146 on the box).

## Why
Roseanne S02–S09 (German PAL DVD rip) is field-interlaced video that the encode flags progressive.
No player deinterlaces it: the box's Realtek decoder logs `nEnableDeInterlace:0 interlace:0`, and
browsers never deinterlace. Those episodes are being re-encoded with bwdif on bytefortress. Other
titles like them exist, so the viewer gets a manual switch. The user chose: **a player toggle,
remembered per show, on web and Android** (2026-11-06). Detection by the uploader and an index
flag were offered and not chosen.

## Shared decisions (both surfaces must match)
- **Preference:** name `deinterlace`, value `on`, filed under the show's scope like `framing`
  (state.db rows that web and Android already share). Off = forget the row. Default off.
- **Where:** one extra row, "Deinterlace", at the end of the existing Framing menu/section
  (web framing menu, phone FramingSection, TV framing card), showing on/off. Not offered for HDR.
- **Filter: a single-frame comb filter, same maths on both surfaces.** It runs in GLSL at source-line resolution.
  - For each output pixel, take its source line `L` and the lines `a = L-1`, `b = L`, `c = L+1`, sampled at line
    centres and clamped at the edges.
  - `y()` is luma: `dot(rgb, vec3(0.299, 0.587, 0.114))`.
  - `comb = (y(b)-y(a)) * (y(b)-y(c))` (positive when `b` sticks out from both neighbours the same way).
  - `out = mix(b, (a+c)/2, smoothstep(T*0.5, T*2.0, comb))` with `T = 0.0025`, which is about 13/255 on each side.
  - Static areas keep full resolution. Combed areas take the other field's interpolation. The frame rate is unchanged.
  - `sourceLines` is a uniform: the decoded height in lines. If the pipeline stretched the texture vertically (media3
    expands height when pixelWidthHeightRatio < 1), line centres are still `(L+0.5)/sourceLines`.

## Android: shape
- **`DeinterlaceEffect` (`core/playback`):** a media3 `GlEffect`, modelled on `CasSharpenEffect.kt`.
- **Two video renderers.** media3 sends `MSG_SET_VIDEO_EFFECTS` to every video renderer. A renderer builds its
  effects graph on its first enable and never drops it (verified: `MediaCodecVideoRenderer` never nulls `videoSink`;
  `onReset` only clears `hasSetVideoSink`).
  - **Plain renderer:** ignores effects, and stays the default.
  - **Effects renderer:** disabled through the track-selector parameters until a title asks for deinterlacing.
  - So a 4K/HDR title after a deinterlaced one never goes through GL (the spike measured 4K at 56% dropped frames
    through GL).
- **Turning it on:** routes video to the effects renderer. That re-selection re-buffers at the current position, which
  is fine for a toggle and cheap during warm-up.
- **Each open:** resets to the plain renderer. `PlayerChoicesController.resolve` turns it back on from the preference.
- **CAS debug property** (`debug.mediagram.videofilter`): keeps working through the effects renderer.

## Web: shape
- **Rendering:** a WebGL canvas directly above the `<video>` (below subtitles), drawn per frame with
  `requestVideoFrameCallback`.
- **Sizing:** sized to `videoWidth × videoHeight`, and given the same framing styles as the video (`object-fit` applies
  to canvas).
- **When it runs:** only while on; hidden and the loop stopped when off. No WebGL means no row.

## Phases
- [x] Android: effect + two renderers + preference + TV/phone rows + tests
- [x] Web: canvas renderer + menu row + preference + tests; cue layer takes subtitle-style's size/backing rule
- [x] Version 0.144.0 (0.148.0 on main), changelog; web 3949 tests + typecheck + lint, Android unit suites + assembleRelease
- [x] Emulator check (Google TV AVD, Roseanne S3E5): route on/off, shader compiled and drew, combs gone on a moving head; GL path ~8% darker there
- [ ] Box check (box was on spieltisch 0.145.3 at merge; needs a build above it)
- [ ] Web check in the real player with a combed title (stub harness serves no media)

## Known limits
- The GL path is ~3.6% darker than the direct path (spike report `plans/reports/tv-video-sharpen-spike-261010-1615-report.md`); only titles with the switch on pay it.
- Not bwdif quality: no temporal neighbours, 25 fps stays 25 fps. Re-encoding remains the better fix for a whole show.
