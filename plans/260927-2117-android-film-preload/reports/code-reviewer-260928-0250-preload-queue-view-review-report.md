# Code review: the preload queue view (0.72.0, uncommitted on feat/android-film-preload)

## Scope
- Diff: 43 modified and 8 new files (engine snapshot/overview, labels, VM, phone/TV Preloads pages, menu entries, docs, version bump), plus the uncommitted TV-box docs (`docs/system-architecture.md` § Film preload, `docs/development-roadmap.md`, the device report).
- Gate: `./gradlew testDebugUnitTest lint :app:assembleDebug` came back **green**, with every task UP-TO-DATE against the implementer's `--rerun-tasks` run (inputs unchanged).
- Probes: run in a scratch copy of the worktree, under Robolectric, with real D-pad key events, NATIVE text measurement, and the real `FilmPreloader` with a shared `DownloadLane`. The worktree was not touched.

## Verdict
The engine rules are untouched. `FilmPreloader` only gains a `val`, and `publish()` adds one snapshot from inside the same lock. Saved-state compatibility is safe: `FrameKind` is encoded by name and `decode` drops unknown kinds. The `MenuActions` change is safe: it has two constructors (phone `LibraryFlow`, TV `tvMenuActions`), and both are updated. The Preloads page observes no per-film flows. The budget read is main-safe (`occupancy` suspends and opens the cache on IO). There is no crash path.

Six Medium issues remain. Two are spec misses (the TV bar and flagged call 2), two are TV focus problems, one is a kids-profile leak, and one is stale docs.

## Findings (ranked)

### M1: A dequeued film waiting for the lane shows as "Preloading 0%". This is flagged call 2; reject it.
`core/playback/src/main/kotlin/FilmPreloadOverview.kt:61-62` (the `else ->` branch).
- **Probe:** Der Pate is 50% held and a series preload holds the lane. `stateOf` is `Queued`, but the overview returns `Running(heldBytes=0)`. The film page says "Queued", the Preloads page says "Preloading · 0 of … · 0%", and Dune's page says "Queued · after Der Pate, 0%". Only after the lane frees does it jump to `Running(500)`.
- **Why it matters:** the window is not a moment. It lasts for the whole of a series-episode write. It also contradicts the engine's own rule in `FilmWriteAttempt.kt:33-37`: "never shows Running while merely waiting for the lane".
- **Fix:** one line. `else -> FilmPreloadRow.Waiting(active.setId, active.title, active.totalBytes)`. The Preloads page then lists the film first under Queued, which agrees with its film page. The next film reads "Queued · 1 ahead", which is true. Flip `FilmPreloadOverviewTest.aJustDequeuedActiveFilmReadsAsRunningAtZero` to match. The two `FilmPreloaderTest.queueOverview*` tests still hold, because their writer has the lane.

### M2: The TV Preloads page has no bar (spec: "Preloading (title, bar, …)")
`ui-tv/.../catalog/TvPreloadsPage.kt:100-108` draws only the caption text.
- The changelog says "Preloading (title, bar, …)" for both surfaces.
- **Fix:** in `TvPreloadingRow`, replace the two `TvQuietLine`s with `TvPreloadDetailLines(preloadRowState(row), serverLine = null, onOpenStorage = {})`. It already draws `TvPreloadBar` and the caption. This is DRY and matches the film page.
- The phone has the same duplication at `PreloadsScreen.kt:87-100`, where fraction, bar and caption are a copy of `PreloadProgressBar`. That one is Low: reuse it, or extract the bar.

### M3: A kids profile sees grown-up preloads (titles, count, Cancel)
`LibraryBrowseBranches.kt:156-167`, `TvLibraryExtraFrames.kt:175-195`, `FilmPreloadLabels.kt` `queuedAheadLabel`, and the `queueCount` gate in `LibraryFlow.kt`/`TvLibrary.kt`.
- **Scenario:** a grown-up queues Der Pate and the TV is switched to the kids profile. The engine is a `@Singleton`, so:
  - the menu shows "Preloads · 1";
  - the page lists "Der Pate" with Cancel;
  - a kid's own queued film reads "Queued · after Der Pate, 36%";
  - tapping the row pushes TITLE, which does not resolve in the kids catalogue, so `ResolvedBranch` pops it (a dead tap). With `KidsEmpty` it spins "Loading…" instead.
- **Why it matters:** there is no notification leak today, because `POST_NOTIFICATIONS` is undeclared, so this change is the first place these titles surface. It also contradicts the household rule in the profile-roles design on `main` ("Rated above N — hidden").
- **Fix:** keep only rows whose `setId` resolves in the current `catalogState` (`catalogState.mediaSet(id) != null`). Apply this to the page rows, the count, and `queuedAheadLabel`. When the head does not resolve, fall back to "Queued · N ahead" without a title. Grown-ups resolve every film, so nothing changes for them. This is a policy call; see Q1.

### M4: The TV page forgets which row opened a film, and steals focus when the head changes
`TvLibraryExtraFrames.kt:188-191` records `restore.opened(here, setId)`, but `TvPreloadsPage` takes no `restoreKey`. Separately, `TvPreloadsPage.kt:46` runs `LaunchedEffect(firstRowId) { firstFocus.requestFocus() }`.
- **Probe:** open "Film 1" (row 2), then Back. Focus lands on "Film 0" (row 1). Latest, Genres and the other sibling frames return to the row that was opened.
- Because the effect is keyed on the head, focus also jumps to the new first row whenever the running film finishes or is cancelled. A viewer walking down to Cancel row 3 gets pulled back to the top.
- **Fix:** pass `restoreKey = restore.of(here)`. Request focus once on arrival: the restored row if it still exists, otherwise the first row. After arrival, re-request only if the focused row itself left the page. Add a D-pad test for open, Back, and "focus is still on the opened row".

### M5: The TV menu's "Preloads · n" row is below the fold of a menu that cannot scroll
`ui-tv/.../system/TvMenuPage.kt:61-84`.
- **Probe (w960dp-h540dp harness):** the row's bounds are 0×0. Nine D-pad Downs stop on Genres, which is itself clipped at 513dp.
- `TvMenuTest.thePreloadsRowNamesTheCount…` reaches the row with a semantics click, so it could not see this.
- **On the real box:** `bug06-menu.png` (1920×1080, 2×) has rows at 39.5dp pitch, and Genres ends at about 425dp. The row should fit at font scale 1.0 with about 1.5 rows spare. At a larger TV font scale it will not, and the TMDB-key note adds a line.
- **Fix:** `Modifier.fillMaxSize().verticalScroll(rememberScrollState())` on the Column. Verified in the probe: the row becomes D-pad reachable and all of `TvMenuTest` stays green. Also change the new test to reach the row with DPAD_DOWN.

### M6: The docs are inaccurate for this release
- `docs/system-architecture.md` § Film preload says "the queue's only visible progress is the film page itself (`TitlePreloadViewModel`, polling §12's `GET /v1/sets/{id}` every 5s while running)". This ships stale:
  - The Preloads page, the queued-ahead labels and the `QueueSnapshot`/`queueOverview` projection are not mentioned.
  - The sentence also mixes up two things. The bar comes from the engine's progress callback; the 5 s poll is only the home-server line.
- `docs/development-roadmap.md` § Android: film preload:
  - Its header says "Complete." while the Phase 4 row says "In review" and the tablet pass is pending.
  - It covers only 0.70.0–0.71.0, with no 03b row or 0.72.0 entry. `plan.md` has both.
- `docs/project-changelog.md` 0.72.0 claims a bar on both surfaces, which is false for TV until M2 is fixed.
- The other claims were checked against code and hold:
  - the `fits` check at the front of the queue;
  - the open-title reserve;
  - `STATE_IDLE` as the only thing that clears it;
  - `POST_NOTIFICATIONS` undeclared;
  - no persistence;
  - the shared writer/lane and the LAN PUTs.

### Low
- **L1: A background time limit empties the page.** `pauseForTimeLimit` drops every film from the queue, which is correct engine behaviour. As a result the Preloads page and the menu entry vanish, although each film page says "Paused (background limit reached)" and waits for a tap. A 30 GB queue on the box can hit the 6 h ceiling. See Q2.
- **L2: Labels wrap at 360dp** (NATIVE probe). "Needs 30 GB · budget is 8.0 GB · Try again" takes 2 lines at 360dp and 1 at 411dp. "Queued · after Chihiros Reise ins Zauberland, 36%" takes 2 lines at both widths. The `Failed` label already ellipsizes to 28 characters for this reason. Ellipsize the title inside `queuedAheadLabel` (for example to about 20 characters) with the same `ellipsize`.
- **L3: "8.0 GB" vs the spec's "8 GB".** This is `humanSize`'s normal one-decimal format below 10, the same as every other size in the app. Accept it.
- **L4: Weak test assertions.**
  - The TV Back test asserts `onNodeWithText("Menu").assertExists()`, which matches both the menu-page title and the masthead button. Back actually lands on the home wall, the same as Latest and Genres.
  - Neither the TV nor the phone menu test drives the row by D-pad.
- **L5: Small recomputation.** `catalogState.heldFilms()` walks the whole shelf on every progress-tick recomposition. That is negligible, but `remember(catalogState)` is free. `queueCount` could add `distinctUntilChanged()`, although State already dedupes.
- **L6: Comment voice.**
  - Some docs narrate history rather than explaining why: `FilmPreloading.queueOverview` ("every test fake built before a Preloads page needed one"), `CacheBudgetQuery.Noop` ("before NeedsSpace named its own budget"), and `TitlePreloadViewModel.queueRows` ("the trap those two once fell into").
  - `TvMenuPage`'s KDoc still says "the same five items".
  - `MenuActions`' "sixth, conditional row" is confusing next to the phone's nine rows.
- **L7: File sizes.** `TvLibraryExtraFrames.kt` went from 182 to 218 lines and `TvLibraryCatalogFrames.kt` from 196 to 218. Growth elsewhere was in files already over 200 lines. The new files are all 120 lines or fewer.

## The two flagged calls
1. **Menu placement (after Genres on both surfaces): accept.** It sits in the same relative place, after the browse utilities, on both surfaces. The phone/TV order difference was already there. The row moves to the rail at the merge anyway.
2. **Just-dequeued row reads "Running at 0%": reject.** See M1. It holds for a whole lane wait, not just an instant, and it zeroes a partially held film.

## Spec checklist
- Queued labels, NeedsSpace budget, menu row hidden while idle, and count = running + queued: correct on both surfaces.
- Empty sections hidden, idle text shown, rows open their film, Cancel/Remove reach the right id, queued order kept: correct.
- TV bar: missing (M2).
- On this device = `Entry.Film` ∩ `heldIds`: correct. Keys are unique (films sit only on the Movies shelf) and the list is kid-filtered, because the catalogue is.
- Android-only note: present in DESIGN.md and the doc comments.
- Version: 0.72.0 in all three manifests; `versionCode` untouched. `main` is at 0.69.4 and the sibling worktrees also bump, so renumber at merge.

## Device-only
- TV menu: the row reachable by remote at the box's density and font scale; a long Preloads list scrolling with focus.
- How long the lane wait lasts in practice after leaving a show, which decides how visible M1 is.
- A kids profile on the box with a grown-up's queue.
- The NeedsSpace line against the box's real 8 GB budget.
- Pill wrapping with real fonts on the tablet; the tablet pass in general.

## Unresolved questions
- **Q1:** Should kids see only preloads their catalogue can resolve (M3)? Or should Preloads be hidden on kids profiles entirely until profile roles land?
- **Q2:** Should films paused by the background limit stay listed on the Preloads page, with a Resume action (L1)? That needs the engine to expose them; the queue itself would stay unchanged.
