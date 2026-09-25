# Phase 05: Audio track chooser, remembered per show

## Context links
- `web/public/lib/audio-chooser.js:148-202` (label `Language · title · channels · codec`; channels mono/stereo/5.1/7.1/`Nch`; fallback `Track N`; pick remembered language → file default → first)
- `web/public/lib/player.js:872-889` (remembered by **language**, pref name `audio`)
- `web/public/lib/language-label.js:17-37` (`und` → caller text)
- `web/test/audio-chooser.test.ts` (cases to port)
- `android/ui-mobile/src/main/kotlin/PlaybackStatRows.kt:64` (decoded audio format already readable)
- Phase 04: `PlayerChoices`, settings sheet, reset-on-open

## Overview
Priority P1 · Status pending · 312 of 566 sets have more than one audio language (prior plan count).

## Key insights
- Web can only switch audio by restarting a server conversion. ExoPlayer
  switches inside the decoder with a `TrackSelectionOverride`: no refetch, no
  re-encode. Same decision (what is offered, labelled, remembered), cheaper
  mechanism — not a behavioural difference, note it in the changelog only.
- Remember the **language tag**, never the ordinal: episode 2's track order is not episode 1's.
- Tracks arrive with `onTracksChanged`, not at `prepare()`; the remembered
  language is applied when the first `Tracks` for this media item arrive.
- Singleton player: clear audio overrides on every `open` (phase 04 rule).

## Requirements
- Sheet section "Audio" only when > 1 audio track the device can actually decode.
- Labels exactly per `audio-chooser.js`: language name, `und`/blank →
  track label → `Track N`. **Corrected against the web reference itself**
  (found in review, 2026-09-24): the web's own `languageLabel` fixes
  `Intl.DisplayNames(["en"], …)` — always English, never the browser's
  locale — so "in the device language" (this section's original wording)
  was wrong against the thing it was supposed to match; the app now fixes
  English too, the same as `PlaybackStatRows`' own language line.
- Choosing writes `audio=<lang>`, skipped for a pick with no usable
  language (blank or `und` — the web's own `player.js` change handler
  writes only a real one) so it can never blank a good choice already on
  record. Opening applies the remembered language if it names a track this
  device can decode; otherwise nothing is pinned and ExoPlayer's own
  selection (already accounting for what the device supports) stands —
  never a guess of this app's own.
- A track with no decoder on a stock build (DTS, TrueHD — there is no
  FFmpeg extension here) never appears in the menu and is never pinned,
  even if it is what a viewer once chose for this show.

## Architecture
`AudioOptions.kt` (pure, `:core:playback`): `Tracks` → `List<AudioOption(groupIndex, trackIndex, label, language, selected)>`.
VM: on tracks changed for the current item and not yet applied → select
remembered language via `trackSelectionParameters.buildUpon().setOverrideForType(...)`.
To keep `AudioOptions` JVM-testable, it takes a small plain list built from
`Format` fields (language, label, channelCount, sampleMimeType), not `Tracks` itself.

## Related code files
Create:
- `android/core/playback/src/main/kotlin/AudioOptions.kt`, `ChannelLayoutLabel.kt`
- `android/core/playback/src/test/kotlin/AudioOptionsTest.kt` (port audio-chooser cases)
- `android/ui-mobile/src/main/kotlin/AudioSection.kt` (sheet section)
Modify:
- `android/feature/player/src/main/kotlin/PlayerChoices.kt`, `PlayerViewModel.kt` (or its split helper), `DefaultPlayerHandle.kt` (reset overrides on open)
- `android/ui-mobile/src/main/kotlin/PlayerSettingsSheet.kt`

## Implementation steps
1. `AudioOptions` + label rules + tests (two languages, one track → empty, `und`, title-only, 5.1/stereo/odd count → `Nch`).
2. Listener: first `onTracksChanged` per media item applies remembered language.
3. Sheet section; selection writes preference and applies override.
4. Reset audio override in the open path.
5. Device: two-language film, switch mid-play; next episode of a two-language
   show opens in the chosen language; stats overlay `reads` row shows no refetch burst.

## Todo
- [x] AudioOptions + tests
- [x] apply-on-tracks + reset-on-open
- [x] sheet section + persistence
- [x] check.sh, bump, changelog
- [x] device run 2026-09-24 on the tablet (0.45.0): Blade: Trinity lists "German · AC3 5.1 · 5.1 · ac3" and "English · EAC3 6.1 · 7ch · eac3" as the web labels them; a pick switches the audio at once and is saved (`key:tmdb-movie-36648 / audio / de`); leaving and reopening keeps the section with the remembered track checked; a second title (Star City S1E4) lists English and German with the file default checked; every sheet row sits above the navigation bar.

## Success criteria
- Tests green; device switch is gapless (picture never stops).
- Single-track film shows no Audio section.

## Risks
- Some MKVs tag both tracks `und`: labels fall back to track titles; remembered `und` then
  matches the first `und` — acceptable, same as web.
- Tunnelled/offloaded audio re-init may blip audio ~100 ms; acceptable.

## Deliberate difference (device review, 2026-09-24)
`Format.language` already shortens ISO 639-2 (`deu`, `ger`) to ISO 639-1
(`de`) by the time it reaches this app — media3's own container parsing,
not this app's code. The web's server-side prober keeps ffprobe's own tag
verbatim and stores whichever length it happened to hand back. A
preference this app writes is therefore always two-letter; one the web
wrote is whatever ffprobe used for that file. Harmless today — preferences
are per device and unsynced (see plan Facts) — but a future sync of this
table must normalise both sides onto one form before comparing them, or a
viewer's choice made on one surface will silently fail to match on the
other.

## Security
None.

## Implementation notes (deviations from the file list above)
- `AudioChoiceController.kt` (new, `feature:player`) holds the
  Tracks-listening and race-guarding logic this file's Architecture section
  describes; `PlayerViewModel.kt` and `DefaultPlayerHandle.kt` were both
  already at or past the project's 200-line guideline, so this is a third
  split-out controller alongside `PlayerChoicesController` and
  `PlayerMarksController`, not new scope. The real-`Player` mechanics
  (reading a `Tracks` into plain facts, building and clearing an override)
  are further split into `AudioTrackSelection.kt`, which is what actually
  kept this one under the guideline. `PlayerScreen.kt` crossed 200 lines
  the moment its `PlayerSettingsSheet` call site grew two parameters;
  `shouldStopOnDispose` moved to `PlayerScreenLifecycle.kt` to bring it
  back under.
- The override reset on open lives in `AudioChoiceController.reset()`
  (called from `PlayerChoicesController.reset()`, called from
  `PlayerViewModel.open()`), not inside `DefaultPlayerHandle.openOn()` —
  same effect, chosen because `DefaultPlayerHandle.kt` had no line budget
  left and `PlayerViewModel.open()` already resets every other per-show
  controller at exactly that point.
- The listener is attached directly to the live `Player` from
  `AudioChoiceController` (the way media3's own Compose progress state
  does), not through `DefaultPlayerHandle`'s permanent bridge — that one is
  process-lifetime and forwards to whichever `PlayerViewModel` is current;
  adding a callback to it for one controller's use would have meant either
  growing an already-full file or leaving a caller nobody else needs.
  `release()` detaches it when this controller's own `PlayerViewModel` is
  cleared, since — unlike the permanent bridge — this one does not outlive
  a single screen visit.
- `PlayerScreen.kt` needed its `PlayerSettingsSheet(...)` call site updated
  for the sheet's two new parameters; not listed in the file list above
  since that list predates the sheet's own signature change, but there was
  no way to wire the section without it.
- `PlayerReopenTest.kt`'s `PreparablePlayer` fixture captured only the last
  `Player.Listener` added to its mock; once `AudioChoiceController` also
  attaches one to the same singleton, that was the wrong one. Fixed to
  collect and dispatch to every listener added, which is what a real
  `ExoPlayer` does anyway.

## Device review and fixes (2026-09-24)
Full findings: `reports/code-reviewer-260924-1716-phase-05-audio-report.md`.
Device run found the chooser working on the first title, then two bugs:
the Audio section vanished on any reopen, and the sheet sat under the
navigation bar. Both are fixed, along with a decodability gap the review
found before it could reach a device: this app has no FFmpeg extension, so
a remembered or picked track this build cannot decode must never be
pinned. Summary of the fixes (detail in the report):
- **Critical** — `ExoPlayerImpl` fires a synthetic `Tracks.EMPTY` while
  reloading for a new title, read as "no audio" and settled on
  permanently; every title after the first, and any reopen (`stop()`
  already clears the session), silently lost its menu. Fixed:
  `AudioChoiceController.handleTracksChanged` ignores an event with no
  groups at all.
- **High** — a remembered or default track could be pinned without
  checking `Tracks.Group.isTrackSupported`, and a `TrackSelectionOverride`
  was always built from a fresh `player.currentTracks` lookup rather than
  the `Tracks` snapshot a caller's facts actually came from. Fixed:
  unsupported tracks are excluded at extraction; nothing is ever pinned
  with nothing usable remembered (ExoPlayer's own selection, read via
  `isSelected`, marks the row instead — the "file default" pin this
  controller invented is gone); overrides build from the captured
  `Tracks`, checked to still be an audio group.
- **Medium** — codec names now match ffprobe's (`ac3`, not `AC3`);
  language names are fixed English, matching the web's own choice (a spec
  correction, see Requirements); a pick with no usable language is never
  saved (see Requirements); the ISO 639-2→639-1 table this phase's tests
  needed was removed from production code once real Format objects turned
  out to arrive already shortened (see Deliberate difference above).
- **Low** — a pick that cannot be applied no longer claims to be the
  viewer's; an override always builds from the `Tracks.Group` captured
  with a caller's facts; labels no longer depend on any locale, so no test
  needs to pin one; `release()` detaching the listener is now covered
  directly.
- **Device (b)** — the sheet's content now scrolls and pads for the
  navigation bar (`navigationBarsPadding`), so every row is reachable
  above the system bar in both orientations.

## Next steps
06 mirrors the same pattern for text.
