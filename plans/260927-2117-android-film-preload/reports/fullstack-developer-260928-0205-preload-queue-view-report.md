## Phase Implementation Report

### Executed Phase
- Phase: phase-03b-preload-queue-view
- Plan: /home/andre/Workspace/mediagram-preload/plans/260927-2117-android-film-preload
- Status: completed

### Files Modified

Created (`android/core/playback/src/main/kotlin/`):
- `FilmPreloadOverview.kt` (87) — `FilmPreloadRow` (`Running`/`Waiting`), `filmPreloadRows` (pure builder), `filmPreloadOverview` (the `Flow` wiring) — the queue projection, kept out of `FilmPreloader.kt` per instruction.
- `CacheBudgetQuery.kt` (17) — `fun interface CacheBudgetQuery { suspend fun currentBudgetBytes(): Long? }` + `Noop`, the same narrow-query-plus-Noop-default shape `HeldSetsQuery` already uses, so no existing test that builds a `TitlePreloadViewModel` needed to change.

Created (`android/core/playback/src/test/kotlin/`):
- `FilmPreloadOverviewTest.kt` — pure tests of `filmPreloadRows` (empty, running+pending order, paused-with-reason, just-dequeued-reads-as-running, nothing-active).

Created (`android/ui-mobile/src/main/kotlin/ui/catalog/`):
- `PreloadsScreen.kt` (115) — the phone/tablet Preloads page: `LazyColumn`, three sections (Preloading/Queued/On this device), empty sections hidden, all-empty says nothing is preloading.

Created (`android/ui-tv/src/main/kotlin/ui/tv/catalog/`):
- `TvPreloadsPage.kt` (118) — the TV twin: a scrollable `Column`, same three sections, `TvTextRow` pairs (title + Cancel/Remove) per row, initial focus on the first row across whichever section leads.

Created tests: `PreloadsScreenTest.kt` (phone, default + `w1164dp-h777dp`), `TvPreloadsPageTest.kt` (TV, real `performKeyInput`/D-pad), `PreloadsMenuFlowTest.kt` (phone, full `LibraryFlowFixture` route).

Modified:
- `core/playback/.../FilmPreloadQueue.kt` — `QueueSnapshot(active, pending)`, published from the same `publish()` every method already calls.
- `core/playback/.../FilmPreloadState.kt` — `FilmPreloading.queueOverview: Flow<List<FilmPreloadRow>>`, defaulted to `flowOf(emptyList())` in the interface itself (not abstract) so `Noop` and every test fake built before this phase keep compiling unchanged.
- `core/playback/.../FilmPreloader.kt` — one property, `override val queueOverview = filmPreloadOverview(queue.snapshot, ::stateOf)`; net +3 lines over its pre-phase 316 (removed two now-unused imports too) — the projection logic itself lives in the new file, per instruction not to grow this one.
- `core/playback/src/test/kotlin/{FilmPreloadQueueTest.kt,FilmPreloaderTest.kt}` — snapshot test; two `queueOverview` integration tests (order; promotion after cancel) over the real engine + fakes.
- `feature/player/.../player/FilmPreloadLabels.kt` (188) — `preloadLabel` gained two optional params (`queuedAhead: String?`, `budgetBytes: Long?`, both default `null` — every existing call site unchanged); `needsSpaceLabel`; `queuedAheadLabel(rows, setId)` (the "after X, n%" / "n ahead" rule); `preloadRowState(row)` (reuses `preloadLabel`/`preloadBarLabel` for a Preloads-page row instead of a second copy of the words).
- `feature/player/.../player/TitlePreloadViewModel.kt` (134) — `queueRows`/`queueCount` (plain `val`s, not functions — sharable across the menu badge and the Preloads page with no `remember` keying, unlike `stateOf`/`serverLine`'s own per-film shape), `queuedAhead(setId, totalBytes)`, `needsSpaceBudget(setId, totalBytes)`, `cancel(setId)` (the Preloads page's own direct cancel), a `cacheBudget: CacheBudgetQuery = CacheBudgetQuery.Noop` constructor param.
- `feature/player/.../di/PreloadModule.kt` — `provideCacheBudgetQuery` wrapping `CacheProvider.occupancy(context).budgetBytes`, the same live figure `provideFilmPreloader`'s own `fits` lambda already reads.
- `feature/player/src/test/kotlin/{FakePreload.kt,TitlePreloadViewModelTest.kt}` — settable `queueOverview`; tests for `queuedAhead`/`needsSpaceBudget`/`queueCount`.
- `feature/catalog/.../CatalogUiState.kt` — `heldFilms()`: `Entry.Film` cards among `heldIds`, reusing the catalogue's own held set rather than a new query (the "films/documentaries" distinction is exactly what `Entry.Film` vs `Entry.Collection` already draws).
- `feature/catalog/.../Destination.kt` — `Destination.Preloads` (bar title "Preloads", "Back").
- `feature/catalog/src/test/kotlin/CatalogUiStateTest.kt` — three `heldFilms()` tests.
- `ui-common/.../LibraryPositions.kt` — `FrameKind.PRELOADS`, `openPreloads()` — additive, same shape as `LATEST`/`GENRES`.
- `ui-common/.../MenuActions.kt` — `onPreloads: (() -> Unit)? = null`, `preloadCount: Int = 0` on the existing `MenuActions` (not a new parameter threaded through every scaffold call site — see Navigation below).
- `ui-mobile/.../LibraryBrowseBranches.kt` — `PreloadsFrame` (mirrors `GenresFrame`/`LatestFrame`).
- `ui-mobile/.../LibraryFlowBranches.kt` — one dispatch line, `FrameKind.PRELOADS -> PreloadsFrame(...)`.
- `ui-mobile/.../LibraryFlow.kt` — resolves `TitlePreloadViewModel`, builds `onPreloads`/`preloadCount` from `queueCount`.
- `ui-mobile/.../OverflowMenu.kt` — the conditional "Preloads · n" row.
- `ui-mobile/.../TitlePreloadWiring.kt`, `.../catalog/{TitlePreload.kt,TitleDetailScreen.kt}` — thread `queuedAheadLabel`/`needsSpaceBudgetBytes` from the ViewModel into `TitlePreloadUi` and `PreloadPill`.
- `ui-mobile/src/test/kotlin/ui/{TitlePreloadFixtures.kt,catalog/TitlePreloadTest.kt}` — settable `queueOverview`; two new label tests (queued-after, needs-with-budget) through the real `TitleDetailScreen`.
- `ui-tv/.../{TvLibrary.kt,TvLibraryCatalogFrames.kt,TvLibraryExtraFrames.kt,TvMenuBranches.kt,catalog/{TvTitlePage.kt,TvTitlePreload.kt},system/TvMenuPage.kt}` — the television twins of the above: `TvPreloadsFrame`, `FrameKind.PRELOADS` dispatch, `tvMenuActions(..., preloadCount)`, the TV menu's own conditional row (reads `menu.onPreloads`/`menu.preloadCount` — no new `TvMenuPage` parameter needed either), `queuedAheadLabel`/`needsSpaceBudgetBytes` threaded into `TvTitlePreloadUi`/`TvPreloadPlate`.
- `ui-tv/src/test/kotlin/ui/tv/{TitlePreloadFixtures.kt,TvMenuTest.kt,catalog/TvTitlePreloadStateTest.kt}` — settable `queueOverview`; two new label tests; two new `TvMenuTest` cases (entry absent, entry present + real navigation + Back).
- `DESIGN.md` — a paragraph naming the Preloads page Android-only and describing its shape, beside the existing Preload-control paragraph.
- `docs/project-changelog.md` — new `0.72.0` entry.
- `Cargo.toml`, `Cargo.lock` (five workspace members' `version` only), `web/package.json`, `android/app/build.gradle.kts` (`versionName` only) — `0.71.0` → `0.72.0` by regex (verified: `versionCode` untouched at 18; `cargo check -p mediagram-cache` confirms `Cargo.lock` integrity).
- `plans/260927-2117-android-film-preload/{plan.md,phase-03b-*.md}` — status/Todo updated.

Left in place, uncommitted from the prior session, per instruction: `docs/system-architecture.md`, `docs/development-roadmap.md`, the device-verification report and its screenshots.

### Tasks Completed
- [x] Engine exposes one observable queue list, unit-tested (`FilmPreloadOverviewTest`, `FilmPreloadQueueTest.snapshotStartsEmptyAndTracksActiveAndPendingTogether`, `FilmPreloaderTest.queueOverview*`)
- [x] Queued label names what is ahead, phone + TV, with tests
- [x] NeedsSpace names the live budget, phone + TV, with tests
- [x] Preloads page, phone/tablet + TV, sections/actions tested at default width, `w1164dp-h777dp`, and via real TV key events
- [x] Menu entries with count, hidden while idle, tested end-to-end on both surfaces (real navigation, not just the isolated row)
- [x] Android-only note beside the page (DESIGN.md, doc comments)
- [x] Whole-project `testDebugUnitTest lint :app:assembleDebug --rerun-tasks` green (703/703 tasks, no cache reuse)
- [x] Version bumped 0.71.0 → 0.72.0 by regex; changelog written
- [ ] Device check (TV box/tablet) — explicitly deferred; both in use by other work this session per instruction

### Labels, as built

| Situation | Phone / TV text |
|---|---|
| Queued, next (only the running film precedes it) | `Queued · after Der Pate, 36%` |
| Queued, N films ahead (running + preceding queued) | `Queued · 2 ahead` |
| NeedsSpace, live budget known | `Needs 30 GB · budget is 8.0 GB · Try again` |
| NeedsSpace, budget not yet read | `Needs 30 GB · Try again` (unchanged from phase 03) |
| Menu entry, count > 0 | `Preloads · 2` |

`queuedAheadLabel`'s rule: index 0 in the engine's own ordered list is never queued (it is the running row); index 1 (immediately behind it) gets the "after `<title>`, `<percent>`" form; index ≥ 2 gets "N ahead" where N is the index itself (every row strictly before it, running one included).

### Navigation choice

Followed the phase's own instruction to route like Latest/Genres rather than like a `MenuScreen` (System/Settings/TmdbKey): `Destination.Preloads` + `FrameKind.PRELOADS` + `LibraryPositions.openPreloads()`, a frame of its own on the shared back-stack (`PreloadsFrame`/`TvPreloadsFrame`), saved-stack safe by construction — `FrameKind` is additive and the frame carries no payload (same as `LATEST`/`GENRES`). A `MenuScreen` would have meant reusing the "moves rather than stacks" swap semantics that only make sense for System/Settings/the key screen, none of which fit a page a viewer navigates into and back out of like any other library screen.

The menu's own "Preloads · n" row was folded directly onto the existing `MenuActions` struct (two new optional fields) rather than threaded as a new parameter through `LibraryScaffold`/`LibraryBranch`/`TvMenuPage` and every one of `LibraryBranches.kt`'s dozen call sites — `MenuActions` already reaches every scaffold, so this was the only change with no new plumbing. `MenuActions.kt`'s own "the five things the menu can do" doc line is now specifically about the five permanent rows; the two new fields are documented as the sixth, conditional one.

### Decisions where the spec left room

1. **"On this device" = `Entry.Film` among `heldIds`.** The spec says "films/documentaries only if that kind exists here" — `Kind` has no separate documentary value (a documentary is `Kind.MOVIE`), and `Entry.Film` is already the shelf's own film-card type (as opposed to `Entry.Collection` for shows/courses), so filtering to it is exactly that distinction with nothing further to decide. Verified against `feature/catalog/Collection.kt`.
2. **Preloading section shows the front of the queue even in the brief pre-first-loop-iteration window** (state still `Queued` structurally though the engine has already dequeued it) — reads as `Running` at 0 held bytes rather than a fourth row kind, since it is about to write, not waiting behind anything. Documented in `filmPreloadRows`'s own doc; covered by `FilmPreloadOverviewTest.aJustDequeuedActiveFilmReadsAsRunningAtZero`.
3. **`CacheBudgetQuery` as a new narrow interface** rather than injecting `Context`/`CacheProvider` straight into `TitlePreloadViewModel` — `CacheProvider` opens a real (Robolectric-visible) disk cache and needs explicit `resetForTest()` between tests (see `CacheProviderTest.kt`); a raw `Context` dependency there would have put every existing `TitlePreloadViewModel`-based test (four direct-construction call sites) at risk of cross-test disk-cache pollution or slowdown for a phase that only needed one number. The `Noop` default keeps every one of them behaviourally identical to before this phase (`budgetBytes = null` → the phase-03 wording, unchanged).
4. **The Preloads page is a plain row list (`LazyColumn`/`Column`), not a poster wall** — what it shows is a handful of films at most, never the library's worth `TvWall`/`ShelfWall` are built for. Documented in DESIGN.md's own new paragraph.
5. **Menu row order** — "Preloads · n" placed after Genres (the last of the four browse utilities) on the phone, and after Genres on the TV menu page too (TV's own utilities already sit after its five fixed items, unlike the phone's, a pre-existing drift this phase did not touch). Not specified by the phase; chosen as the least disruptive position on both (verified no existing order assertion breaks — `TvMenuTest`'s own five-item order check is untouched, since the new row is conditional and absent in that test's fixture).

### Tests Status
- Type check: pass (whole project `compileDebugKotlin`, run per-module during development and once whole-project).
- Unit tests: pass — `./gradlew testDebugUnitTest lint :app:assembleDebug --rerun-tasks` (whole project, every task re-executed, none `UP-TO-DATE`, 703/703) green. New: 5 `FilmPreloadOverviewTest`, 1 `FilmPreloadQueueTest` addition, 2 `FilmPreloaderTest` additions, 6 `TitlePreloadViewModelTest` additions, 6 `PreloadsScreenTest` (phone incl. tablet width), 5 `TvPreloadsPageTest` (TV, real D-pad), 2 `PreloadsMenuFlowTest` (phone, full app route), 2 `TvMenuTest` additions (TV, full app route), 2 `TitlePreloadTest`/`TvTitlePreloadStateTest` additions each, 3 `CatalogUiStateTest` additions. Every pre-existing test unaffected.
- Lint: pass — whole-project `lint`, baseline unchanged ("1 error, 34 warnings, 3 hints filtered", same as before this phase).
- Integration: `:app:assembleDebug` green.
- No device install — TV box and tablet both in use by other work this session, per instruction.

### File-size guideline

New files all at or under 120 lines. Pre-existing files this phase touched and that were already over the ~200-line guideline before it (`LibraryFlowBranches.kt` 343→344, `TvLibraryCatalogFrames.kt` 196→218, `TvTitlePage.kt` 214→222, `TitleDetailScreen.kt` 244→254) grew only by the wiring each wanted (1–12 lines); none were split further, matching the reasoning phase 01/02/03's own reports already gave for the same files ("splitting further would fragment one screen's composition for little real separation"). `TvLibraryExtraFrames.kt` (182→218) gained one whole new frame function (`TvPreloadsFrame`, 24 lines) — the same size as its siblings (`TvGenresFrame`, `TvLatestFrame`) already in that file, not a guideline violation of its own. `FilmPreloader.kt` stayed essentially flat (316→319) as instructed — the queue-projection logic itself lives entirely in the new `FilmPreloadOverview.kt`.

### Issues Encountered

None blocking. One thing to flag for the coordinator: `FilmPreloadQueue.kt`'s new `QueueSnapshot` duplicates `hasWork`'s own "is there anything" computation inside `publish()` (both derived from the same `active`/`pending` read, in the same synchronized method) — harmless (same source of truth, same lock), not merged into one field because `hasWork` is a `Boolean` `PreloadService` already depends on and changing its shape was out of scope.

### What needs a device

- The Preloads page's real scroll/focus behaviour with more than a handful of rows (Robolectric proves layout and D-pad navigation between a few synthetic rows, not a long real list).
- The menu's "Preloads · n" entry against a real preload in progress (unit/Robolectric tests use synthetic `FilmPreloadRow`s via the fake engine, not a real `FilmPreloader` write).
- `NeedsSpace`'s live-budget line against the TV box's own real 8 GB budget that prompted this phase — the exact number the user saw silently missing.
- Tablet pass generally (phase 04's own outstanding item, unrelated to this phase's own work).

### Unresolved Questions
- None blocking. Menu row order (decision 5) and the "just dequeued reads as Running" choice (decision 2) are both judgment calls flagged above for the coordinator to confirm or override.

**Status:** DONE
**Summary:** The engine's queue is now one ordered, unit-tested list; a film page's Queued label names what it is waiting on and NeedsSpace names the live budget, on both surfaces; a new Preloads page (phone/tablet + TV) shows Preloading/Queued/On this device; the overflow menu and TV menu carry a "Preloads · n" entry visible only while something is running or queued. Whole-project `testDebugUnitTest lint :app:assembleDebug --rerun-tasks` green (703/703), version bumped to 0.72.0, changelog and DESIGN.md updated, nothing committed.
**Concerns/Blockers:** None blocking. Device verification (TV box + tablet) still outstanding — both are in use by other agents this session, per instruction. Two judgment calls (menu row placement, the just-dequeued row's brief "Running at 0%" reading) flagged above for review.

---

## Addendum: fixes from code-reviewer's review + two user decisions

Report reviewed: `code-reviewer-260928-0250-preload-queue-view-review-report.md`. All six
Mediums fixed, both user decisions (kids filtering, time-limit-paused films staying listed)
implemented with tests, all Lows addressed except L3 (accepted as-is, the app's own size
format).

### M1 — reject the flagged "Running at 0%" reading

`filmPreloadRows`'s `else ->` branch now returns `FilmPreloadRow.Waiting`, not `Running` at
zero — a film `FilmPreloadQueue` marks active but that has not yet taken the shared lane
(a series preload can hold it for a whole episode's write) reads as still waiting, matching
`FilmWriteAttempt`'s own "never Running before the lane" rule; it still leads every row from
`snapshot.pending`, since it is still the next film to write. `FilmPreloadOverviewTest`'s own
case flipped to assert `Waiting`; the two `FilmPreloaderTest.queueOverview*` tests hold
unchanged (their writer already has the lane by the time the assertion runs).

### M2 — the TV Preloads page draws the bar

`TvPreloadingRow` now also calls `TvPreloadDetailLines(state, serverLine = null,
onOpenStorage = {})` beside the existing state-label line (kept — losing it in an early pass
of this fix would have dropped the pause-reason word entirely; caught before finishing by
`TvPreloadsPageTest`'s own `assertCountEquals(2)` failing). The phone's own bar already
reused `PreloadProgressBar`; no change needed there.

### M3 — kids-profile filtering (user decision: filter by resolvability)

`CatalogUiState.resolvableQueueRows(rows)` (`feature/catalog`): keeps only rows whose
`setId` this profile's own catalogue can resolve (`mediaSet(id) != null`) — the same
`Entry.Film`/`Entry.Collection` walk `heldFilms()` already uses, so a kids profile's own
already-filtered shelves decide this for free; no second kids-aware code path. Applied at
the wiring layer (never inside `TitlePreloadViewModel`, which has no reason to know about
catalogues) in four places: the Preloads page's own rows (`PreloadsFrame`/`TvPreloadsFrame`),
the menu's own count (`LibraryFlow.kt`/`TvLibrary.kt`), and a queued film's own "Queued ·
after …" label — `TitlePreloadViewModel.queuedAhead` gained an optional `rows` parameter
(default `queueRows`, so every caller not filtering keeps behaving exactly as before) that
`rememberFilmPreloadUi`/`rememberTvFilmPreloadUi` now pass a `catalogState`-filtered flow
into. Tests: `CatalogUiStateTest` (kids catalogue drops what it cannot resolve, a grown-up's
catalogue keeps everything), `TitlePreloadViewModelTest.queuedAheadReadsWhicheverRowsFlowItIsGiven`
(the VM reads the given rows, not its own default, when one is passed), and a real-fixture
end-to-end test (`PreloadsMenuFlowTest.aFilmNotInThisCatalogueDoesNotCountOrShow`) proving a
film outside the current profile's catalogue never reaches the count or the page.

### M4 — TV focus: restore key, and no stealing

`TvPreloadsPage` gained `restoreKey`; a new `rememberQueueFocus`/`QueueFocus`
(`TvQueueFocus.kt`) computes a `targetRowId` synchronously each recomposition (arrival:
`restoreKey` if still present, else the first row; afterward: `null`, unless the row that
held focus has since left the row list, in which case the first row again) and only calls
`requestFocus()` inside a `LaunchedEffect` keyed on that resolved id — so a progress tick or
a promotion that leaves the SAME row still present never re-fires it. A row's own title
tracks its focus back via `Modifier.trackedBy(focus, setId)` (an extension on `Modifier`,
not a plain factory method — Compose's own `ModifierFactoryExtensionFunction` lint rule
caught the first draft of this). Tests: opening a title records `restore.opened`, wired into
`TvPreloadsFrame`; `TvPreloadsPageTest` covers `restoreKey` landing on the named row, focus
staying put when an unrelated row's content changes, and focus moving to the new first row
once the row that held it actually leaves.

### M5 — the TV menu page scrolls

`TvMenuPage`'s `Column` gained `Modifier.verticalScroll(rememberScrollState())`; Compose's
own focus-into-view machinery brings a newly-focused row into the visible area without
further code. `TvMenuTest`'s own new case walks nine real `KEYCODE_DPAD_DOWN` presses from
"System" to "Preloads · 1" and asserts focus at each end, rather than reaching the row with
a semantics click alone.

### M6 — docs corrected

`docs/system-architecture.md` § Film preload: separated the bar (the engine's own progress
callback) from the 5s poll (only ever the home-server line), and added the Preloads page,
`queueOverview`/`QueueSnapshot`, and `timeLimitPaused`. `docs/development-roadmap.md`:
header changed from "Complete" to "In progress" (matching the Phase 4 row, which the
original text already contradicted), a new 03b row, released range extended to 0.72.0.
`docs/project-changelog.md`: the TV-bar claim is accurate now that M2 is fixed; added
entries for the time-limit-paused Resume action and kids-profile filtering.

### User decision — time-limit-paused films stay listed, with Resume

`FilmPreloadRow.TimeLimitPaused(setId, title, totalBytes, heldBytes, wasActive)`
(`core/playback`); `FilmPreloader.timeLimitPaused: StateFlow<List<FilmPreloadRow.TimeLimitPaused>>`,
populated inside `pauseForTimeLimit()` (which already knew, per film, whether it was the one
active) and cleared per film by `enqueue` (the Resume path) or `remove` — reported apart
from `queueOverview`, since `pauseForTimeLimit` empties the real queue by design; no engine
rule changed. `TitlePreloadViewModel.queueRows` now `combine`s `queueOverview` with
`timeLimitPaused`, so the menu's own count (`queueCount`, and the kids-filtered count both
surfaces compute) includes them for free. Both Preloads pages keep such a film under
Preloading (`wasActive`) or Queued (otherwise) — the section it held before the pause —
named "Paused — background limit", one "Resume" action
(`TitlePreloadViewModel.resume(setId, title, totalBytes)` → `enqueue`, restarting its own
place in the queue) rather than Cancel. Tests: a real-thread engine test
(`FilmPreloaderBlockingWriterTest.timeLimitPausedReportsBothFilmsThenDropsOneOnceItIsResumed`,
both the active and the merely-queued film, `wasActive` correct for each, resuming one drops
only that one) and page-level tests on both surfaces (row lands in the right section, its
own caption, Resume reaches the right row).

### Lows

- **L2:** `queuedAheadLabel` ellipsizes the running title to 20 characters
  (`QUEUED_AHEAD_TITLE_MAX_LENGTH`), the same `ellipsize` `Failed` already uses at 28.
  New test: `queuedAheadEllipsizesALongRunningTitle`.
- **L3:** accepted — `humanSize`'s own one-decimal format, unchanged.
- **L4:** the TV Back test now asserts a specific, known plate (`hasText("Film 1") and
  hasClickAction()`) rather than ambiguous `onNodeWithText("Menu")` text; D-pad-driven
  reach is M5's own test.
- **L5:** `heldFilms()` is `remember(catalogState)`-wrapped at both wiring call sites
  (already true for the phone from the first pass; added for TV here), so it recomputes
  once per real catalogue change, not once per progress tick.
- **L6:** history-narrating comments rewritten to explain why rather than when
  (`FilmPreloading.queueOverview`/`timeLimitPaused`, `CacheBudgetQuery.Noop`,
  `TitlePreloadViewModel.queueRows`); `TvMenuPage`'s doc no longer says "the same five
  items" now that a further conditional row exists; `MenuActions`' own doc no longer calls
  it "the sixth" row (the phone has nine before it, not five).
- **L7:** `TvLibraryExtraFrames.kt` (`TvPreloadsFrame` extracted to its own
  `TvPreloadsFrame.kt`) and `TvLibraryCatalogFrames.kt` (`rememberTvFilmPreloadUi` extracted
  to `TvFilmPreloadWiring.kt`, mirroring the phone's own `TitlePreloadWiring.kt`) are both
  back under 200 lines (182 and 174).

### Files touched by this pass (beyond the original list)

New: `ui-tv/.../catalog/TvQueueFocus.kt`, `ui-tv/.../TvPreloadsFrame.kt`,
`ui-tv/.../TvFilmPreloadWiring.kt`. Modified: `FilmPreloadOverview.kt`, `FilmPreloadState.kt`,
`FilmPreloader.kt` (316 baseline → 335: the time-limit-paused bookkeeping is tied to this
class's own private state — `queue`, `itemJobs`, `externallySettled` — and was not a good
extraction candidate the way `queueOverview` was), `FilmPreloadOverviewTest.kt`,
`FilmPreloaderBlockingWriterTest.kt`, `FilmPreloadLabels.kt`, `TitlePreloadViewModel.kt`,
`TitlePreloadViewModelTest.kt`, `FakePreload.kt` (+ its ui-mobile/ui-tv twins),
`CatalogUiState.kt`, `CatalogUiStateTest.kt`, `MenuActions.kt`, `PreloadsScreen.kt`,
`PreloadsScreenTest.kt`, `LibraryBrowseBranches.kt`, `LibraryFlow.kt`,
`TitlePreloadWiring.kt`, `LibraryFlowBranches.kt`, `TvPreloadsPage.kt`,
`TvPreloadsPageTest.kt`, `TvLibraryExtraFrames.kt`, `TvLibraryCatalogFrames.kt`,
`TvLibrary.kt`, `TvMenuBranches.kt`, `TvMenuPage.kt`, `TvMenuTest.kt`,
`docs/{system-architecture.md,development-roadmap.md,project-changelog.md}`. No version
bump this pass — same 0.72.0 the feature itself shipped under.

### Tests status (this pass)

`./gradlew testDebugUnitTest lint :app:assembleDebug --rerun-tasks` (whole project, every
task re-executed) green, 699/699. One real lint regression caught and fixed mid-pass (not
left in): `QueueFocus.modifierFor` tripped `ModifierFactoryExtensionFunction` (a plain
method returning `Modifier` rather than an extension on it) — confirmed via `:app:lintDebug`
going from "no new issues" to "1 warning" and back after the fix (`Modifier.trackedBy`).

### Unresolved / flagged, not changed further

- `FilmPreloader.kt` at 335 lines (was 316 before this whole plan started) — see "Files
  touched" above.
- Device verification (TV box + tablet) for the whole of phase 03b, including the two new
  user decisions, still outstanding — both devices in use by other work this session.
