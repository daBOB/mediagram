# Code review: the Preload control on the film pages (0.71.0, uncommitted on 5c966a9c)

## Scope
- **Files:** everything in `git status` under android/, plus DESIGN.md, the changelog and the manifests. About 250 lines of production code and about 500 lines of tests are new.
- **Spec:** phase-03 and the decisions in plan.md. I also read the engine (`FilmPreloader.kt`, `FilmPreloadState.kt`) and the LAN route (`LanChunkProtocol.setStatus`).
- **Build:** `./gradlew testDebugUnitTest lint :app:assembleDebug` is green. Every task came back UP-TO-DATE after the implementer's own green run.
- **Probes:** I copied the worktree to the scratchpad (rsync without build output), wrote probe tests there, and changed nothing in the real worktree. I used:
  - real `TitlePreloadViewModel` behind the real `rememberFilmPreloadUi`, with counting fakes;
  - `@GraphicsMode(NATIVE)` layout probes at 360dp and 411dp;
  - the real `FilmPreloader`;
  - the TvAppFixture and LibraryFlowFixture routes;
  - TV key-event focus probes.

  I also applied the fixes suggested below in the scratch copy. They were verified there, and the full `:ui-mobile:testDebugUnitTest` suite stayed green with them in.

## Overall
The structure is sound. There is one set of pure label functions shared by both surfaces, the screens stay free of Hilt, and only `FilmPreloading`'s public methods are used. The TV Play row keeps its initial focus.

Three defects pass CI and break on a real device:
- **Server polling:** it runs once per progress tick instead of every 5 s.
- **Phone ⋯ menu:** it disappears on every real phone width, which also makes "Remove preload" unreachable.
- **NeedsSpace:** it is a dead end once the budget has been raised.

Beyond those, the "x of y GB" figures are wrong for the first gigabyte of every film.

## Findings, ranked

### High

**H1. The state and server-line flows are rebuilt on every recomposition, so the 5 s poll becomes a poll per progress tick**
- **Where:** `ui-mobile/.../ui/TitlePreloadWiring.kt:27-28`, `ui-tv/.../TvLibraryCatalogFrames.kt:104-105`.
- **Cause:**
  - `viewModel.serverLine(...)` and `viewModel.stateOf(...)` each return a new cold `Flow` on every call.
  - `collectAsStateWithLifecycle` is `produceState(…, this, …)`, keyed on that Flow instance.
  - `rememberFilmPreloadUi` returns a value, so it has no restart scope of its own. Its state reads invalidate the caller's scope.
  - So every state emission (progress, about 4 a second under `ProgressThrottle`), every change in the server string, and every unrelated recomposition of the title branch rebuilds both flows. Each rebuild restarts `flatMapLatest`, which fetches immediately.
- **Probe (phone wiring, real ViewModel, counting `LanChunkProtocol`):**

  | Situation | GETs expected | GETs measured |
  |---|---|---|
  | Page opens while Idle | 1 | 2 |
  | 10 Running progress ticks, no time passing | 2 | 13 |
  | Server answer changes on every call, no time passing | 1 | 31 (the fake's cap) |

  `stateOf` was subscribed 44 times where 22 were expected. For Idle and Done, every re-subscription also re-reads the cache (`heldSets.heldBytes`).
- **Result:** while a film is Running on a visible page, the phone makes about 4 new HTTP connections a second to the home server instead of one every 5 s. That is about 20 times what the spec allows, and those GETs take the server's index lock alongside the preload's own PUTs.
- **Fix:** verified in the scratch copy, where the probe went back to 1 / 2 / 1:
  ```kotlin
  val state by remember(viewModel, set.setId, set.totalBytes) { viewModel.stateOf(set.setId, set.totalBytes) }
      .collectAsStateWithLifecycle(initialValue = FilmPreloadState.Idle(0L, set.totalBytes))
  val serverLine by remember(viewModel, set.setId, set.totalBytes) { viewModel.serverLine(set.setId, set.totalBytes) }
      .collectAsStateWithLifecycle(initialValue = null)
  ```
  Make the same change on TV, and keep the probe as a regression test (see the recipe at the end).

**H2. Phone: the pill row overflows, and ⋯ (editor's choice, Remove preload) is 0dp wide on real phones**
- **Where:** `ui-mobile/.../catalog/TitlePills.kt:54` (`Row` with no wrap or scroll), plus the new pill at :60.
- **Probe** (`@GraphicsMode(NATIVE)`, `TitleDetailScreen`, adult profile):
  - **360dp, no preload (baseline):** Play, My List and ⋯ all fit, and ⋯ is 58dp wide.
  - **360dp, Idle "Preload · 5.8 GB":** My List is squeezed to 51dp wide and 196dp tall (its text wraps letter by letter). ⋯ is 0dp wide.
  - **360dp, Running, and 360dp, Done:** ⋯ is 0dp wide. For Done, the only way to Remove on the phone is gone.
  - **360dp, Failed ("Could not preload this film"):** My List and ⋯ are both 0dp.
  - **411dp (Pixel), Idle:** My List wraps to two lines and ⋯ is 0dp.
  - **411dp, Paused(TimeLimit) or Failed:** My List and ⋯ are both gone.
- **Why the tests missed it:** Robolectric's default LEGACY graphics mode measures text at almost zero width. "▶ Play" measured 58dp there. `assertIsDisplayed` on the Preload pill alone cannot see the overflow.
- **Result:** this breaks the requirement that Play, My List and ⋯ stay unchanged.
- **Fix:** verified. Use `FlowRow(horizontalArrangement = spacedBy(small), verticalArrangement = spacedBy(small))`, which the codebase already uses in `GenreLinks.kt` and `ShelfPager.kt`. My List and ⋯ then wrap to a second line at full size, and Play keeps its place. Add a NATIVE-graphics width test at w360dp.

**H3. NeedsSpace is a dead end: after the budget is raised, nothing on the page can retry**
- **Where:** `feature/player/.../FilmPreloadLabels.kt:51` (NeedsSpace maps to `NONE`, so the pill is disabled).
- **Engine behaviour:** `FilmPreloader.stateOf` returns the NeedsSpace override unchanged, and only a fresh `enqueue` re-judges `fits`.
- **Probe (real `FilmPreloader`):**
  - enqueue with budget 500, film size 1000: NeedsSpace;
  - raise the budget to 10 000 and advance 60 s: still NeedsSpace;
  - enqueue again: Done.
- **Scenario:**
  1. The viewer taps "Raise the cache budget".
  2. Settings › Storage opens, and they raise the budget.
  3. Back on the film, the pill still reads "Needs 5.8 GB", disabled, with the same link.
  4. It stays that way until the process dies.
- **Fix:** map NeedsSpace to `ENQUEUE` (retry) and label it along the lines of "Needs 5.8 GB · Try again". Keep the storage link, and update the DESIGN.md line on Line and Quiet styles.

### Medium

**M1. "x of y GB" drops the unit of the held amount**
- **Where:** `FilmPreloadLabels.kt:71, 81, 86`. `sizeWithoutUnit` strips the held amount's own unit.
- **Probe:** `preloadBarLabel(500 MB, 5.8 GB)` gives "500 of 5.8 GB · 8%". 1 MB gives "1.0 of 5.8 GB · 0%". The server line gives "Home server: 812 of 5.8 GB".
- **Result:** the bar and the server line are wrong for the first gigabyte or so of every film, on both surfaces. The test only covers 40% of 5 GB, where both numbers are in GB.
- **Fix:** keep the unit whenever it differs from the total's (`humanSize(held)` + " of " + `humanSize(total)`, the shape `heldOfBudget` already uses), or format the held amount in the total's unit.

**M2. Paused(TimeLimit): the accessibility text says one thing and the tap does another**
- **Pill hint:** `FilmPreloadLabels.kt:63-65`. TimeLimit maps to `ENQUEUE`, so its hint comes out as "Tap to preload this film to your device." The "Tap to resume." case sits in the `CANCEL` branch, which TimeLimit never reaches, so it is dead code.
- **Phone bar:** `TitlePreload.kt:109` hard-codes "Tap to cancel.", but the bar is wired to `onToggle`, which for TimeLimit resumes.
- This breaks the promise in the KDoc at :38 that "neither can say one thing and do another".
- **Fix:** move the TimeLimit case into the `ENQUEUE` branch, and have the bar use `preloadAccessibilityHint(state)`. Consider changing the visible label to "Paused · tap to resume".

**M3. TV: after OK on "Remove preload", focus jumps to the "Overview" tab**
- **Where:** `TvTitlePage.kt:196`.
- **Probe:** Right, Right, OK on Remove. Focus lands on the Overview tab at the top of the page, because the focused plate was removed from composition.
- **Fix:** verified. Pass a `FocusRequester` to `TvPreloadPlate` (its `focusRequester` parameter at `TvTitlePreload.kt:28` is currently never passed) and call `plate.requestFocus()` before `p.onRemove()`. Focus then lands on "Preload · 4.7 GB", and Left goes back to Play.

**M4. The phone wiring has no test coverage, and its fixture will crash the first time a test opens a film**
- `LibraryFlowFixture` does not register `TitlePreloadViewModel` and holds only episodes. Nothing tests `rememberFilmPreloadUi` or the `MenuScreen.Storage` branch on the phone.
- The first test that opens a film will hit the same `NoSuchMethodException` the implementer fixed in `TvAppFixture`.
- The TV fixture's relaxed mock never leaves Idle, so the route from NeedsSpace to Storage is untested on TV as well.
- My probes show both routes work:
  - **Phone:** the film page, "Raise the cache budget", then Storage (its Budget control is shown); Back returns to the film.
  - **TV:** Down from Play reaches the link; OK lands on the Storage index row; Back puts focus on Play.
- Keep both as tests: add a film and the ViewModel to `LibraryFlowFixture`, and stub `stateOf` and `serverLine` in `TvAppFixture`.

**M5. TV has no bar (to be confirmed with the user)**
- The success criterion says "bar climbs" on the tablet and on the TV box.
- TV shows only the text "2.1 of 5.8 GB · 36%", which cannot take focus. The changelog nonetheless says "thin full-width bar … tap or OK to cancel" for both surfaces.
- Either add a thin bar on TV, or write the difference down in DESIGN.md and correct the changelog.

### Low
- **L1. Failed:** the visible label is only the reason; the word "retry" appears only in the accessibility hint. The spec asks for "short reason + retry". Core sentences can be long, which makes the pill wide (on TV it reached 850 of 960dp). Suggest "Preload failed · Retry", with the reason on a second line or in the description.
- **L2. Wrong label for one frame:** `initialValue = Idle(0, total)` means a Done, Running or Queued film shows "Preload · 5.8 GB" for a frame on every page open and every rotation. `stateOf` for Idle and Done reads the cache off-main first. A nullable initial value that renders nothing until the first emission avoids it.
- **L3. Edge-case labels:**
  - "Preload · 0% held" appears when 0 < held < 1% of the film.
  - "Preload · 100% held" appears for a film playback has already cached in full. One tap turns it Done straight away.
  - A film with `totalBytes == 0` shows "Preload · 0 B", and tapping it does nothing, because the engine returns early. Hide the control when `totalBytes <= 0`.
- **L4. Comment voice:** `TitlePreload.kt:78` and `:89` say "the plan's own wording". Those are plan references in code comments; state the rule itself instead ("the bar stays through a pause").
- **L5. Docs vs code:**
  - DESIGN.md and the implementer's table say pauses are Quiet, but TimeLimit draws Line, because `preloadIsAffirmative` counts `ENQUEUE`.
  - The KDoc at `FilmPreloadLabels.kt:54` links to `ui.settings.LinePill`, which feature/player cannot see.

### Nits
- `TitlePreloadUi` and `TvTitlePreloadUi` are identical data classes. Data-class equality over lambdas means nothing. One shared type in feature/player would do.
- `fetchServerLine` reimplements `LanCacheRuntime.server()` (feature enabled and a verified server). Injecting the runtime would say the same thing once.
- File sizes: TitleDetailScreen.kt is 244 lines, TvTitlePage.kt 214, LibraryFlowBranches.kt 343 (it was 329 before). The implementer's reasoning for leaving them is acceptable.

## The two flagged judgement calls
1. **Tapping Paused(TimeLimit) resumes: agree.**
   - `FilmPreloader.pauseForTimeLimit` (FilmPreloader.kt:144-168) drops every item from the queue ("resuming … is a fresh enqueue").
   - Mapping the tap to cancel would only clear the label back to "Preload · x% held".
   - Resuming from the page is a foreground start, which Android permits. That still needs checking on a device after a real 6 h limit.
   - Fix the accessibility text first (M2).
2. **Touching MenuScreen, TvMenuBranches and TvAppFixture: agree, and all three were necessary.**
   - `MenuScreen` is saved in the frame stack by name (`LibraryPositions.kt:142, 201`), not by position, so adding a case is safe.
   - Every `when (menuScreen)` is exhaustive and was updated on both surfaces. No code looks a `MenuScreen` up from its `Destination`.
   - An older build reading "Storage" gets `null`. TV then leaves the screen at once; the phone returns early. Only a downgrade with saved state would hit that.
   - The same fixture fix is still owed on the phone (M4).

## Spec checklist
- **Films only:** met. Both surfaces check `Kind.MOVIE`, and pages for other kinds show nothing.
- **Kids profiles:** not gated, and a kids profile gets ⋯ only for Remove.
- **Each engine state gets its label and action:** met, except NeedsSpace (H3), TimeLimit's accessibility text (M2) and Failed's retry wording (L1).
- **Raise the cache budget lands on Settings › Storage:** met on phone and TV (probes).
- **Server line:**
  - LAN cache on + a verified server: met. That is the same meaning of "paired" as `LanCacheRuntime`.
  - bytesHeld > 0: met.
  - "y" is the film's own size: met.
  - Hidden when there is no answer: met.
  - Stops when the page leaves or goes to the background: met.
  - Every 5 s only while visible and Running: **not met** (H1).
  - Format of the held amount: **wrong** (M1).
- **Play, Resume, My List and ⋯ unchanged:**
  - TV: met.
  - Phone: **not met** (H2).
- **TV focus:**
  - Play keeps its initial focus.
  - Right reaches Preload, then Remove.
  - The disabled "Preloaded ✓" and "Needs …" plates still take focus, so a Running-to-Done change keeps focus.
  - Remove loses focus (M3).
- **One ViewModel shared across pages, not one per film:** harmless. It is activity-scoped and holds no state, and only the page on top is composed.
- **Process death:** the engine's overrides are not saved, so the page shows Idle with the real held percentage. This is accepted by the plan.
- **Only `FilmPreloading`'s public methods are used:** met (stateOf, enqueue, cancel, remove).
- **Versions (0.71.0) and changelog:** in step. The changelog's claim about a TV bar needs correcting (M5).

## What only a device can confirm
- The success walk on the tablet and the TV box:
  1. The bar climbs.
  2. Leaving the app and coming back, it is still climbing.
  3. With something playing, it reads "Paused while playing".
  4. It reaches "Preloaded ✓", and the film then plays with no Telegram reads.
  5. The server line reaches the film's size.
- Pill wrapping at the user's real font scale and in tablet portrait (after the H2 fix).
- Whether the one-frame Idle label (L2) is visible, and how TalkBack reads the merged bar node (range info plus description).
- Resuming after TimeLimit: whether the `dataSync` service may start again from the foreground after the 6 h limit.
- Whether the focus ring on disabled TV plates is visible from across a room, on the real remote.
- Against an old server, the 404 hides the line.

## Probe recipe (to keep as regression tests)
- **H1:** `CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { TitleDetailScreen(film, …, preload = rememberFilmPreloadUi(film) {}) }`.
  - Use `mockkStatic(::HiltViewModelFactory)` answering `secondArg()`, and a real `TitlePreloadViewModel` over a fake engine and a counting `LanChunkProtocol`.
  - Push 10 `Running` states, calling `waitForIdle` after each.
  - Assert `setStatus` was called 2 times.
- **H2:** `@GraphicsMode(GraphicsMode.Mode.NATIVE)` with `qualifiers = "w360dp-h800dp"`. Assert that `onNodeWithContentDescription("More").getBoundsInRoot()` is wider than 0.
- **H3:** in FilmPreloaderTest, use a `fits` whose budget can be changed. After a NeedsSpace, raise the budget and assert that `preloadTapAction(state) != NONE`.

## Unresolved questions
- M5: should TV draw a bar too, or is text only a deliberate TV difference to write down?
- H3 wording: "Needs 5.8 GB · Try again" on the pill, or a separate "Try again" beside the storage link?

**Status:** DONE_WITH_CONCERNS
**Summary:** The structure and contracts are sound, and the build is green. Three high-severity defects pass CI but break on a device: server polling runs once per progress tick, the phone's ⋯ menu disappears (so Remove is unreachable), and NeedsSpace cannot be retried after the budget is raised. The byte figures are also wrong for the first gigabyte of each film. Each fix was verified in a scratch copy.
