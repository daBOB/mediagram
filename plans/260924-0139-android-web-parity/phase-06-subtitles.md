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
- [x] parser decision — media3 1.10.1 ships `WebvttParser` in `media3-extractor`, already an `api` dependency of `media3-exoplayer`; no hand-written parser needed
- [x] SubtitleChoice, ActiveCues, CueStyle + tests
- [x] loading + layer
- [x] sheet sections + persistence
- [x] text renderer disabled
- [x] check.sh, bump, changelog
- [x] device run 2026-09-24 (see Device check)

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

## Deliberate differences
- No `c`-key equivalent. The web's `c` toggles subtitles off and back to
  whatever they were, without persisting the toggle — a keyboard shortcut a
  touch device has no equivalent gesture for, the same reasoning that
  already excludes the web's other keyboard-only features on this surface
  (plan.md's own list: A-B loop, frame step, number jumps, `[`/`]`). The
  sheet's "Off" row is the one control this surface offers, and it does
  persist, matching every other row in the same section.
- Implementation is split into two sibling controllers,
  `SubtitleChoiceController` (language, cues) and `SubtitleStyleController`
  (size, backing, offset), where the phase's own file list named one
  `SubtitleChoice.kt`/helper — the file guideline (200 lines) would not fit
  both concerns in one class once each carries its own reset/resolve/remember
  machinery, the same reason `AudioChoiceController` is already split out of
  `PlayerChoicesController` rather than folded into it.
- `SubtitleTrackSource` (`core:playback`) is a small addition the phase's
  file list did not name outright, covered by its own "`CoreClient.kt`
  consumers only if a helper is needed" — it is that helper: fetches
  `CoreClient.setText` and parses the result, kept in `core:playback` rather
  than `core:data` because `core:data` cannot depend on `core:playback`
  (the parser it calls lives there) without a cycle.

## Device check (2026-09-24, tablet, 0.46.0, throwaway `test` profile)
The earlier note here was wrong: the 162 local `subtitle` rows belong to
Geldhochschule, which *is* on the phone. The channel index the phone
installs simply carries no `assets` rows at all (0), so the check ran on a
fixture: the tablet's installed `library.db` with those real rows copied
in, applied after the app's launch refresh (which re-downloads and
reinstalls the index every time) and restored to the channel copy after.
- Einführung: section shows `und` as "Subtitles", on by default; cues draw.
- Off → saved `test / show:Geldhochschule / subtitle=off`; lesson 2 opens
  with Off selected and draws nothing.
- On + Large + Box + `+1.0s` → saved as four separate values; lesson 1
  reopens with all of them. At 3:25 the cue ending 3:24.237 is still drawn:
  the offset shifts later, as on the web.
- Found and fixed: with the bar shown the cue sat on the progress bar (a
  fixed 96dp clearance). Now measured: lifted by the part of the picture
  the bar covers, back to the bottom when it hides. Also settles review L2.
- The `andre` profile's preferences were not touched.

## Review fixes (reports/code-reviewer-260924-2235-phase-06-subtitles-report.md)
- M1 fixed: nothing is chosen until languages and preferences have both
  answered; re-picking the row already on does not refetch. Covered with a
  gated preference fake.
- L1 fixed: size and backing flags and writes are separate.
- L2 fixed by the measured placement above.
- L3 not changed: while paused the tick writes an unchanged position, which
  neither recomposes nor recomputes cues; the cost is one read per 100ms.
- L4: tests split into choice, lifecycle and style files, all under 200.
- Also found: `SubtitleTrackTest` hung forever on the bare android.jar —
  `WebvttParser` skips the header with `while (!TextUtils.isEmpty(...))`
  and the stub answers `false` always. Runs under Robolectric now; the app
  itself was never affected.

## Next steps
08 must re-verify cue placement under Fill/16:9/4:3 (placement is measured
against the bar now, so framing changes should carry through).
