# Phase 04 — Home kept alive: implementation report

Scope: steps 1-3 (mechanism, test, check.sh) + docs half of step 7 (DESIGN.md,
system-architecture.md § Television differs, changelog). Steps 4-6
(measurements, fallback decision, device walk) explicitly left to the lead.

Branch: continued on `worktree-agent-a4e8c1b7d67ee7a8d`'s tip in this worktree
(started at 11a67b25, version 0.84.0; this phase's own work commits on top,
version 0.84.1). Worktree: `/home/andre/Workspace/mediagram/.claude/worktrees/agent-a58a98d39abfc4366`.

## What was built

**`ui-tv/.../TvHomeLayer.kt`** (new) — `LocalLibraryCovered` (raw signal: is
something pushed over the root right now, including the menu page),
`TvHomeLayer` (the Box wrapper: `drawWithContent {}` + `focusGroup()` +
`focusProperties { onEnter = { cancelFocusChange() } }` + `clearAndSetSemantics {}`
while covered — nothing drawn, nothing focusable, no duplicate semantics node),
`heldWhile` (freezes a value at the moment `covered` turns true, releases it
in one step on uncover), `TvHomeLayerTestTag`.

**`TvLibrary.kt`** — restructured: a `Box` now holds `TvHomeLayer(covered) { TvLibraryHomeFrame(...) }`
always, with the existing `when (top)` (now a sibling, drawn after so it
paints on top) reduced to just the pushed-frame branches plus the menu page;
the old `null ->` branch that used to mount `TvLibraryHomeFrame` is now
`null -> Unit` since `TvHomeLayer` already draws it unconditionally.
`covered = top != null || menuOpen`, with a comment naming the exact one-line
edit for the `PLAYER` fallback (see below).

**`TvLibraryBranches.kt`** — `TvLibraryHomeFrame` reads `LocalLibraryCovered`
and wraps its own `catalogState` in `heldWhile`, so a position save or a
refresh landing mid-cover never recomposes the hidden tree.

**`TvCatalogScreen.kt`** — the `LocalTakesArrivalFocus` it provides around its
body is now `!covered && nav.takesArrivalFocus` (was `nav.takesArrivalFocus`
alone), so every arrival effect downstream is gated on being covered too, not
only on the existing sentinel logic.

**`TvCatalogNav.kt`** (`rememberTvCatalogRestore`) — a real regression found by
tracing, not observed on a device: every sentinel restore key (Search, ⋮,
Latest, Genres, Settings, System) is set the moment its own frame *opens*
(`restore.opened(here, TvSearchEntryKey)` etc., called from
`TvLibraryBranches.kt`), not when it closes. Today that is harmless because
Home fully unmounts while any frame is open and remounts after, so the
consuming `LaunchedEffect` only ever sees the key on the *fresh mount that
follows closing*. With Home kept alive, that effect would fire immediately on
*opening* instead — requesting focus onto an inert, now-hidden bar (a no-op)
and then calling `onEntryRestored()`, which forgets the restore key before
Back ever reaches the frame it named. Fixed: all six consuming effects now
also key on and require `!covered`, so consumption waits for the frame to
actually close.

**`TvHome.kt`** — the `arrived` latch (Lesson 3) was a true one-shot: set once
per app launch and never reset, so with Home never remounting, Back from a
pushed frame stopped re-granting arrival focus after the very first grant.
Fixed with the minimum that preserves the existing "don't restart on a
sentinel's transient dip" protection already documented there: `covered`
(the raw signal, not the combined `takesFocus`) resets `arrived` to `false`
the moment the page is covered, and joins `target` as a key on the grant
effect. Traced through both cases (a real content return, and a sentinel
round-trip) to confirm the existing protection still holds — see the file's
own updated comment for the trace's conclusion, not the trace itself.

**`TvWall.kt` / `TvKeptWall.kt` (`EmptyKeptWall`) / `TvLists.kt`** — each had
an arrival-restore `LaunchedEffect` not keyed on `takesFocus` at all (`TvWall`)
or keyed on `Unit` (`EmptyKeptWall`, i.e. mount-only) — both one-shot-per-mount
patterns that silently stop working once mount stops recurring. Added
`takesFocus` to each key list, matching the pattern `TvCollectionsPage.kt`,
the department pages and `TvDepartmentRows.kt` already used (phase 03 already
got this right there; nothing changed in those files).

**`TvLibraryChrome.kt`** — all three `BackHandler`s gated with `&& !covered`,
so the chrome's own Back chain never answers a Back meant for whatever is
actually on top.

**`TvHomeCover.kt`** — the rotation `LaunchedEffect` now also keys on and
checks `covered` (paused, not merely uncomposed, while hidden); `HOLD_MS`
changed from `private` to `internal` so a test can drive the same clock.

## Test

`TvHomeKeptAliveTest.kt` (new), two classes:

- `TvHomeKeptAliveTest` — full `TvLibrary` + `TvAppFixture` integration
  (`films(2)`, no cover, matching `TvLibraryTest`'s own fixture): opens a
  Recently-added poster, asserts the title page's own text for it is the
  *only* match (Home's copy is semantically gone, not merely unfocused),
  presses Down/Left/Right ×10 and asserts after each press that no focused
  node has `TvHomeLayerTestTag` as an ancestor, then presses Back and asserts
  the same poster is refocused.
- `TvHomeCoverRotationWhileCoveredTest` — direct `TvHomeCover` composition
  (two plain films, `LocalLibraryCovered` driven from the test), advances the
  Robolectric clock 3×`HOLD_MS` while covered and asserts the film shown is
  unchanged, then uncovers and advances past one more `HOLD_MS` and asserts
  it rotated — proves the pause is real, not just "nothing observed yet".

Ran (not simulated): `./gradlew :ui-tv:testDebugUnitTest --rerun` — full
suite green, including `TvLibraryChromeUpTest`, `TvHomeStateTest`,
`TvLibraryTest`, `TvMenuTest`, `TvSearchAndGenreTest` and the two new classes.
`scripts/check.sh` — green (clippy, `cargo test --all`, gradle
`testDebugUnitTest`/`core:model:test`/`lint`/`compileDebugAndroidTestKotlin`).
`cargo metadata --locked --offline` — clean, confirming the `Cargo.lock` bump.

## Deliberately not done (out of scope, or genuinely not needed)

- Did **not** re-key `TvCollectionsPage.kt`, `TvMoviesDepartmentPage.kt`,
  `TvAnimeDepartmentPage.kt`, `TvDocumentariesDepartmentPage.kt`,
  `TvShowsDepartmentPage.kt` or `TvDepartmentRows.kt`'s `DeptRow`/`DeptEntryRow`/
  `GenreTileRow`. All of them already key their own arrival effect on
  `takesFocus` (phase 03 built them this way from the start) — nothing there
  was missing per the plan's own instruction.
- Did **not** rename `TvSearchEntryKey`/`TvMenuEntryKey`'s `"masthead:search"`/
  `"masthead:menu"` string values, or the shared `MastheadSplit`/`mastheadSplitOf`
  names `feature:catalog` exposes to phone, tablet and TV alike. Checked: no
  doc or comment in `DESIGN.md`/`docs/system-architecture.md`/`docs/project-changelog.md`
  still *describes* an Anime/Documentaries wall or a masthead-hosted menu as
  current; the surviving word "masthead" names either the phone/tablet's own
  real masthead or a shared data model TV's bar also reads from, not a stale
  TV concept.
- The "Left from a Continue card → rail → Right lands on Watch now, not the
  card" gap (known open item, phase 03's own report) did not fall out of this
  phase's changes — it is about `TvLibraryChrome`'s own rail-Right target, not
  about anything covered/uncovered touches. Left open, per the task's
  instruction to report it rather than chase it.
- No device-only behaviour (memory, frame timing, the self-close history from
  phase 01) is claimed fixed here — that is the numbers table the lead's own
  measurement produces.

## Concern worth flagging, not fixed

Every arrival effect in `TvCollectionsPage.kt`/`TvMoviesDepartmentPage.kt`/etc.
keys on the *combined* `takesFocus` (already the case before this phase).
Traced carefully (see commit message / `TvHome.kt`'s own comment for the full
reasoning applied there): for a sentinel return (Search, ⋮, Latest, Genres,
Settings, System) while one of *those* pages is the selected tab, the combined
signal flips false → true in **two** steps a frame apart (once when `covered`
clears, again once the chrome's own sentinel effect consumes its restore key)
rather than the one clean step a real content return gets. Because these
pages key straight on the combined value, the second step could in principle
re-run their own grant and pull focus back off the sentinel's own target
(the search icon, ⋮, a rail row) a frame later. This is a *latent* risk the
keep-alive change makes reachable for the first time (these pages used to
fully unmount on any push, which hid it) — not something observed, and not
something Robolectric proved either way in the time this phase had. `TvHome.kt`
avoids it by keying on the raw `covered` signal instead and relying on the
already-documented "target doesn't change across a sentinel visit" argument;
extending that same treatment to the department/Collections pages would touch
five more files outside this phase's named scope and wasn't done speculatively.
Named for the lead's own device walk below.

## For the lead: device walk / measurement checklist

Everything below needs the box; none of it was claimed done here.

1. **Baseline the fix itself** — R1 (poster → title → Back), R2 (cover Watch
   now → Back → Back), R4 (rail Settings → Back) on debug, then benchmark +
   `compile -m speed`: 0 `Davey!`, 0 `Choreographer…Skipped`; debug also:
   `MeasurePassDelegate.remeasure` count and `Choreographer.doFrame` inclusive
   time on return ≤ 20% of the phase-01 baseline (the trace method is in the
   debugger's own report, already in `reports/`).
2. **R3 (tab switch) for information only** — confirm it still rebuilds (by
   design) and note whether it janks on benchmark; not gating.
3. **PSS bound** — `dumpsys meminfo … | grep TOTAL` 60s into a film, before and
   after this phase's change, on the *same* build/profile the phase-01
   baseline used. If it grows > 30MB, flip the one line named in `TvLibrary.kt`'s
   own comment above `val covered = …` (excludes `PLAYER` from the kept-alive
   set) and re-measure — do not need to come back to me for that: the comment
   names the exact edit and the reason to write down.
4. **Tablet R1** — one run on its own benchmark build (`ANDROID_SERIAL=caad49da`),
   recorded for the parity question the debugger raised; tablet's own code is
   unchanged by this phase.
5. **The sentinel-race concern above** — specifically try: on the Movies (or
   any department/Collections) tab, open Search from the bar, close it with
   Back with *nothing else pressed*. Does the remote land on the search icon
   and stay there, or does it visibly jump to a plate on the department page a
   frame later? Repeat for ⋮, Latest, Genres, Settings, System. If any of them
   shows the jump, it is real and needs the same `covered`-keyed fix `TvHome.kt`
   already has, extended to that page — flag it back rather than living with
   it.
6. **The open item from phase 03** (Home: Left from a Continue card → rail →
   Right lands on Watch now, not the card) — still open; not this phase's
   fix, listed here only so it isn't lost.
7. **Watch-now self-close** (phase 01's symptom 1, not reproduced since 0.82.1)
   — kept as a watch item; nothing in this phase touches that path, but the
   keep-alive change is exactly the kind of change that could resurface a
   focus/lifecycle edge case, so worth one deliberate repro attempt.
8. **Full D-pad walk + `reports/tv-vs-tablet-overview.png`**, `reports/tv-web-look-verification-report.md`
   (numbers table, before/after, differences, follow-ups) — per the phase
   file's own step 6/7.

## Files touched

Created: `android/ui-tv/src/main/kotlin/ui/tv/TvHomeLayer.kt`,
`android/ui-tv/src/test/kotlin/ui/tv/TvHomeKeptAliveTest.kt`.

Modified: `android/ui-tv/src/main/kotlin/ui/tv/TvLibrary.kt`,
`TvLibraryBranches.kt`, `catalog/TvCatalogNav.kt`, `catalog/TvCatalogScreen.kt`,
`catalog/TvHome.kt`, `catalog/TvKeptWall.kt`, `catalog/TvLists.kt`,
`catalog/TvWall.kt`, `catalog/home/TvHomeCover.kt`, `chrome/TvLibraryChrome.kt`;
`DESIGN.md`, `docs/system-architecture.md`, `docs/project-changelog.md`;
`plans/260929-0215-tv-web-look-chrome-home-departments/phase-04-tv-home-kept-alive-measure-docs.md`;
version bump across `Cargo.toml`, `Cargo.lock` (5 workspace crates),
`web/package.json`, `android/app/build.gradle.kts` (0.84.0 → 0.84.1).

Gitignored native libs copied from the main checkout before building
(`android/core/rust/src/main/jniLibs/*/libmediagram_core.so`,
`android/core/ffmpeg/src/main/jniLibs/*/libffmpegJNI.so`) — not committed,
already covered by `.gitignore`.

## Unresolved questions

None blocking. The sentinel-race concern above is a flag, not a question —
resolving it either way (real or not) needs the box, which is explicitly the
lead's side of this phase.
