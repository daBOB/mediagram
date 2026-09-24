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
- Sheet section "Audio" only when > 1 audio track.
- Labels exactly per `audio-chooser.js` (Locale display name for language, in
  the device language; `und`/blank → track label → `Track N`).
- Choosing writes `audio=<lang>`; opening applies remembered lang if present,
  else leaves ExoPlayer's default (= file default, matching web).

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
- [ ] AudioOptions + tests
- [ ] apply-on-tracks + reset-on-open
- [ ] sheet section + persistence
- [ ] check.sh, bump, changelog, device run

## Success criteria
- Tests green; device switch is gapless (picture never stops).
- Single-track film shows no Audio section.

## Risks
- Some MKVs tag both tracks `und`: labels fall back to track titles; remembered `und` then
  matches the first `und` — acceptable, same as web.
- Tunnelled/offloaded audio re-init may blip audio ~100 ms; acceptable.

## Security
None.

## Next steps
06 mirrors the same pattern for text.
