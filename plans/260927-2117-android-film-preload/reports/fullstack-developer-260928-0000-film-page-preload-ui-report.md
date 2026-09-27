## Phase Implementation Report

### Executed Phase
- Phase: phase-03-film-page-preload-button-progress
- Plan: /home/andre/Workspace/mediagram-preload/plans/260927-2117-android-film-preload
- Status: completed

### Files Modified

Created:
- `android/feature/player/src/main/kotlin/player/FilmPreloadLabels.kt` (86) — pure `FilmPreloadState → String` functions shared by both surfaces: `preloadLabel`, `preloadBarLabel`, `preloadServerLine`, `preloadAccessibilityHint`, `preloadTapAction`/`preloadIsAffirmative`/`preloadIsEnabled`.
- `android/feature/player/src/main/kotlin/player/TitlePreloadViewModel.kt` (91) — the Hilt ViewModel: `stateOf`, `toggle`, `remove`, `serverLine` (the 5s-while-Running poll).
- `android/feature/player/src/test/kotlin/TitlePreloadViewModelTest.kt` (148) — labels, `toggle` dispatch per state, server-line gating and polling start/stop.
- `android/ui-mobile/src/main/kotlin/ui/catalog/TitlePreload.kt` (128) — `PreloadPill`, `PreloadRemoveMenuItem`, `PreloadProgressBar`, `PreloadStorageLink`, `PreloadServerLine`, `TitlePreloadUi`. No Hilt, no engine import — reads `FilmPreloadState` and the shared labels only.
- `android/ui-mobile/src/main/kotlin/ui/TitlePreloadWiring.kt` (36) — `rememberFilmPreloadUi`, split out of `LibraryFlowBranches.kt` purely for the line-count guideline (see Issues).
- `android/ui-mobile/src/test/kotlin/ui/catalog/TitlePreloadTest.kt` (221) — every state through `TitleDetailScreen`, at default width and `w1164dp-h777dp`.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvTitlePreload.kt` (75) — `TvPreloadPlate`, `TvPreloadRemovePlate`, `TvPreloadStorageRow`, `TvPreloadDetailLines`, `TvTitlePreloadUi`.
- `android/ui-tv/src/test/kotlin/ui/tv/catalog/TvTitlePreloadStateTest.kt` (134) — real `pressKey`/`performSemanticsAction` key events, focus order, every state.

Modified:
- `android/ui-mobile/src/main/kotlin/ui/catalog/TitlePills.kt` (90, +30) — added `preloadPill`/`preloadRemoveItem` slots; the ⋯ menu now opens on either `onToggleEditorsChoice != null` or a removable preload, not only the former (a kids profile keeps Preload's own Remove even with no editor's-choice pin).
- `android/ui-mobile/src/main/kotlin/ui/catalog/TitleDetailScreen.kt` (244, +17) — `preload: TitlePreloadUi? = null` param, wired into `TitlePills`' new slots plus a bar/link/server-line block below the pill row.
- `android/ui-mobile/src/main/kotlin/ui/LibraryFlowBranches.kt` (343, +14 net of the extraction) — `TitleDetailScreen`'s `preload` argument, gated `title.kind == Kind.MOVIE`; a new `MenuScreen.Storage` branch.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvTitlePage.kt` (214, +29) — `preload: TvTitlePreloadUi? = null`; Play and the preload/remove plates now share one `Row` (D-pad right from Play reaches Preload, right again reaches Remove); `TvPreloadDetailLines` under it.
- `android/ui-tv/src/main/kotlin/ui/tv/TvLibraryCatalogFrames.kt` (196, +27) — `rememberTvFilmPreloadUi`, gated `Kind.MOVIE`, wired into `TvTitleFrame`.
- `android/ui-tv/src/main/kotlin/ui/tv/TvMenuBranches.kt` (+2) — `MenuScreen.Storage -> TvSettingsScreen(initial = SettingsSection.STORAGE)`.
- `android/feature/catalog/src/main/kotlin/MenuScreen.kt` (25, +7) — new `Storage(Destination.Settings)` case, the same direct-section shape `System` already has.
- `android/feature/player/src/test/kotlin/FakePreload.kt` (111, +77) — `FakeFilmPreloading`, `FakeLanServerSource`, `FakeLanChunkProtocol`.
- `android/ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt` (296, +7) — registered `TitlePreloadViewModel` in the fixture's `hiltViewModel()` map (see Issues — this was a real regression, now fixed).
- `Cargo.toml`, `Cargo.lock` (five workspace members' `version` only, via `cargo check -p mediagram-cache`), `web/package.json`, `android/app/build.gradle.kts` (`versionName` only) — `0.70.1` → `0.71.0` by regex, not exact string; `versionCode` untouched.
- `docs/project-changelog.md` — new `0.71.0` entry at the top.
- `DESIGN.md` — a paragraph under "Pill (line / quiet)" naming the Preload control as Android-only and naming which pill state maps to Line vs Quiet.
- `plans/260927-2117-android-film-preload/{plan.md,phase-03-*.md}` — status → done, Todo boxes ticked.

### Tasks Completed
- [x] ViewModel + test (`TitlePreloadViewModel`, `TitlePreloadViewModelTest`)
- [x] Phone/tablet control + tests (`TitlePreload.kt`, `TitlePreloadTest.kt`, default + `w1164dp-h777dp`)
- [x] TV control + test (`TvTitlePreload.kt`, `TvTitlePreloadStateTest.kt`, real key events)
- [x] Server line, hidden on null/0/old server, polling gated on visibility+Running
- [x] Tests green, version bumped, changelog written

### The state → label table, as built

| `FilmPreloadState` | Phone pill (style) | TV plate | Bar / detail lines | Tap / OK |
|---|---|---|---|---|
| `Idle(0, total)` | "Preload · 5.8 GB" (Line) | same | — | Enqueue |
| `Idle(held>0, total)` | "Preload · 36% held" (Line) | same | — | Enqueue |
| `Queued` | "Queued" (Quiet) | same | — | Cancel |
| `Running(held, total)` | "Preloading" (Quiet) | same | bar/quiet line "2.1 of 5.8 GB · 36%" | Cancel |
| `Paused(_, _, Playing)` | "Paused while playing" (Quiet) | same | bar/line stays (same figure) | Cancel |
| `Paused(_, _, Metered)` | "Waiting for Wi-Fi" (Quiet) | same | bar/line stays | Cancel |
| `Paused(_, _, TimeLimit)` | "Paused (background limit reached)" (Quiet) | same | bar/line stays | **Enqueue** (resumable — see Decisions) |
| `Done` | "Preloaded ✓" (Quiet, disabled) + ⋯ "Remove preload" | same + second "Remove preload" plate | — | none on the pill itself |
| `NeedsSpace(total)` | "Needs 5.8 GB" (Quiet, disabled) + "Raise the cache budget" link | same + "Raise the cache budget" row | link → `MenuScreen.Storage` | none on the pill itself |
| `Failed(reason)` | `reason` text itself (Line) | same | — | Enqueue (retry) |

Server line (`Home server: x of y GB`, `y` always the film's own `totalBytes`): rendered under the bar when one is showing, else under the button row; hidden whenever `bytesHeld <= 0` (covers the LAN cache off, no server paired, an old server returning 404→null, or a real but empty answer alike, since `TitlePreloadViewModel.fetchServerLine` returns `null` for all of them uniformly).

### Decisions where the spec left room

1. **What a tap/OK does per state** wasn't spelled out for every state — built `preloadTapAction` (`FilmPreloadLabels.kt`) as the one place that decides: Idle/Failed/Paused(TimeLimit) enqueue, Queued/Running/Paused(Playing|Metered) cancel, Done/NeedsSpace do nothing on the main control (their own actions sit beside it). The `TimeLimit` case reads "resumable from the page" in the engine's own doc comment (phase 02's report) as the reason it exists as a distinct `PauseReason` at all — a plain cancel-only reading would have made that state indistinguishable from Playing/Metered in practice, so tapping it re-enqueues instead of clearing the queue slot the engine already gave up.
2. **`Paused(TimeLimit)`'s visible label** — spec names it only as "time limit", not a fixed string. Wrote "Paused (background limit reached)" (contentDescription adds "Tap to resume.") — plain language for Android's `dataSync` foreground-service ceiling, no jargon.
3. **NeedsSpace's own tap target for "Raise the cache budget"** — this needed Settings to open directly on Storage, which `SettingsScreen`/`TvSettingsScreen` already support (`initial: SettingsSection`) but nothing yet routed to. Added one `MenuScreen.Storage` enum case (`feature/catalog/MenuScreen.kt`) mirroring `System`'s existing direct-section shape, plus one `when` branch on each surface's menu dispatcher — outside the phase's literal file list but the same "TV equivalent"/"wiring" scope the phase's own Related Code Files section names, and a one-line, additive, non-breaking enum addition.
4. **Bar caption during a pause** — the plan says "bar stays"; read literally, that's the bar's own figure (held/total bytes don't reset on a pause), not a second copy of the pause reason. `PreloadProgressBar`/`TvPreloadDetailLines` always show the numeric "x of y GB · %" caption; the pause reason is said once, by the pill/plate's own label above it.
5. **Hilt stays out of `TitleDetailScreen`/`TvTitlePage`** — both screens already take every other optional feature (editor's choice, watchlist, credits) as plain callbacks/data, tested today with no Hilt setup at all (`FilmPageTest`, `TvTitlePageStateTest`). `TitlePreloadUi`/`TvTitlePreloadUi` follow that shape; `hiltViewModel<TitlePreloadViewModel>()` is only ever called from the wiring layer (`LibraryFlowBranches.kt`/`TvLibraryCatalogFrames.kt`), the same place `BrowseViewModel` already is. This kept every existing Robolectric test compiling and passing unchanged, and let the new tests skip Hilt entirely too.
6. **Kids profiles** — `preload` is computed unconditionally (not gated on `kidsProfile`, unlike `onToggleEditorsChoice`), per the acceptance criterion; `TitlePills`' ⋯ menu visibility condition was widened from "only if `onToggleEditorsChoice != null`" to "either that or a removable preload", so a kids profile with a `Done` film still gets a way to remove it.

### Tests Status
- Type check: pass — `./gradlew compileDebugKotlin` (whole project) clean.
- Unit tests: pass — `./gradlew testDebugUnitTest` (whole project, `--rerun-tasks`) green, including the new `TitlePreloadViewModelTest` (13 cases), `TitlePreloadTest` (16 cases, phone/tablet), `TvTitlePreloadStateTest` (8 cases, real key events), and every pre-existing test unaffected (`FilmPageTest`, `TvTitlePageStateTest`, `TvMenuTest`, `TvLibraryTest`, `TvSearchAndGenreTest` all re-verified individually).
- Lint: pass — `./gradlew lint` (whole project) clean; `app`'s baseline unchanged (still "1 error, 34 warnings, 3 hints filtered").
- Integration: `./gradlew :app:assembleDebug` green.
- No device install, per instruction — device verification is phase 04's.

### Issues Encountered

- **A real regression, caught before reporting done, not left in**: `TvAppFixture` (`ui-tv`'s full-app Robolectric test fixture) hand-registers every `hiltViewModel()`-resolved class into its own `ViewModelStore` — a new call site with no matching entry falls through to `ViewModelProvider`'s default reflective factory, which throws `NoSuchMethodException` on a class with no no-arg constructor. Adding `hiltViewModel<TitlePreloadViewModel>()` to `TvTitleFrame` broke three existing tests this way (`TvLibraryTest` ×2, `TvSearchAndGenreTest` ×1) the moment they opened any film's title page. Fixed by registering a `relaxed = true` mockk `TitlePreloadViewModel` in `TvAppFixture`'s map, the same pattern `BrowseViewModel`/`AppearanceViewModel` already use there (with a comment naming why, matching the file's own existing comments for each entry). Verified by re-running all three by name before and after.
- **File-size guideline**: `TitleDetailScreen.kt` (244) and `TvTitlePage.kt` (214) are modestly over the ~200-line guideline; both were already large, cohesive single-screen files before this phase (228 and 197 lines) and splitting either further for a ~15-30 line overrun would fragment one screen's composition across files for little real separation — the same call phase 01/02's reports made for their own modest overruns. `LibraryFlowBranches.kt` was already at 329 lines before this phase touched it (pre-existing, out of scope to fix here); its own net addition was pulled into `TitlePreloadWiring.kt` so this phase's contribution to it stays small (+14 lines) rather than adding to the pre-existing overrun.
- **`MenuScreen.Storage`, `TvMenuBranches.kt`, `TvAppFixture.kt`**: not named in the phase's literal "Related code files" list, all three flagged above with the reasoning; no other phase in this plan owns any of them (phase 01 = the cache server crate; phase 04 = tests/docs/manifests, and this worktree runs one phase at a time per `plan.md`).

### What needs a device

Everything in this phase is compiled and unit/Robolectric-tested only. Phase 04 owns:
- The success criterion's own walk: tap Preload on a film on the tablet, leave and return, watch the bar climb, start Play elsewhere and see "Paused while playing", finish and see "Preloaded ✓" with a Telegram-free playback, and the server line reaching the film's full size on the TV box.
- Real focus/scroll behaviour on the tablet at its actual density (the `w1164dp-h777dp` Robolectric test proves layout, not a real touch/D-pad walk).
- The Settings › Storage deep link actually landing where expected from a live NeedsSpace film, on both surfaces.

### Unresolved Questions
- None blocking. Worth a second look in review: the `Paused(TimeLimit)` tap-to-resume choice (decision 1) and its wording (decision 2) — the phase text names the state but not its interaction, so this was decided rather than found.

**Status:** DONE
**Summary:** Preload lands on both film pages — a Hilt ViewModel over the phase-02 engine and phase-01's server route, a phone/tablet pill+bar+server line reusing `LinePill`/`QuietPill`, a TV plate pair in the Play row with real-key-event tests, a new `MenuScreen.Storage` deep link for "Raise the cache budget", and a real `TvAppFixture` regression (three tests) fixed rather than left broken; whole-project `testDebugUnitTest lint :app:assembleDebug` green, version bumped to 0.71.0, changelog and DESIGN.md updated, nothing committed.
**Concerns/Blockers:** None blocking — two judgment calls (the `Paused(TimeLimit)` tap behaviour/label, and the `MenuScreen.Storage`/`TvMenuBranches.kt`/`TvAppFixture.kt` files touched outside the phase's literal file list) flagged above for the reviewer to confirm or override.

---

## Addendum: fixes from code-reviewer's review

Report reviewed: `code-reviewer-260928-0027-film-page-preload-ui-review-report.md`. Every
High and Medium finding fixed, plus every Low the coordinator named explicitly. All three
High findings were real device-breaking bugs the build had passed through; none were
disputed.

### H1 — server line polled per progress tick, not every 5s

`rememberFilmPreloadUi`/`rememberTvFilmPreloadUi` (`TitlePreloadWiring.kt`,
`TvLibraryCatalogFrames.kt`) called `viewModel.stateOf(...)`/`.serverLine(...)` directly
inside the composable body — both plain cold `Flow`s, a fresh instance on every call, and
`collectAsStateWithLifecycle` restarts its collection whenever the `Flow` instance it was
given changes identity. Every recomposition (every ~250ms progress tick under
`ProgressThrottle`) rebuilt both flows and restarted the 5s wait from zero, firing an
immediate re-poll each time. Fixed by keying both behind `remember(viewModel, set.setId,
set.totalBytes) { ... }`, so the same `Flow` instance survives recomposition and only a
real setId/totalBytes change (a different film) rebuilds it. Verified by
`FilmPreloadFlowTest.openingTheFilmPagePollsTheServerOnceThenOnceMoreOnEveryStateChangeNotEveryProgressTick`
— ten `Running` progress ticks at unchanged time now cost exactly one poll (not ten),
kept as a real regression test over the full `LibraryFlowFixture` wiring rather than a
scratch probe.

### H2 — the phone's ⋯ menu measured 0dp wide on real phone widths

Robolectric's default LEGACY graphics mode measures text at near-zero width, which is why
this passed CI: `TitlePills`' plain `Row` had no wrap, and a real phone at 360-411dp
squeezed My List and ⋯ to 0dp the moment a Preload pill joined the row — Remove became
unreachable for a `Done` film. Fixed by switching to `FlowRow` (the same wrap `GenreLinks`/
`ShelfPager` already use), so the row wraps to a second line instead. New
`TitlePillsWidthTest.kt` runs at `w360dp-h800dp` under `@GraphicsMode(NATIVE)` (real text
measurement) and asserts ⋯'s bounds are non-zero for Idle/Running/Done, plus that My List
stays single-line rather than wrapping its own text.

### H3 — NeedsSpace was a dead end after the budget was raised

`FilmPreloader` never re-judges a `NeedsSpace` override on its own — only a fresh
`enqueue` does — but the pill mapped NeedsSpace to `NONE` (disabled), so a viewer who
raised the budget in Settings had no way back to a working preload short of restarting
the app. Fixed: `preloadTapAction` now maps NeedsSpace to `ENQUEUE`; label became
"Needs 5.8 GB · Try again" (visible retry affordance, Line style); the "Raise the cache
budget" link stays beside it. Verified by
`TitlePreloadViewModelTest.needsSpaceTogglesEnqueueSoRaisingTheBudgetIsNotADeadEnd`,
`TitlePreloadTest.needsSpaceOffersTheStorageLinkAndItsOwnPillRetries` (phone) and
`TvTitlePreloadStateTest.needsSpaceOffersTheStorageRowAndItsOwnPlateRetries` (TV).

### M1 (unit dropped for the first GB of every film)

`preloadBarLabel`/`preloadServerLine` stripped the held figure's own unit whenever it
differed from the total's, giving "500 of 5.8 GB" for 500 MB — silently claiming 500
*gigabytes*. Fixed: `heldSizeLabel` only drops the held unit when it equals the total's
(the common case, "2.0 of 5.0 GB"); a held figure still in MB against a GB total keeps its
own unit ("500 MB of 5.8 GB"). Covered by new cases in `TitlePreloadViewModelTest`.

### M2 — Paused(TimeLimit)'s accessibility text said "Tap to cancel." for a tap that resumes

`preloadAccessibilityHint` was routed through `preloadTapAction`'s own `CANCEL`/`ENQUEUE`
split, which put the one `TimeLimit`-is-`ENQUEUE` case inside the `CANCEL` branch's hint by
construction — dead code, and wrong. Rewritten to key directly on `state`, and the phone's
`PreloadProgressBar` now builds its contentDescription from `preloadAccessibilityHint`
rather than a hard-coded "Tap to cancel.".

### M3 — TV: OK on Remove sent focus to the Overview tab

Removing the plate that held focus left Compose's own focus search to pick whatever was
nearest — the Overview tab at the top of the page. Fixed: `TvTitlePage` now keeps a
`FocusRequester` for the main Preload plate and calls `requestFocus()` on it before
`onRemove()` fires, landing focus back on "Preloaded ✓" (soon to read something else once
the real state updates). Verified in `TvTitlePreloadStateTest`.

### M4 — no fixture coverage; the phone fixture would have crashed on the first real film

`LibraryFlowFixture` held only episodes and had no `TitlePreloadViewModel` entry — the
first test to open a film would have hit the exact `NoSuchMethodException` `TvAppFixture`
needed fixing for in the first pass. Added a film to `LibraryFlowFixture`'s own `sets`,
registered a real `TitlePreloadViewModel` over new local fakes
(`FakeFilmPreloading`/`FakeLanServerSource`/`FakeLanChunkProtocol`, duplicated per module
the same way `TvAppFixture`'s own ViewModel fixtures already are — no test source set can
reach another module's), and replaced `TvAppFixture`'s relaxed mock with the same real
wiring so its own NeedsSpace→Storage route is actually exercised rather than stuck at
`Idle` forever. Two new end-to-end tests: `FilmPreloadFlowTest` (phone) and
`TvFilmPreloadFlowTest` (TV), both walking the real `LibraryFlow`/`TvLibrary` navigation
rather than building `TitleDetailScreen`/`TvTitlePage` directly.

### M5 — TV had no bar at all

Confirmed with the coordinator: add one. New `TvPreloadBar` (`TvTitlePreload.kt`) draws
the same thin rectangle-on-rectangle bar `TvSeekBar` uses for its own track, at
`ProgressBarRangeInfo` semantics, but plain — no focus, no thumb, no draggability, since
cancelling/resuming is the plate's own OK, not the bar's (a real `TvSeekBar` answers
Left/Right for an actual seek; a preload bar has nothing for a direction key to do).
Verified by `TvTitlePreloadStateTest.runningShowsTheBarAtItsOwnFraction` via
`assertRangeInfoEquals`.

### Lows

- **L1 (Failed):** label is now `"<reason, ellipsized to 27 chars>… · Retry"` — a fixed,
  bounded width regardless of the core's own sentence length; the full untruncated reason
  reaches a screen reader through `preloadAccessibilityHint`.
- **L2 (one-frame stale flash):** `rememberFilmPreloadUi`/TV twin now seed
  `collectAsStateWithLifecycle(initialValue = null)` and return `null` (render nothing)
  until the engine's first real emission arrives, rather than flashing a fabricated
  `Idle(0, total)` on every page open/rotation.
- **L3:** `< 1%` now shown instead of `0%` for any real but sub-1% held amount; an
  `Idle` the cache already holds in full now reads and behaves exactly like `Done`
  (`normalized()` in `FilmPreloadLabels.kt`, the one place this is decided); a film with
  `totalBytes <= 0` hides the control entirely at the wiring layer rather than showing
  "Preload · 0 B" for a tap that does nothing.
- **L4:** the two "the plan's own wording" comments in `TitlePreload.kt` now state the
  rule itself ("a pause does not reset held or total bytes, so the bar stays up...").
- **L5:** `preloadIsAffirmative` no longer derives from `preloadTapAction` — it is its own
  explicit `when`, so a background-limit pause (which enqueues on a tap) still reads Quiet
  rather than Line, matching DESIGN.md's own reading of a pause as "unclogging on its
  own," not a fresh affirmative choice. DESIGN.md's Pill section and the changelog both
  corrected to describe this precisely, and to say a bar now draws on both surfaces.

### Files touched by this pass (beyond the original phase list)

New: `ui-mobile/src/test/kotlin/ui/{TitlePreloadFixtures.kt,FilmPreloadFlowTest.kt,
catalog/TitlePillsWidthTest.kt}`, `ui-tv/src/test/kotlin/ui/tv/{TitlePreloadFixtures.kt,
TvFilmPreloadFlowTest.kt}`. Modified: `FilmPreloadLabels.kt`, `TitlePreloadWiring.kt`,
`TvLibraryCatalogFrames.kt` (H1/L2/L3 fixes), `TitlePills.kt` (H2), `TitleDetailScreen.kt`,
`TvTitlePage.kt` (M3), `TvTitlePreload.kt` (M5), `LibraryFlowFixture.kt`/`TvAppFixture.kt`
(M4), `TitlePreloadTest.kt`/`TvTitlePreloadStateTest.kt`/`TitlePreloadViewModelTest.kt`
(label wording updated for H3/M1/L1), `DESIGN.md`, `docs/project-changelog.md`.

### Tests status (this pass)

`./gradlew testDebugUnitTest lint :app:assembleDebug --rerun-tasks` (whole project, every
task re-executed, none `UP-TO-DATE`) green — 699/699 tasks. Individually re-verified before
and after each fix: `feature:player:testDebugUnitTest` (labels/ViewModel), `ui-mobile:
testDebugUnitTest` (phone, including the new `FilmPreloadFlowTest`/`TitlePillsWidthTest`
and the pre-existing `LibraryFlowTest` unaffected by the fixture's new film),
`ui-tv:testDebugUnitTest` (TV, including `TvFilmPreloadFlowTest` and the three previously-
broken `TvLibraryTest`/`TvSearchAndGenreTest` cases now passing). No device install, per
instruction.

### Unresolved / flagged, not changed further

- The "Nits" section (merge `TitlePreloadUi`/`TvTitlePreloadUi` into one shared type;
  have `fetchServerLine` reuse `LanCacheRuntime.server()`) — not in the coordinator's
  explicit fix list, left as the reviewer's own lower-priority suggestion rather than
  expanded scope.
- File sizes (`TitleDetailScreen.kt` 244, `TvTitlePage.kt` 214, `LibraryFlowBranches.kt`
  343) — reviewer called the original reasoning acceptable; unchanged by this pass beyond
  the small additions each fix needed.
