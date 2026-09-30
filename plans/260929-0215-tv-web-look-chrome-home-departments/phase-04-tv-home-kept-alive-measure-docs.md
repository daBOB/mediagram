---
phase: 4
title: "TV Home kept alive under pushed frames, measured; device walk; docs"
status: in-review
priority: P2
effort: 6h
dependencies: [3]
---

# Phase 04 — Home kept alive, measured; device walk; docs

## Context links

- Finding + method: `plans/260928-0200-android-decoder-stall-recovery/reports/debugger-260928-0200-decoder-freeze-rootcause-and-closeout-report.md`
  lines 85-201 (debug ~5s of Davey frames on any return to Home; benchmark clean; Part 3 direction).
- Cause in code: `ui-tv/src/main/kotlin/ui/tv/TvLibrary.kt:109-174` — `when (top)` composes exactly
  one branch, so Home (`null ->` `:172-173`) leaves the composition whenever anything is pushed and
  is rebuilt, remeasured and replaced from zero on return; only `rememberSaveable` survives
  (`TvLibraryBranches.kt:122` `SaveableStateProvider`).
- Arrival/restore effects that must not fire while hidden: `catalog/TvWall.kt:111`,
  `TvCollectionsPage.kt:68`, `TvLists.kt:69`, `TvKeptWall.kt:128`, `TvDepartmentRows.kt:58,91,117`,
  the Home and department effects rebuilt in 02/03, `TvCatalogNav.kt` effects left after 01.
- Baseline numbers: this plan's `reports/baseline-home-return-measurements.md` (phase 01 step 0).

## Overview

Stop rebuilding Home on every return: keep the root library (chrome + current tab) composed and
laid out under every pushed frame, hidden and inert, so Back is a focus restore rather than a
full compose/measure/place. Then measure against the phase-01 baseline, walk the whole TV surface
against the tablet, and write the TV look and its remaining differences down.

## Key decisions

- **Keep alive under every pushed frame** (title, season, collection, person, genre, franchise,
  list, search, Latest, Genres, Settings/System, preloads, menu page, player) — one rule, the
  debugger's recommended direction. While covered, the home layer is:
  - not drawn (`drawWithContent` skips `drawContent`), still placed — nothing to re-place on return;
  - not focusable (`focusGroup` + `focusProperties { onEnter = cancel }`), semantics cleared
    (`clearAndSetSemantics`) so TalkBack and test finders never see it;
  - inert: `LocalTakesArrivalFocus` false (every arrival effect keyed on it re-runs on uncover
    and lands on the restore key's stop), chrome `BackHandler`s disabled, cover rotation paused;
  - frozen: its `CatalogUiState` input is the value from the moment it was covered
    (`heldWhile(covered, value)`), so position saves during playback do not recompose Home;
    uncovering applies the latest state once, incrementally.
- **Fallback, pre-decided**: if playback on the box shows stutter or TOTAL PSS 60s into a film
  grows > 30MB over the baseline, keep Home alive under every frame *except* `PLAYER` (one
  condition in `TvLibrary`), mark it with a `ponytail:` comment naming the ceiling, and record why.
- **Tab switches stay a rebuild** (`key(selected)`, by design: a new tab must not inherit the old
  one's scroll). R3 is measured for information only.
- **Tablet**: not changed here (fast device, different host). One R1 measurement on its benchmark
  build (`ANDROID_SERIAL=caad49da`) is recorded so the parity question the debugger raised has an answer.

## Requirements

Functional
- Back from any pushed frame lands exactly where it does today (plate, rail row, search, ⋮).
- A title page never loses focus to Home; D-pad from any pushed page never reaches a Home node.
- Home's scroll position, focused stop and cover film survive a round trip without restore work.

Non-functional (measured on the box, TV test profile, 3 runs each)
- Benchmark build: R1, R2, R4 → 0 `Davey!`, 0 `Choreographer … Skipped`.
- Debug build: R1, R2 → no multi-second freeze; target 0 Davey; must: `MeasurePassDelegate.remeasure`
  calls and `Choreographer.doFrame` inclusive time on return ≤ 20% of the phase-01 baseline.
- Playback PSS within the fallback bound above.

## Architecture

```
TvLibrary
  Box {
    TvHomeLayer(covered = top != null || menuOpen)          // always composed
      Modifier.coveredLayer(covered)                        // skip draw, block focus, clear semantics
      CompositionLocalProvider(LocalLibraryCovered provides covered)
        TvLibraryHomeFrame(state = heldWhile(covered, catalogState), …)
    when (top) { PLAYER, TITLE, … -> pushed frame on top;  null if menuOpen -> TvMenuPage;  null -> {} }
  }
covered true  → LocalTakesArrivalFocus false, BackHandlers off, rotation paused, input frozen
covered false → draw; effects keyed on takesFocus re-run → requestFocus(restore stop) — no remeasure
```

## Related code files

Create
- `android/ui-tv/src/main/kotlin/ui/tv/TvHomeLayer.kt` — `coveredLayer` modifier, `heldWhile`,
  `LocalLibraryCovered`.
- `android/ui-tv/src/test/kotlin/ui/tv/TvHomeKeptAliveTest.kt`.

Modify
- `ui-tv/.../TvLibrary.kt:109-174` (layer + pushed frame), `TvLibraryBranches.kt` (covered flag,
  frozen input), `chrome/TvLibraryChrome.kt` (Back handlers gated), `catalog/TvCatalogScreen.kt`
  (arrival provider gated), `catalog/home/TvHomeCover.kt` (rotation gated), the effects listed in
  Context links (add `takesFocus` to their keys where missing).
- Docs: `DESIGN.md` (TV chrome, rail, bar, focus shapes, cover remote rules; the stale masthead
  section `:487-497`; "no horizontally-scrolling rail" `:416`, `:673` — unless the tablet plan's
  docs phase already rewrote them), `docs/system-architecture.md` § Television differs (`:631+`:
  every remaining difference below) + a line on the kept-alive home layer, `docs/project-changelog.md`.

## Implementation steps

1. `TvHomeLayer.kt`; restructure `TvLibrary` dispatch; gate arrival, Back, rotation, input.
2. `TvHomeKeptAliveTest` (Robolectric, w960dp-h540dp): open a Recently added poster, press
   Down/Left/Right ×10 on the title page → focus never inside the Home root; Back → same poster
   focused and Home's `LazyListState` index unchanged; cover film unchanged after 30s covered;
   no duplicate `onNodeWithText` matches while covered. Existing `TvLibraryTest`/`TvMenuTest`/
   `TvSearchAndGenreTest` stay green.
3. `scripts/check.sh`.
4. Measure (debug, then benchmark + `cmd package compile -m speed -f`): R1 Home → Recently added
   poster → Back; R2 cover Watch now → Back → Back; R3 Movies pill → Home pill; R4 rail Settings →
   Back. Per run: `adb -s 192.168.0.35:5555 logcat -c` → repro → `logcat -d | grep -c "Davey!"`,
   `grep -c "Choreographer.*Skipped"`; `dumpsys gfxinfo com.mediagram.android reset`/read janky %.
   Debug R1/R2 also: `adb shell am profile start --sampling 1000 com.mediagram.android /data/local/tmp/home-return.trace`
   → repro → `am profile stop` → `adb pull`; parse the SLOW v3 trace (script in the scratchpad,
   format in the debugger report) for remeasure/placeAt counts and doFrame inclusive time.
   PSS: `dumpsys meminfo com.mediagram.android | grep TOTAL` 60s into a film, before/after.
   Debug installs over benchmark keep the sign-in (same debug key, `android/app/build.gradle.kts:24-42`);
   if an install ever asks to uninstall, stop. Reinstall benchmark + compile speed at the end.
5. Apply the fallback if the bound is crossed; re-measure.
6. Full D-pad walk (never OK on a Settings choice): rail rows, every pill, Home sections, every
   department, each pushed frame and Back to exit; final sheet `reports/tv-vs-tablet-overview.png`.
7. Docs; `reports/tv-web-look-verification-report.md` (numbers before/after, sheets, differences,
   follow-ups); bump patch; changelog; commit.

## Todo

- [x] home layer kept alive, inert and frozen while covered
- [x] TvHomeKeptAliveTest; all ui-tv tests green; check.sh green
- [ ] debug + benchmark measurements vs baseline; PSS during playback; tablet R1 recorded
- [ ] fallback applied only if needed (one-line `PLAYER`-only switch left ready in `TvLibrary.kt`'s `covered` line for the lead to flip)
- [ ] full device walk + overview sheet
- [x] DESIGN.md, system-architecture.md, changelog
- [ ] verification report (lead, on the box)
- [x] version, commit (implementation half; lead's own on-box findings land in a follow-up commit)

## Success criteria

- Numbers table in the verification report: baseline vs after for R1–R4, debug and benchmark,
  meeting the Non-functional bounds; trace counts show no full remeasure of Home on return.
- Remaining TV differences each written in `docs/system-architecture.md` with the reason:
  collapsed-until-focused rail; pushed frames full screen without chrome; pill press keeps the
  remote on the pill; cover height fits 540dp, no pause button, no drift zoom, focus-selecting
  dots; department hero and its quote credit are not focus stops; ten-foot text floors; no blur
  behind the bar (as tablet); dark only (existing).
- No doc or code comment still describes Anime/Documentaries walls or the masthead Menu entries.

## Risk assessment

| Risk | L×I | Mitigation |
|------|-----|------------|
| Focus search reaches hidden Home nodes | M×H | `onEnter` cancel on the layer; test D-pad from pushed pages |
| Hidden Home steals focus (restore key written on open) | H×H | `LocalTakesArrivalFocus` false while covered; effects keyed on it |
| Home `BackHandler` answers Back on a pushed page | M×H | handlers `enabled && !covered`; existing Back-chain tests |
| Memory on the 32-bit box during playback | M×M | measured bound + pre-decided `PLAYER` fallback |
| Player surface black/z-order with Home underneath | L×H | Home not drawn while covered; verify picture + Back on the box |
| Stale Home after a refresh while covered | L×L | frozen input released on uncover → one incremental recomposition |
| Debug build install drops the box's session | L×H | same debug key; stop rather than uninstall |

## Security

None: no data, permission or session change; the device walk never changes a setting.

## Next

Follow-ups for the report: keep visited department tabs alive if R3 ever janks on benchmark;
phone/tablet navigation keep-alive only if a tablet measurement shows the same cost; the web's
cover genre column above 1180px (not reachable on a 960dp TV).
