# Phase 05 report — Android quick subtitle toggles, captions key, Settings › Profile

Branch `worktree-agent-ab1817fa3f2153ede`, fast-forwarded to main `ed7f2509` before any edit (the worktree started two commits behind).

## What shipped

- **Captions key.** `TvKeyAction.ToggleSubtitles`; `tvKeyAction` answers `Key.Captions` first, next to `MediaNext`, so it holds with controls hidden or up, panel open, notes open, up-next shown and nothing controllable. `TvPlayerRemote` toggles via `PlayerViewModel.toggleSubtitles()` and shows the controls (PlayPause landing); a held key is one toggle. `Key.Captions` exists in this Compose version (compiles).
- **CC buttons.** Phone/tablet: `GlyphButton("CC")` before the gear in `PlayerControls`, faint when off (`dimmed`), shown only when `subtitleOptions` is not empty (a regular track exists). TV: `TvToolGroup` button "CC ●" / "CC ○" before the gear, D-pad reachable. Content description "Subtitles on" / "Subtitles off" (state, not press direction).
- **Style gate split.** `PlayerSettingsSheet` gained `subtitleStyleVisible`; language rows follow `subtitleOptions`, style/sync rows follow `subtitleStyleVisible`. `TvPlayerSettingsPanel` likewise. Forced-only: style only. No tracks: neither.
- **Settings › Profile.** `SettingsSection.PROFILE` ("Who is watching and how") after Appearance; new drawable `core_designsystem_ic_settings_profile`. New `setup.ProfileSettingsViewModel` (profile name + subtitle choice, reads/writes preference `profile`/`subtitle` through `PlayerPreferences`), `ProfileSubtitleChoices` (off/de/en), `profileStatus()`. Phone: `ProfileSettingsSection` (chips, disabled without a profile, web's hint lines). TV: `TvProfileBlock` (radio rows like the cache budget). Index status = profile name, or "Nobody chosen". A separate VM rather than growing `SettingsViewModel` (already over 200 lines, different concern).
- Write is validated against the offered set, refused with no profile, and shown only after the write is accepted and the same profile is still chosen. Stored value `off` is written literally, as the web does.

## Deliberate notes

- `subtitlesOn`/`hasSubtitles` are derived inline in `PlayerScreen` and `TvPlayerScreen` (one line each): the shared place for it (`feature:player`, `ui-common/ui/player`) is outside this phase's ownership.
- `SettingsChip` got an `enabled` parameter; `GlyphButton` a `dimmed` one.
- `ui-mobile` now has `debugImplementation(ui.test.manifest)` so `createAndroidComposeRule` has a host (same as ui-tv); `feature:setup` depends on `:core:model` for `Profile`.
- The web's Profile panel also has "Switch profile"; Android has no profile picker in Settings yet, so only name + Subtitles (per the phase). Not done: profile switching from Settings.

## Tests

- `TvPlayerCaptionsKeyTest`: Captions in 7 states (hidden, up, seek bar, panel, notes, up-next, cannot-control).
- `ProfileSettingsViewModelTest`: no profile -> Nobody chosen, off, write refused; write stores (`alice`,`profile`,`subtitle`)=de; `fr` refused; stored `en` read back, stored `xx` ignored; refused write leaves row unchanged.
- `TvPlayerSubtitleGatesTest`: forced-only -> style section, no language rows, no CC; no tracks -> neither, no CC; regular track -> CC off, press -> on.
- `PlayerSettingsSheetGatesTest` (ui-mobile, Robolectric): forced-only, both, neither.
- Existing fixtures (MobileApp, LibraryFlow, SettingsPanes, SettingsProfileRetry, TvApp) hand back a `ProfileSettingsViewModel` mock via `profileSettingsModel()` helpers.
- `./gradlew testDebugUnitTest :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin` green. There is no detekt task in this project (the brief's `detekt` does not exist); lint is what `scripts/check.sh` runs.

## Docs

`docs/system-architecture.md`: Settings sections (Profile added) and a TV-deviation bullet for the captions key / CC button / gate split. Versions untouched; `project-changelog.md` untouched.

## Changelog entry

**Added**

- Android: a CC button in the phone/tablet transport row and the TV tool row turns regular subtitles on and off, and the remote's captions key does the same from any player state, showing the controls briefly. The button appears only for a title with a regular subtitle track, and the key does nothing (and remembers nothing) on a title without one; forced lines keep following their own rule.
- Android: Settings has a Profile section showing who is watching and that profile's default subtitle language (Off, German, English), the same preference the web player's Profile panel writes. It is disabled until a profile is chosen.

**Changed**

- Android: the player settings' size, backing and sync rows now show for any title with a subtitle track that can show, forced-only titles included; the language rows still need a regular track.

## Review fixes (second commit)

- `SubtitleChoiceController.toggle()` returns early before settling or without a regular track, so the captions key never files `off` for a forced-only, subtitle-less or still-loading title (tests added).
- Phone transport row is a `FlowRow` (wraps at 360/411dp instead of squeezing the gear/CC/Next to 0dp); width tests at both widths for a film and an episode with Next.
- `PlayerChoices.ccVisible` / `subtitlesOn` replace the two inline derivations; `PROFILE_SCOPE`, `SUBTITLE_PREFERENCE`, `SUBTITLE_OFF` live in `core:data` beside `PlayerPreferences` and are used by both the player and the profile settings (`SUBTITLES_OFF` aliases it).
- Sheet test moved to the v2 empty rule; no-subtitles TV test waits for the title line. Screens held at main's size except `SettingsControls.kt` (+1, the `alpha` import).
- Changelog: the captions key is now a no-op where no regular track exists.

## Device checks left to the lead

keyevent 175 on the box (cues toggle, controls appear briefly, CC reachable by D-pad, next lesson keeps state); tablet CC, hidden on a title without subtitles; Settings › Profile on both, walk the five-row TV index (navigate only; change the subtitle row on the test profile only, with go-ahead).

## Unresolved

- Content description is state-worded ("Subtitles on/off"); the stats button is press-worded. Flip if the lead prefers press direction.
- Web hint under the row is shown on TV only when a profile is chosen, plus a lead-in line; wording may want a device look.
