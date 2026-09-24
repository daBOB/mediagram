# Phase 04: Player foundation — settings sheet, per-show store, title, ends-at, retry, speed

## Context links
- `web/public/lib/preference-scope.js:30-37` (`scopeOf`: `key:<showKey>` → `show:<show>` → `set:<setId>`)
- `web/src/routes.ts:370,378` (`showKey` = poster key, same as Android `MediaSet.posterKey`)
- `web/public/lib/watch-state.js:387-393` (`PUT …/preferences`), names in use: `audio`, `subtitle`, `speed`, `framing`, `cue-size`, `cue-backing`, `cue-offset`
- `web/public/lib/transport.js:43,163-207` (speeds `[0.75,1,1.25,1.5,1.75,2]`; invalid → 1)
- `web/public/lib/player.js:600-602` (title line `[show, episodeLabel, title].join(" · ")`)
- `web/public/lib/format.js:186-192` + `player.js:463-472` (`ends HH:MM`, 24 h hand-rolled, remaining ÷ rate, blank when runtime unknown)
- Android: `android/feature/player/src/main/kotlin/PlayerViewModel.kt` (223 l.), `DefaultPlayerHandle.kt` (206 l.), `android/ui-mobile/src/main/kotlin/PlayerScreen.kt` (217 l.), `PlayerControls.kt:110-176`, `PlayerScreenParts.kt:84-88` (`CenteredError`)
- Singleton player: `android/feature/player/src/main/kotlin/di/PlaybackModule.kt:53-73`
- `android/feature/catalog/src/main/kotlin/ResumeLine.kt:48` (`episodeLabel`, already a web port)

## Overview
Priority P1 · Status done (device check pending) · The shell 05-08 and 11 plug into; speed is the
first remembered choice, proving the store end to end.

## Key insights
- Web remembers per **profile** on the host, unsynced. Android: core `state.db`
  `preferences` (phase 01), keyed by chosen profile id. No profile chosen →
  choices apply for the title and are not written (web: no profile, no writes).
- The player is app-scoped: a speed set on episode 1 is still set when a film
  opens. Every `open` must **apply the remembered value or the default**, never
  inherit. This is the single rule that keeps 05, 06, 08 correct.
- Three player files exceed 200 lines; split before adding anything.
- Web has **no retry**; a failed conversion says "pick a position to start it
  again". Phone failures are network failures: a Retry button re-opening at the
  last saved position is an Android addition — record as owed-to-web or
  phone-only per the open question.

## Requirements
- `PreferenceScope.kt` (pure port, same prefixes) + `PlayerPreferences`
  repository: `load(profileId, scope): Map<String,String>`, `remember(scope, name, value?)`.
- One settings button in the control bar → `ModalBottomSheet` with sections
  (this phase: Speed; later phases add Audio, Subtitles, Framing).
- Speed: web's six steps; the bar shows e.g. `1.5×` when ≠ 1; remembered as
  `speed` with web's value strings (`"1.5"`); invalid stored value → 1.
- Title line at top: show · S1E2 · title (web order, `episodeLabel`).
- `ends HH:MM` beside the time, 24 h, (duration − position) ÷ speed, hidden when duration unknown.
- Failed state: message + Retry (re-open same set at last saved position).

## Architecture
`PlayerViewModel.open(setId)` resolves `MediaSet` from `CatalogRepository`
(survives rotation; `LibraryPositions` keeps only the id) → scope → loads
prefs once → `PlayerChoices` state (speed now; audio/subtitle/framing later) →
applied to the `Player` after `prepare`. UI reads `PlayerChoices` from the VM
and writes through VM methods (VM owns persistence; media3 state holders still
own transport, per `feature/player` module docs).

## Related code files
Create:
- `android/feature/player/src/main/kotlin/PreferenceScope.kt`, `PlayerChoices.kt`, `PlaybackSpeed.kt`, `EndsAt.kt`, `PlayerTitleLine.kt`
- `android/core/data/src/main/kotlin/PlayerPreferences.kt` (over `CoreClient.preferences/setPreference`)
- `android/feature/player/src/main/kotlin/PlayerSession.kt` (split: ticking/progress out of VM), `PlayerHandleListener.kt` (split out of `DefaultPlayerHandle`)
- `android/ui-mobile/src/main/kotlin/PlayerSettingsSheet.kt`, `PlayerTopBar.kt` (title + back, split out of `PlayerScreen`), `PlayerFailure.kt`
- tests: `PreferenceScopeTest` (port `web/test/preference-scope.test.ts`), `PlaybackSpeedTest`, `EndsAtTest`, `PlayerTitleLineTest`, `PlayerChoicesResetTest`
Modify:
- `PlayerViewModel.kt`, `DefaultPlayerHandle.kt`, `PlayerScreen.kt` (all end < 200), `PlayerControls.kt`, `PlayerScreenParts.kt`, `di/PlaybackModule.kt` (if a binding is needed), `android/core/data/src/main/kotlin/di/DataModule.kt`
- `android/feature/player/src/test/kotlin/FakePlayerHandle.kt`, `TestPlayerViewModel.kt`

## Implementation steps
1. Pure splits of the three oversized files; tests unchanged and green.
2. `PreferenceScope` + tests; `PlayerPreferences` + DI.
3. VM resolves the set, loads prefs, exposes `PlayerChoices`, `setSpeed()`.
4. Reset-on-open: `setPlaybackSpeed(remembered ?: 1f)` in the open path; test
   that a second `open` without a stored speed returns to 1.
5. Settings sheet with Speed; bar speed label.
6. Title line, ends-at (ticks with the existing position ticker), Retry.
7. Device: set 1.5× on an episode, open the next episode (1.5×), open a film (1×).

## Todo
- [x] split PlayerViewModel / DefaultPlayerHandle / PlayerScreen
- [x] PreferenceScope + PlayerPreferences
- [x] PlayerChoices + reset-on-open
- [x] settings sheet + speed
- [x] title line, ends-at, retry
- [x] check.sh, bump, changelog
- [x] device run 2026-09-24 on the tablet (0.44.0): title line "Brooklyn Nine-Nine · S8E2 · Das Haus am See"; ends-at correct at 1× and 1.5×; the gear sheet lists 0.75×–2×; 1.5× is stored as `key:tmdb-tv-48891 / speed / 1.5` (the web's format), the next episode of that show opens at 1.5× and a film at 1×; rotating mid-episode keeps 1.5×, the ends-at and the position; set lookup 30 ms off the main thread. The first tap on search after launch worked 2 times out of 3; the miss was the first launch after install. Retry was not forced on the device (it needs the network cut); unit tests cover it.

## Success criteria
- Tests above green; `PlayerChoicesResetTest` proves no leak across titles.
- Device: speed remembered per show, not across shows; ends-at moves with speed;
  airplane mode mid-film → error + Retry resumes near the same position.

## Risks
- Sheet over the video steals taps from controls-visibility logic
  (`ControlsVisibility.kt:24-36`): keep controls pinned while the sheet is open.
- `CatalogRepository` lookup before catalog loaded (cold start straight into player
  after process death): fall back to title-less player, prefs by `set:<id>`.

## Security
Preference values are length-capped by core; nothing executed or parsed beyond known names.

## Next steps
05, 06, 07, 08, 11 all attach to the sheet and `PlayerChoices`.
