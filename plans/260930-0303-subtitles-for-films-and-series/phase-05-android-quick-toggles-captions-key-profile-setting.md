# Phase 05 — Android: quick subtitle toggles, the remote's captions key, profile subtitle setting

## Context links
- Phone/tablet transport row: `android/ui-mobile/src/main/kotlin/ui/player/PlayerControls.kt:52-58` (params), `:112-157` (row: skip, play, skip, stats, Next, gear `GlyphButton` at `:156`); 167 lines.
- TV: `android/ui-tv/src/main/kotlin/ui/tv/player/TvToolGroup.kt:17-65` (Notes, speed, gear, stats), `TvPlayerKeys.kt:10-58` (actions), `:115-168` (`tvKeyAction` table; `MediaNext` answered first at `:124`), `TvPlayerRemote.kt:75` (`onKey`), `TvPlayerScreen.kt:159` (`onPreviewKeyEvent`). `KEYCODE_CAPTIONS` is handled nowhere today.
- Settings: `android/ui-common/src/main/kotlin/ui/settings/SettingsSections.kt:13-18` (four sections), phone `ui-mobile/.../settings/SettingsIndex.kt:59,98`, `SettingsPage.kt:76-100`, `SettingsScreen.kt:122`; TV `ui-tv/.../system/TvSettingsIndex.kt:41,82`, `TvSettingsPanes.kt:67`, `TvSettingsScreen.kt:101-155`.
- Web reference: `web/public/lib/catalog/settings-page.js:98-115` (Profile panel) + the Subtitles row added in phase 03; web 'c' key (`player-keys.js:80-81`) and its remembering toggle (phase 03).
- Behaviour it wires: `SubtitleChoiceController.toggle()`, `subtitleStyleVisible`, `PlayerViewModel.toggleSubtitles()`, preference scope `profile` (phase 04); rule text `plan.md` "Playback rule".
- Style gates today hide size/backing/offset unless a regular track exists: `android/ui-mobile/src/main/kotlin/ui/player/PlayerSettingsSheet.kt:81-91`, `android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerSettingsPanel.kt:80-91` (red team: assumption-destroyer F3, scope-critic F2).
- Profile-roles plan phase 07 notes Android Settings shows no profile at all — this phase closes that part of the gap (name line), nothing more.

## Overview
Priority P2. Effort 1d. Version: next **minor**. Status: pending. Depends on phase 04 and installs to devices together with it. Parallel-safe with phase 06 (disjoint files).

## Key decisions
- **What a toggle does (user decision 2026-09-30):** CC, 'c' and the captions key switch **regular** subtitles only. Off leaves a forced track showing by its own rule. On = last regular this session ?? profile preference ?? regular in the audio language ?? first regular.
- **Phone/tablet:** a "CC" `GlyphButton` in the transport row just before the gear, shown only when the title has a regular (non-forced) track; its state reads on while a regular track shows. One press = `toggleSubtitles()`.
- **Style controls:** the size/backing/offset section shows whenever any track can show (`subtitleStyleVisible`), forced-only titles included; the language rows still need a regular track.
- **TV:** the same CC button in `TvToolGroup` before the gear (D-pad reachable), and `KEYCODE_CAPTIONS` → `TvKeyAction.ToggleSubtitles`, answered first like `MediaNext` (`:124`), so it works in every state — controls hidden or up, settings panel or notes open. It toggles and brings the controls up briefly so the CC state is visible — the existing "show briefly" path `SeekByAndShowControls` uses.
- **Settings:** a fifth section, **Profile** ("Who is watching and how"), on phone and TV: the chosen profile's name (parity with the web's Profile panel) and "Subtitles: Off / German / English" (+ the web's hint line). Per profile, stored as preference (`profile`, `subtitle`). Not in Appearance: that section is device-wide.

## Requirements
- CC button hidden for a title with no regular tracks; content description "Subtitles on/off".
- `PlayerSettingsSheet.kt:81-91` and `TvPlayerSettingsPanel.kt:80-91`: split the gate — `SubtitleSection` when `subtitleOptions` is not empty, `SubtitleStyleSection`/`TvSubtitleStyleSection` when `subtitleStyleVisible`.
- `Key.Captions` if the Compose version exposes it, else `Key(KeyEvent.KEYCODE_CAPTIONS)` — verify when implementing.
- Profile section disabled with "Choose a profile first" when none is chosen; a change writes through `PlayerPreferences`/core preferences (device-local until phase 08 syncs it).
- New section needs an index icon (reuse a designsystem person/profile drawable if present, else add one vector) and an index status line (profile name).
- Files stay ≤ 200 lines (PlayerControls at 167 → the button is ~10 lines).

## Architecture
```
PlayerControls CC ─┐
TvToolGroup CC ────┼─> PlayerViewModel.toggleSubtitles() ─> SubtitleChoiceController.toggle() (remembers per show)
KEYCODE_CAPTIONS ──┘   via tvKeyAction → TvPlayerRemote
Settings › Profile › Subtitles ─> preferences(profile_id, "profile", "subtitle") ─> read at next player open
```

## Related code files
- Modify: `android/ui-mobile/src/main/kotlin/ui/player/PlayerControls.kt` (+ its caller passing `subtitlesOn`, `hasSubtitles`, `onToggleSubtitles`), `android/ui-mobile/src/main/kotlin/ui/player/PlayerSettingsSheet.kt`, `android/ui-tv/src/main/kotlin/ui/tv/player/TvPlayerSettingsPanel.kt`, `android/ui-tv/src/main/kotlin/ui/tv/player/TvToolGroup.kt`, `TvPlayerKeys.kt`, `TvPlayerRemote.kt`, `android/ui-common/src/main/kotlin/ui/settings/SettingsSections.kt`, `android/ui-mobile/src/main/kotlin/ui/settings/SettingsIndex.kt`, `SettingsPage.kt`, `SettingsScreen.kt`, `android/ui-tv/src/main/kotlin/ui/tv/system/TvSettingsIndex.kt`, `TvSettingsScreen.kt`, the settings view model in `android/feature/setup/src/main/kotlin/` that feeds these screens.
- Create: `android/ui-mobile/src/main/kotlin/ui/settings/ProfileSettingsSection.kt`, `android/ui-tv/src/main/kotlin/ui/tv/system/TvProfileBlock.kt`, possibly a profile drawable in `core/designsystem`.
- Tests: `TvPlayerKeys` table test (Captions in each state), settings view-model test (FakeCore), existing screenshot/UI tests updated where they enumerate sections.
- Docs: `docs/system-architecture.md` (TV remote keys, settings sections), `docs/project-changelog.md`.

## Implementation steps
1. `TvKeyAction.ToggleSubtitles` + table row + remote dispatch (toggle, show controls briefly); table test.
2. CC in `TvToolGroup` and in `PlayerControls`; wire from the player screens to `toggleSubtitles()` and the controller's state; split the style gates.
3. `SettingsSection.PROFILE`; exhaustive `when`s updated (compiler lists them); phone section + TV block; view-model read/write of (`profile`, `subtitle`).
4. `./gradlew test detekt`; bump by pattern; changelog.

## Todo
- [ ] captions key + table test
- [ ] CC buttons phone/tablet and TV
- [ ] style section follows `subtitleStyleVisible` (forced-only included)
- [ ] Profile section phone + TV, preference read/write
- [ ] tests, detekt, manifests, changelog

## Success criteria
- Unit tests green; detekt clean.
- TV box (`192.168.0.35:5555`, benchmark build), test profile, during a lesson with subtitles: `adb -s 192.168.0.35:5555 shell input keyevent 175` toggles cues on/off and briefly shows the controls; the CC button is reachable with the D-pad and does the same; the next lesson keeps the state.
- After phase 06/07 put a forced-only title in the channel: with German audio its forced lines show while CC reads off; the settings panel offers size/offset but no language rows.
- Tablet (`ANDROID_SERIAL=caad49da`), test profile: CC in the transport row toggles; hidden on a title without subtitles.
- Settings → Profile shows the name and the Subtitles row on both devices. Changing the row only on the test profile, with the user's go-ahead (profile data spreads once phase 08 syncs; navigation-only walks otherwise — memory: device test walks must not change settings).

## Tests
| Level | What |
|---|---|
| Unit | `tvKeyAction(Key.Captions, …)` → `ToggleSubtitles` with controls hidden, controls up, panel open, notes open, up-next shown |
| Unit | settings VM: no profile → disabled; write stores (`profile`,`subtitle`) |
| UI (Compose) | sheet and TV panel: forced-only state → style section, no language rows; no tracks → neither |
| Device | TV keyevent 175, D-pad CC, tablet CC |

## Risk assessment
| Risk | L × I | Mitigation |
|---|---|---|
| The TV box remote has no captions key | Med × Low | CC button covers it; keyevent 175 proves the handler |
| A fifth settings section breaks the TV two-pane focus map | Low × Med | `TvSettingsPanes.kt:67` builds requesters from `entries`; walk it on the box (navigate only) |
| A setting changed on the real household profile | Low × Med | Test profile only; ask first |

## Security
UI only; preference values validated against the offered set (`off|de|en`) before writing.

## Rollback
Reinstall the previous APK; a stored `profile/subtitle` preference is ignored by older builds.

## Next
Phase 08 makes the profile preference follow the viewer to other devices.
