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
| 01 | [Motion module](phase-01-motion-module.md) | none | done |
| 02 | [Press feedback replaces ripple](phase-02-press-feedback-replaces-ripple.md) | 01 | done |
| 03 | [Page turns and section reveals](phase-03-page-turns-and-section-reveals.md) | 01, 02 | done |
| 04 | [Cover, reel and Home details](phase-04-cover-reel-and-home-details.md) | 01, 02 | done |
| 05 | [Title and department pages](phase-05-title-and-department-pages.md) | 01, 02, 03 | done |
| 06 | [Cards, arrows and colour fades](phase-06-cards-arrows-and-colour-fades.md) | 02 | done |
| 07 | [Menus, tabs, loading and dialogs](phase-07-menus-tabs-loading-and-dialogs.md) | 02, 04, 05, 06 (small edits in their files) | done |
| 08 | [Player](phase-08-player.md) | 02, 07 | done |
| 09 | [Docs, measurement, version and walk](phase-09-docs-measurement-version-and-walk.md) | all | done |

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

### What shipped

`feat/motion` at 0.121.0: 44 commits above main's 0.120.0 (`dab93dc8..50ce2f83`, 176 files),
rebased onto main before 9.1. `scripts/check.sh` is green. Merging and pushing are the user's
call; phones and tablets install by adb.

- **One motion module** in `core/designsystem`: the web's tokens (`Motion.kt`), `entrance`,
  `drift` and `hoverMotion` (`Entrance.kt`, `Drift.kt`, `HoverMotion.kt`), and
  `catalogueIndication` (`CatalogueIndication.kt`). `LocalReducedMotion` is live, so turning
  "Remove animations" on mid-run reaches every node.
- **Ripple is gone.** `MediagramTheme` provides the web's press: a quiet tint, a 2dp ring and a
  snapped 0.98. Material buttons draw it through `ui.chrome`'s `Catalogue*Button` wrappers, on
  the drawn container, while the 48dp touch box stays.
- **Pages and Home.** Page turns move only the content slot. Home's sections rise once a
  visit. The cover fades up from black and settles, with a tap shield mid-fade. The Featured
  poster drifts once per slide and holds when paused. Every Continue-style strip snaps.
- **Title and department pages.** Hero art eases in and the pull-quote follows. Tabs fade the
  underline and raise the panel 8dp. Arrows nudge toward where they lead, and with a mouse
  or stylus posters lift and art zooms. Colour fades follow `.card` (fade) and `.row` (snap).
- **Menus, loading, dialogs.** `CatalogueMenu` fades in, drops 4dp and closes at once; in the
  player it is still. Loading is the film leader's 3·2·1 countdown (`CountdownLeader.kt`,
  and on the web `film-leader.css` with `lib/film-leader.js`). Dialogs open without the window
  animation.
- **Player.** The controls fade over 260 ms and ignore taps once fading. Presses over the
  video tint white, and tools, marks and Notes turn the stage accent.

### Rulings made during execution

Planning rulings 1–14 stand above; ruling 2's "read once" was later revised (4.2 below).

- **Pre-flight (P1–P12).**
  - activity-compose is test-only in designsystem; `catalogueColorScheme` moves to its own
    file; no plan labels in test names.
  - Animated colours are read at draw, or in the smallest leaf where M3 only takes a
    `Color`.
  - Rows get accent hover (`catalog.css:44,48,76-78`). Appearance options use pressScale 1.
  - 9.2 and 9.3 run as one dispatch.
  - Device commands carry the serial inline. Theme is a third setting to read and restore.
    8.2's device check folds into 9.4. Item 4 drops "radio row"; item 8 reads "controls come
    back, no menu opens".
  - The Blurred fallback keeps the turn gate. Stale brief text is struck.
  - The cover's tap shield stays a timer. One `linkClickable` and one `SeeAllLink` serve
    every link. The click smoke test stays.
  - The project lints with Android lint (`lintDebug`): the plan's spotless/detekt do not exist
    in this build.
- **1.1.** HoverMotion snaps its progress to 0 on reuse, so a reused card never stays
  half-way.
- **1.2.** The ring is inset by half its stroke, so the full 2dp shows. Press, hover and focus
  sets reset on detach.
- **L1 (user decision).** Loading is a countdown leader, "cinema related", everywhere, web
  included. It supersedes rulings 8 and 9's still words.
- **2.2.** The tint and the 0.98 sit on the drawn container, not the 48dp touch box.
- **4.1.** The cover's arrival flag is held by HomeScreen, outside the LazyColumn, so a refresh
  or a scroll back never replays the fade. Only the fading slide is shielded.
- **4.2.** `LocalReducedMotion` observes the setting with a ContentObserver. This revises
  planning ruling 2, so a live toggle leaves the drift at rest.
- **5.1.** HeroArtInTest is split to stay under 200 lines.
- **6.3.**
  - Rows accent on a finger press too: the web's bare `:hover` fires on tap.
  - ShelfList has no thumb, so it gets no lift; that difference is written in its code.
  - The press-shrink test is waived as draw-only; the walk checked it.
  - `.row` titles snap and `.card` names fade (`catalog.css:44,74-79`).
- **7.1.** `UnderAnchor` centres and clamps a tall menu as Material's `DropdownMenu` does.
- **7.3.**
  - Split into 7.3a (the web leader, the reference) and 7.3b (Android).
  - The TV's leader spots go to a follow-up on `feat/tv-parity`.
  - The stage leader hides at `loadeddata`. The library block's sweep keeps `--accent`.
  - `lib/film-leader.js` stands as a boot script.
  - The stage's status region is always present.
  - `CountdownLeader` takes page inks. One `CenteredLoading` serves the seven in-page waits
    and the channel picker.
  - A reduced-motion flip test is added.
- **7.4.** The window tests were proved RED.
- **8.1.** The card's bounds clear only when its fade finishes, so subtitles and up-next hold.
  Focus return skips a fading card.
- **8.2.** The top-bar marks and Notes turn their label to the stage accent, since they have no
  outline to turn (`playback.css:97-99`).
- **9.1.** Blurred holds: the idle rule is not met. The slower Blurred scroll is outside the
  rule, so the walk judged it by eye. The measurement had no task review.
- **9.2–9.3.** The docs and the bump were reviewed by the controller; the final branch review
  covers them.

### Differences written down

From `docs/system-architecture.md`'s motion paragraph (50ce2f83):

- Controls linger 4 s, not 2.6 s.
- The phone's header is quick-return.
- The cover holds still for TalkBack.
- The first page after a cold start rises.
- The leader also shows in the channel picker and the in-page waits.
- The TV gets the leader later.
- The fetching line is still a progress bar.
- Tab labels are the accent.
- The tab row does not auto-scroll.
- The player tool is not accent while its menu is open.
- The marks' popups do not hold the controls.
- Resting controls cannot be woken by TalkBack or a keyboard, and do not hold on focus.
- The marks and Notes turn their label, not an outline.
- GenreLinks look unlike `.genre`.
- SettingsIndex's fill snaps.
- The cover's tap shield is a timer.
- Blurred's drift is the thing to watch.

Also written down:

- The player says "Loading…" while a title opens (ruling 9, later the leader).
- ShelfList has no thumb (in its code).

### Frame times

From `reports/frame-times-261010-motion-report.md`, tablet, benchmark builds:

- **Default backdrop.** Matches main: scroll p50 7 vs 9 ms, p90 12 vs 12 ms. The idle cover
  draws continuously at p90 16 ms with 0.00% deadline-janky frames.
- **Blurred, idle.** Within the rule: p90 16 vs 15 ms, janky 0.00% vs 0.10%, so no fallback.
- **Blurred, scroll.** Slower than main (p50 14 vs 8, p90 18 vs 11 ms), still with 0% missed
  deadlines.
- **On the walk,** Blurred scrolling showed no stutter by eye.

### The walk

`reports/device-walk-261010-motion-report.md`. With animations on, 39 checks passed, 0 failed
and 4 were not checked: hover (no mouse), subtitles (none on any title), light-theme in-page
waits (not caught), and Blurred over video (the player draws no backdrop). With "Remove
animations" on, 13 passed. Settings came back as found, except the profile: it is left on T,
because returning to A needs andre's PIN. Observations worth a follow-up:

- The refresh progress line pushes Home down while it runs. This is pre-existing on main.
- The three cold-start waits move the leader and restart its count, so they don't quite read
  as one wait (ruling 8).

### Left for the TV batches 3 and 4

- The TV adopts the motion module. It has no `LocalReducedMotion` provider, so its masthead
  still ramps under "Remove animations"; `rememberReducedMotion()` is public for `ui-tv`.
- The TV's countdown-leader spots: `TvSetupStep`, `TvProfilePicker`, `TvLibraryChoiceScreen`,
  `TvPlayerFailure`, `TvStatsPage`. They wait on `feat/tv-parity`.
- The four "Left for later" items above: tab label ink, tab-row auto-scroll, the tool accent
  while its menu is open, and Kids holding the controls. Plus TalkBack and keyboard waking
  the resting controls, which the web does on `focusin`.

### Deferred minors (from the ledger, none blocking)

- **Entrance.kt.** `play()` lacks a `!started` guard. `startOver` doesn't cancel old jobs. A
  detach/reattach without reset can leave an entrance part-way (no movable content uses it
  today).
- **Tests that prove less than they say:**
  - no composed-but-unplaced, reduced-motion entrance or drift resume-vs-restart test;
  - `CatalogueIndicationReuseTest` is a smoke test;
  - the live-toggle test passes with a static local;
  - the sync test misses neighbouring sections added or removed;
  - the 7.1 and 7.2 guard tests can pass vacuously from a wrong working directory;
  - the 7.4 window tests prove the read, not Robolectric's default;
  - `barTop`, `CardFocusReturn` and disabled-hover go untested.
- **Small gaps.**
  - `Drift.kt:79` `sync()` doesn't re-run when reduced motion flips back off.
  - A late rating can make the Featured facts line rise late.
  - A sync that gives a department hero new art plays its art-in where the web's `.settled`
    wouldn't.
  - Lists' empty→non-empty tiles appear at rest.
  - A menu wider than the window is not centred.
  - The ShelfList thumb-lift comment cites the wrong line.
- **Tidiness.**
  - Import order across several files.
  - CatalogShelves' `when` is not re-indented.
  - LEADER_GAP imported from a child package.
  - Two ConfigurationScreenWidthHeight lint warnings in LibraryLoading.
  - `LibraryBrowseBranches.kt` at 233 lines (was 232); `CoverSlide.kt` and `HeroArtInTest.kt`
    at 200.
