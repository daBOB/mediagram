# Android Motion Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The phone and tablet move the way the web player moves: the same easings, durations and triggers, and nothing the web doesn't do.

**Architecture:**
- **One motion module** in `core/designsystem`: the web's tokens, three `Modifier.Node` helpers (`entrance`, `drift`, `hoverMotion`) and `catalogueIndication`.
- **`MediagramTheme` turns ripple off** and provides the indication. Material buttons get thin wrappers that draw it.
- **Each screen adopts the helpers at its own call sites**, row by row from the motion inventory.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2026.06.01: ui and foundation 1.11, material3 1.4.0), Robolectric 4.16 Compose tests.

**Spec:** `plans/261010-0112-android-motion-parity/spec.md`. Source table: `plans/reports/motion-parity-inventory-261009-report.md`; its row numbers (#1–#25) are cited below.

## Global Constraints

- **Worktree:** `../mediagram-motion` on branch `feat/motion`, off main. All paths below are relative to its `android/` unless they start with `web/`, `docs/` or `plans/`.
- **Line numbers** in the phases are main's at planning time. Earlier phases shift them, so match the quoted code, not the number.
- **No motion the web doesn't have.** Every animation names its web source (`file:line`) in a comment. Comments say why, never which plan or phase.
- **Draw phase only:**
  - motion is `graphicsLayer` alpha, translation and scale;
  - never `Modifier.offset`, `animateContentSize`, `animateItem()` or layout-phase reads of animated values.
- **Delays** go in `tween(delayMillis = …)`, never `delay()`, so "Remove animations" zeroes them.
- **Files** stay under 200 lines. No new dependencies; test-only additions use libraries already in the catalog.
- **Commits:** conventional, no AI references, no attribution lines.
- **Tests per task:**
  - `./gradlew :core:designsystem:testDebugUnitTest :ui-mobile:testDebugUnitTest` (plus `:ui-common:testDebugUnitTest` where touched);
  - `./gradlew spotlessApply detekt` before each commit;
  - `scripts/check.sh` at the end.
- **Devices:**
  - tablet only, always `ANDROID_SERIAL=caad49da`;
  - check the avatar shows T (test profile, PIN 1357) before any playback;
  - never tap "Start over";
  - the TV box is not touched by this plan.

## Review Focus

1. **A fast fling down Home and back up.** Section entrances start only for placed items and never replay on the way back; no dropped frames. Test in phase 03.
2. **A catalog refresh while Home is on screen** (sync lands). Nothing replays, neither entrances nor the cover's fade: the web's `.settled` rule. Test in phase 03.
3. **A tap during a fade:** mid page-turn, mid cover fade, or on player controls fading out. It lands on the page under it, on nothing (cover shield), or is ignored (controls), never on a half-hidden control. Tests in phases 04 and 08.
4. **Light theme, Blurred backdrop and the player over video.** The press tint stays visible: ink on the page, white over video. Test in phase 08; device walk in phase 09.
5. **"Remove animations" turned on while the app runs.** Nothing is left invisible or half-moved: every entrance ends at rest, and the reel rests at scale 1. Tests in phases 01 and 04; device walk in phase 09.

## Phases

| # | Phase | Depends on | Status |
|---|---|---|---|
| 01 | [Motion module](phase-01-motion-module.md) | none | pending |
| 02 | [Press feedback replaces ripple](phase-02-press-feedback-replaces-ripple.md) | 01 | pending |
| 03 | [Page turns and section reveals](phase-03-page-turns-and-section-reveals.md) | 01, 02 | pending |
| 04 | [Cover, reel and Home details](phase-04-cover-reel-and-home-details.md) | 01, 02 | pending |
| 05 | [Title and department pages](phase-05-title-and-department-pages.md) | 01, 02, 03 | pending |
| 06 | [Cards, arrows and colour fades](phase-06-cards-arrows-and-colour-fades.md) | 02 | pending |
| 07 | [Menus, tabs, loading and dialogs](phase-07-menus-tabs-loading-and-dialogs.md) | 02, 04, 05, 06 (small edits in their files) | pending |
| 08 | [Player](phase-08-player.md) | 02, 07 | pending |
| 09 | [Docs, measurement, version and walk](phase-09-docs-measurement-version-and-walk.md) | all | pending |

## Rulings made while planning

1. **`MotionModifiers.kt` is split by modifier** into `Entrance.kt`, `Drift.kt` and `HoverMotion.kt`, so each stays under 200 lines. The API is unchanged.
2. **Reduced motion is read once by `MediagramTheme`** and provided as `LocalReducedMotion`. The motion nodes read it through `currentValueOf`, and tests set it directly. `rememberReducedMotion()` stays public for ui-tv to use later.
3. **The ink and ring colours travel in `LocalIndicationTone`**, which the theme provides. That keeps `catalogueIndication(pressScale)` free of colour arguments, as the spec's signature has it.
4. **Draw-phase effects are covered by smoke tests and the device walk, not pixel tests.** Those are the indication's tint, ring and 0.98 snap, and every alpha. Robolectric here cannot capture pixels (`ui-mobile/src/test/kotlin/ui/PageGroundColorTest.kt` records three failed harnesses). Translation and scale are tested through layer-mapped bounds.
5. **The cover drift relies on Compose to stop in the background.** Compose already pauses its frame clock at ON_STOP, so the spec's "stops below RESUMED" needs no lifecycle code. An off-screen cover item leaves composition, so it stops too.

6. **The page turn moves the content slot, not the whole frame.** The web moves only `main`, so the rail and the top bar stay still. It goes on `AppChrome`'s content, the Settings branches and the shelves' tab, not on `when (at.top)`.
7. **Entrance flags are held by the page, never saved.** Back replays, as on the web: Back is a new address there. Scrolling within a page, a redraw and a sync never replay. Turning the tablet replays nothing, because `MainActivity` handles orientation itself. The first page after a cold start does rise, though the web's first draw doesn't (a `ponytail:` in the code).
8. **One still "Loading your library…" block covers the whole cold start.** The spec named only `MobileApp.kt`, but that spinner is the setup check. Cold start has three waits back to back: setup check, profile gate, library. All three get the web's cover-shaped block so it reads as one wait. The channel picker gets plain still text.
9. **The player says "Loading…" while a title opens. This is a written difference:** the web shows nothing there, but Android's byte path can take seconds on a big file, and a silent black stage reads as broken.
10. **Every Continue-style strip snaps,** the department strips too. Every web `.resume-strip` snaps.
11. **Taps on player controls that are fading out pass through to the picture**, as the web's `pointer-events: none` does, so the tap brings the controls back. They are not swallowed.
12. **Menu items and tabs keep Material's widgets, wrapped with the catalogue indication**, so TalkBack hears exactly what it did. Only the menu's container is new.
13. **Over video, the ring and the tool colour are the stage accent**: the web's fixed `--stage-accent`, not the page's accent.
14. **Under reduced motion the cover's mid-fade tap shield is skipped**, since there is no fade to shield.

## Left for later (found while planning, not motion parity)

- Tab labels are the accent here but ink on the web (`title-page.css:126,131-132`).
- Material's tab row scrolls the chosen tab into view (250 ms); the web's tab list never scrolls itself. Removing that means replacing the row.
- The web colours a player tool accent while its menu is open (`player-card.css:82`).
- The Kids menu doesn't hold the player's controls open the way the web's focused `<select>` does (`player-hud.js:28-31`).

## Review

(Filled in at close-out.)
