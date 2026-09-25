# Phase 12: Profile removal, list/grid shelf view, docs close-out

## Context links
- `web/public/lib/profile-picker.js:55-75` ("Rename or remove…" only removes: type the exact name → "No profile by that name." → confirm "Remove "X" and everything they have watched?")
- `web/src/state/store.ts:136-146` (rename exists server-side, **no UI calls it**; delete cascades)
- `crates/mediagram-core/src/state/profiles.rs:79-95` (`profile_named`: an unknown name in another device's sync document **creates** a local profile)
- `web/public/lib/shelf-mode.js:1-53` (`mediagram.shelfView`; device-level; **list is the default**, stored as absence; unknown → list)
- `web/public/app.js:167-175,235-241` (toggle on **Movies and Series**; courses always a list — no artwork, "a wall of initials")
- Android: `android/ui-mobile/src/main/kotlin/ProfilePickerScreen.kt:65-156`, `android/feature/catalog/src/main/kotlin/ProfileViewModel.kt:93-107`, `android/ui-mobile/src/main/kotlin/ShelfWall.kt:43-63` (fixed grid for every shelf)
- Docs: `docs/system-architecture.md` §8 ("profiles cannot be renamed or deleted on the phone"; "What it does not have yet"), `docs/superpowers/specs/2026-09-20-android-system-menu-and-playback-stats-design.md` §9, `PRODUCT.md:52-66` (stale: "keeps no watch state", "no search")

## Overview
Priority P3 · Status done · Final phase; closes the docs.

## Key insights
- Web removal is local and cascades; the profile **comes back** at the next sync
  round if any other device's document still names it (`profile_named`). Same
  on both surfaces; port as-is, say so in the confirm text's neighbourhood (docs), not invent a tombstone.
- Rename: web has no UI. Parity = remove only (pending open question 1).
- Shelf view: web default list, Movies + Series only, device-level. Android is
  grid everywhere, courses included — courses as a grid is itself a divergence
  (a wall of initials). Default pending open question 2.
- `system-architecture.md` §8 currently calls removal a deliberate difference;
  it stops being one.

## Requirements
- Picker: "Remove a viewer…" → dialog listing profiles (tap one, not type a name —
  a phone keyboard for an exact-match name is hostile; same confirm wording) →
  `CoreClient.deleteProfile` → list refreshes; removing the chosen profile returns to the picker.
- Shelf mode: plain `SharedPreferences` (not encrypted; not a secret) key
  `shelf_view`, values `list`/`grid`, default per question 2; toggle in the
  Movies and Series shelf headers; Courses always list.
- List row = existing `SetPlate`/list row components (`ListScreen.kt` row style).
- Docs: deliberate differences list (below) + stale lines fixed.

## Deliberate differences to write (spec §9 + architecture §8)
Scrub thumbnails; adaptive bitrate and needs-transcode badge; keyboard-only
controls (A-B loop, frame step, 0-9 jumps, `[`/`]`, `m`, `c`); volume memory
(hardware volume); audio switch without restart; genres filled from device
fetch; preload budget + unmetered-only; notes as a sheet on phone landscape;
subtitles from the index only (embedded tracks off, as web); Retry, double-tap,
PiP button (phone additions — owed-to-web or phone-only per question 5).

## Related code files
Create:
- `android/core/data/src/main/kotlin/settings/ShelfViewSettings.kt` + test
- `android/ui-mobile/src/main/kotlin/RemoveProfileDialog.kt`, `ShelfModeToggle.kt`, `ShelfList.kt`
- `android/feature/catalog/src/test/kotlin/ProfileRemovalTest.kt`
Modify:
- `ProfileViewModel.kt` (`remove(id)`), `ProfilePickerScreen.kt`, `WatchStateRepository.kt` (`deleteProfile`, profiles flow refresh)
- `ShelfWall.kt`, `CatalogScreen.kt`, `android/core/data/src/main/kotlin/di/DataModule.kt`
- `docs/system-architecture.md`, `docs/superpowers/specs/2026-09-20-android-system-menu-and-playback-stats-design.md`, `PRODUCT.md`, `docs/development-roadmap.md`
- `plans/260922-0124-android-web-parity/plan.md` (mark phases 1-3, 6, 7, 10 superseded by this plan)

## Implementation steps
1. `deleteProfile` through repository; VM `remove`; tests (chosen removed → picker; other removed → stays).
2. Remove dialog + confirm.
3. `ShelfViewSettings` + toggle + list rendering; courses forced to list.
4. Docs and PRODUCT.md; old plan statuses.
5. Device: create a throwaway profile "Probe", remove it; do **not** remove a real
   profile (sync would resurrect, but avoid touching the user's data). Toggle Movies to list and back.

## Todo
- [x] profile removal + tests
- [x] shelf view setting + toggle + courses list
- [x] docs + PRODUCT.md + old plan
- [x] check.sh, bump, changelog, device run

## Success criteria
- Removing "Probe" deletes its rows (core cascade test from phase 01) and it does not reappear (no other device knows it).
- Shelf mode survives restart; Courses never offer the toggle.
- `grep -n "cannot be renamed or deleted" docs/` returns nothing.

## Risks
- Removing a synced profile resurrects it — documented, matches web.
- Changing Android's default layout surprises current phone users — decided by question 2.

## Security
Removal requires explicit confirm; no data leaves the device.

## Next steps
Parity review: diff the web feature list against this plan's phase table; file anything left as a new plan.

## Implementation notes
- Removal: `WatchStateRepository.deleteProfile` (core delete, then `reload`,
  which also drops the choice the core forgot). `ProfileViewModel.remove`
  takes "Stay as I am" away when the chosen profile was the one removed.
  `RemoveProfileDialog` lists names to tap instead of the web's typed name.
- Shelf view: `ShelfViewSettings` (plain SharedPreferences, `shelf_view`,
  grid stored as absence, anything unrecognised is grid), `ShelfViewModel`,
  `offersViewChoice`/`shelfViewFor` (courses always a list), `ShelfModeToggle`,
  `ShelfList`. The film shelf's cards now pass `held`, which the web's shelf
  already showed.
- Also here, for parity with the web's Continue shelf: "Mark finished" —
  `WatchStateRepository.markFinished`, shared with `ProgressRecorder`, keeps
  this branch's rule that a title already finished keeps its first date. On
  `main` another session is changing that rule to re-stamp every time; merging
  will need that change applied to this one function.

## Device run (2026-09-25, tablet)
- Created "Probe", removed it through the dialog; gone from the picker and
  from `state.db`, and still gone after a relaunch's sync round.
- On `test`: "Mark finished" on Justice League took it off Continue (5 → 4);
  `state.db` has it watched with no position.
- Movies → List: rows with year and runtime; survived a force-stop; Tutorials
  shows a list with no toggle; back to Grid (the preference file is empty
  again). Device left on the `andre` profile.
