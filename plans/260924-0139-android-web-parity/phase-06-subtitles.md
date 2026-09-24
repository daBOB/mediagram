# Phase 06: Subtitles — language, size, backing, timing, per show

## Context links
- `web/src/assets.ts:35-51`, `web/src/routes.ts:142,612-617` (VTT from index `assets kind='subtitle'`)
- `crates/mediagram/src/course/sidecars.rs` (source: `{stem}.vtt` sidecars; nothing extracted from containers)
- `web/public/lib/transport.js:368-384` (default: remembered `off` respected → remembered lang if present → **first track on**)
- `web/public/lib/subtitle-panel.js:17-39,89-156` (sizes 80/100/115/135 % "Small/Normal/Large/Larger", default 100; backings shadow (default)/box/none; nudge 0.1 s, ±30 s, Reset; prefs `cue-size`, `cue-backing`, `cue-offset`)
- `web/public/lib/subtitle-style.js:53,77-121` (`cueStyle`, `shiftedTimes`: offset absolute, never before own start)
- `web/test/subtitle-style.test.ts` (cases to port)
- Phase 01: `MediaSet.subtitleLanguages`, `CoreClient.setText(setId,"subtitle",lang)`
- `android/ui-mobile/src/main/kotlin/PlayerScreenParts.kt:48-64` (`Video()` box sized to aspect ratio)

## Overview
Priority P1 · Status pending.

## Key insights
- Subtitles on both surfaces are **only** the index's VTT text. The prior plan's
  PGS/bitmap worry does not apply; embedded container tracks are not offered on
  web, so Android disables ExoPlayer's text renderer to match (no surprise
  forced tracks).
- ExoPlayer has no subtitle offset. Rendering the parsed cues ourselves in
  Compose gives offset, size and backing directly, needs no `media3-ui`
  (View) dependency, and never re-prepares the player. Parse with media3's
  `WebvttParser` (`androidx.media3.extractor.text.webvtt`, `@UnstableApi`,
  shipped with exoplayer's extractor dependency) — no hand-written VTT parser.
  `[UNVERIFIED]` exact class availability at 1.10.1: confirm first; fallback is
  a small parser for the subset the uploader writes.
- Default is **first track on**, not Off (web decision; the old Android plan said Off — superseded).
- Offset is absolute (applied to the parsed times, never cumulatively); both
  ends clamp at 0 and end never precedes start (port `shiftedTimes` exactly).

## Requirements
- Sheet "Subtitles": Off + each language (display name); section hidden when no languages.
- "Subtitle style": size (4), backing (3), timing −/+ 0.1 s with value `+0.3s`, Reset; range ±30 s.
- All remembered per show with web names/values; `subtitle=off` remembered as off.
- Cues drawn inside the visible video rectangle, bottom-centred, above the
  control bar when controls show.

## Architecture
open → VM picks language (rule above) → `setText` via core (IO) → `WebvttParser`
→ `SubtitleTrack(cues: List<TimedCue>)` held in VM → Compose `SubtitleLayer`
reads player position (existing ticker, 100 ms while cues active) →
`activeCues(track, positionMs, offsetMs)` pure → styled `Text` with shadow or box.

## Related code files
Create:
- `android/feature/player/src/main/kotlin/SubtitleChoice.kt` (default rule, pure) + test
- `android/core/playback/src/main/kotlin/SubtitleTrack.kt` (parse via WebvttParser → `TimedCue`)
- `android/core/playback/src/main/kotlin/ActiveCues.kt` (port `shiftedTimes`, active-at) + test
- `android/core/playback/src/main/kotlin/CueStyle.kt` (size %, backing enum; port `cueStyle` values) + test
- `android/ui-mobile/src/main/kotlin/SubtitleLayer.kt`, `SubtitleSection.kt`, `SubtitleStyleSection.kt`
Modify:
- `PlayerChoices.kt`, `PlayerViewModel.kt` (or split helper), `PlayerSettingsSheet.kt`, `PlayerScreen.kt` (layer), `PlayerFactory.kt` (disable `C.TRACK_TYPE_TEXT`)
- `android/core/data/src/main/kotlin/CoreClient.kt` consumers only if a helper is needed

## Implementation steps
1. Confirm `WebvttParser` on the classpath; else write `VttCues.kt` (timestamps + text, strip tags).
2. `SubtitleChoice` + tests (off remembered; lang remembered present/absent; none → first).
3. `ActiveCues` + `CueStyle` + tests ported from `subtitle-style.test.ts`.
4. VM loads text off-main on open and on language change; failure → no subtitles, logged, player unaffected.
5. `SubtitleLayer` in the video box; text sizes from base 18 sp × %; box = black 75 %.
6. Sheet sections; reset-on-open of track/offset state.
7. Device: a course lesson with VTT: on by default; switch Off; next lesson stays Off; nudge +1.0 s visibly shifts.

## Todo
- [ ] parser decision
- [ ] SubtitleChoice, ActiveCues, CueStyle + tests
- [ ] loading + layer
- [ ] sheet sections + persistence
- [ ] text renderer disabled
- [ ] check.sh, bump, changelog, device run

## Success criteria
- Web and phone pick the same default for the same set and profile.
- Offset ±30 clamp, clamp-at-0 and end ≥ start pinned by tests.
- No subtitle text in letterbox bars (Fit) or off-screen (Fill, after phase 08 — recheck there).

## Risks
- Large VTT (feature film ~150 KB) parse on main thread → parse on `Dispatchers.Default`.
- Position ticker cost: tick only while a track is loaded and playing.
- Framing (phase 08) changes the box; layer reads the same geometry `Video()` uses.

## Security
VTT markup is rendered as plain text (tags stripped); no HTML/links interpreted.

## Next steps
08 must re-verify cue placement under Fill/16:9/4:3.
