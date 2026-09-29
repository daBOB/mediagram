# Phase 02 — TV Home body: cover, features, bands, shelves

Worktree: `/home/andre/Workspace/mediagram/.claude/worktrees/agent-a82b6e0a6cf54effc`
Branch: `feat/tv-web-look-home`, based on `main` `03eafd86` (0.82.1)
Version: `0.83.0` (Cargo.toml, Cargo.lock's 5 workspace crates, web/package.json, android/app versionName; versionCode untouched at 18)

## Status

`in-review` — implementation, tests and `check.sh` green. On-box screenshots and R1/R2 left for the lead (no device access here).

## Files

### Created
- `android/ui-common/src/main/kotlin/ui/chrome/CoverBlend.kt` (+ test) — moved from `ui-mobile`, made public for cross-module use.
- `android/ui-common/src/main/kotlin/ui/catalog/HeroScrims.kt` — `CoverScrim(compact, paper, modifier)`, lifted from the phone's `CoverControls.kt`.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/home/` — `TvHomeTargets.kt` (`homeTargetOf` + `TvHomeSection`/`TvHomeTarget`), `TvHomeCover.kt`, `TvCoverSlide.kt`, `TvCoverActions.kt`, `TvHomeFeatures.kt`, `TvContinueBand.kt` (+ quote), `TvResumeCard.kt`, `TvRecentBand.kt` (+ This month), `TvPosterStrip.kt`, `TvCourseList.kt`, `TvBandHeading.kt` (+ `SeeAllLink`, moved from the deleted `TvHomeRow.kt`).
- `android/ui-tv/src/test/kotlin/ui/tv/catalog/home/TvHomeTargetsTest.kt`.

### Modified
- `android/feature/catalog/src/main/kotlin/{LibraryTally.kt,EditorialPicks.kt}` — `spelledCountOf` public; `FeatureKind.label` extension.
- `android/ui-mobile/src/main/kotlin/ui/catalog/home/{CoverControls,HomeFeatures,HomeType}.kt` — delegate to the two lifts above; behaviour unchanged (its own tests stay green).
- `android/ui-tv/src/main/kotlin/ui/tv/TvFocus.kt` — `FeatureCardShape` (14dp).
- `android/ui-tv/src/main/kotlin/ui/tv/TvIndexRow.kt` — label `Column` now `weight(1f)`; fixes the open rail's missing "Continue watching" count (root cause below).
- `android/ui-tv/src/main/kotlin/ui/tv/TvLibraryBranches.kt` — `onToggleWatchlist` threaded to `catalogViewModel::setWatchlisted`.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/{TvHome,TvCatalogBody,TvCatalogScreen}.kt` — Home rewritten as the magazine `LazyColumn`; `homeMagazine`/`homeRows`/`homeListState`/`blend` hoisted to `TvCatalogScreen` (one `magazineHomeOf` call, read by both the bar's blend and the body).
- `android/ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryChrome.kt` — `blend` parameter threaded to `TvDepartmentsBar`.
- Tests: `TvHomeStateTest.kt` (rewritten for the new API), `TvCatalogScreenStateTest.kt`, `TvCatalogRootPlayStateTest.kt` (assertions updated for the new labels/limits/caption semantics), androidTest `TvCatalogScreenTest.kt`/`TvLibraryRemoteTest.kt` (rewritten for the pill/rail focus model).
- `docs/system-architecture.md`, `docs/project-changelog.md` — Home's new layout, the two TV/tablet differences it introduces (no pause/zoom on the cover; Continue's offline badge).

### Deleted
- `ui-mobile/src/main/kotlin/ui/chrome/CoverBlend.kt` (+ test) — moved, not just deleted.
- `ui-tv/.../catalog/TvFeatureStrip.kt`, `ui-tv/.../catalog/TvHomeRow.kt` — superseded by the magazine layout; `TvCoverStory.kt` kept (still used by the two department pages, phase 03).

## What changed, structurally

- **Home** (`TvHome.kt`) is now a `LazyColumn` of at most six sections — cover, features, Continue+quote, Recently added+This month, Latest series, Latest courses — each drawn only if it has content, matching `home-view.js`'s own rule. The column carries a 900dp cache window (six sections, cheap to over-provision) so `Down` reaches a section not yet in the viewport without relying on virtualisation the way a plate wall needs to.
- **Arrival/restore** (`homeTargetOf`, pure): resolves a restore key to a `(section, stop)` pair, or the first section with anything in it. Cover, features, Continue's resume cards, Recently added's posters, series and courses are all restorable; the quote and This month's own rows are not (they carry no focus requester of their own) — a key naming either falls through to the graceful default, per `homeTargetOf`'s own documented "missing key" case, not a dead end.
- **The cover** (`TvHomeCover.kt`/`TvCoverSlide.kt`/`TvCoverActions.kt`): rotates every 9s while nothing has focus inside it, crossfades 1.4s keyed by `setId` (safe if the lineup shrinks), actions and dots drawn outside the crossfade so neither ever fades with a picture no longer shown. No pause button, no drift zoom — both written down in `docs/system-architecture.md` as deliberate TV differences, matching the phase's own reasoning.
- **The bar's bleed**: `homeMagazine`/`blend` are computed once in `TvCatalogScreen` (not inside `TvCatalogBody`'s Home branch) so the departments bar reads the exact same cover the body draws — `ui.chrome.coverBlend` (lifted to `ui-common`) over `homeListState.asHeroListState()`.
- **Continue/Recent/series rows are plain `Row`s in a `horizontalScroll`, not `LazyRow`s** — a deviation from my own first pass, corrected after it produced two real bugs (below). At most ~12 items in any of these rows; Compose's own scrollable-ancestor relocation brings a newly focused card into view without a manual `scrollToItem`, and a plain row can't under-compose relative to a test's own window size the way a `LazyRow`'s viewport-based windowing can.

## Root causes found and fixed during the work

1. **The open rail's missing "Continue watching" count** was truncation, not missing wiring: `TvIndexRow`'s label `Column` had no `weight(1f)`, so it measured against the row's own full width (a Compose `Row` quirk — unweighted children are offered the row's own max width, not what's left after siblings) rather than what remained after the icon — a long label ("Continue watching") could then run wide enough to push the trailing count past the row's own `.clip()` bounds, invisible rather than merely uncounted. "My List" (short) never hit this. Fixed with the same `weight(1f)` the tablet's own `RailRow` already carries on its label.
2. **A sentinel-redirect race stole focus back from Search/the bar's ⋮ after Back consumed it.** `LocalTakesArrivalFocus` reads `false` for exactly one composition while a sentinel (Search, ⋮, Latest, Genres, Settings, System) is being restored, then flips back to `true` the moment that sentinel is consumed — with the wall key Home was handed staying `null` throughout both reads. My first pass keyed Home's own arrival `LaunchedEffect` (and each delegated section's) on that live flag, so the flip alone re-ran them and pulled the remote straight back onto a poster a frame after it had reached the field or button. Fixed by keying every one of these effects on the *target*/`focusAt` value alone (which does not change across that flip) and reading `takesFocus` fresh inside the effect body instead — the same "gate the action, not the key" invariant the phase 01 chrome already relies on elsewhere. Caught by running `TvMenuTest`/`TvSearchAndGenreTest` (pre-existing, unrelated to Home on their face) — confirmed as a real regression, not a pre-existing flake, by running the identical tests against a stashed pre-phase-02 tree first.
3. **`TvCourseList` never actually requested focus at all** — it attached a `focusRequester` to the right card but had no effect to call `.requestFocus()` on it, unlike the lazy rows' own scroll-then-focus effects. Added one.
4. **A caption sitting outside its own `Card` doesn't read as focused with it** — `TvPosterStrip`'s first draft put the series poster's name/count in a sibling `Column` below the `Card`; `mergeDescendants` only folds a focusable node's own descendants into it, never a sibling's, so `onNodeWithText(showName).assertIsFocused()` found the text but not the focus. Restructured to match `TvPlate`'s own shape: poster, name and caption all inside the one `Card`.

## Deviations from the phase file

- **Only `CoverScrim` lifted to `ui-common`'s `HeroScrims.kt`, not the department hero's own `DeptArtScrim`.** This phase's own cover only needs the former; the latter is phase 03's department heroes, which can add it there when it actually needs it rather than this phase carrying an unused second function.
- **Continue/Recent/series rows are plain, always-composed `Row`s (see above), not `LazyRow`s.** The phase's own architecture note asks for the *page* to be a `LazyColumn` (done); it does not name the sub-rows, and virtualising them bought no real performance benefit at their size (≤~12 items) while costing two real bugs.
- **Feature card sizes (300dp lead / 220dp pair; 24sp/26sp titles) and the cover's own text-column cap (620dp)** are this phase's own numbers, read off the tablet's shape rather than a spec value the phase file gives — flagged for the lead's on-box comparison.
- **Continue's resume cards now carry the offline badge; the phone's `ResumeCard` never has.** Continue was a plain `TvSetPlate` row (which does show it) before this phase folded it into the magazine band; dropping the badge silently would have been a real, tested regression (`TvHousekeepingTest`) rather than a deliberate tablet-parity choice — written down in `docs/system-architecture.md` as a kept TV/tablet difference.
- **Quote and This month are not restorable targets** (see `homeTargetOf`'s call sites in `TvHome.kt`) — a documented scope cut, not a silent gap: a key naming either falls through to the default per `homeTargetOf`'s own "missing key" behaviour.

## Tests

- Type check / compile: `:ui-tv:compileDebugKotlin`, `:ui-tv:compileDebugAndroidTestKotlin` — pass.
- Unit tests: `:ui-tv:testDebugUnitTest` (368 tests, all green), `:ui-common:testDebugUnitTest`, `:feature:catalog:testDebugUnitTest`, `:ui-mobile:testDebugUnitTest` — all green (confirms the shared lifts didn't touch phone/tablet behaviour).
- `scripts/check.sh` — **all checks passed** (clippy, `cargo test --all`, `bun run lint`/`bun test`, full Gradle `testDebugUnitTest lint :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin`). Lint: 21 warnings, 1 pre-existing baselined error (unrelated to this phase's files) — no new lint failures blocking the gate.
- `cargo metadata --locked --offline` succeeds.

## Left for the lead (on-box, out of scope here — no device access)

1. Screenshots: `tv-home-{top,cover,dot-focus,features,continue,recent,series-courses}.png`, side by side with `plans/260927-2050-android-home-web-parity/reports/tablet-landscape-home-{top,mid,lower}.png` and the web stub at 960×540.
2. R1 (Home → poster → Back) and R2 on the benchmark build — this phase's Home is heavier than 0.82.1's (a cover, feature cards, several rows) on the same box the decoder-freeze report already measured teardown cost on for the *lighter* pre-chrome tree.
3. Wordmark/feature-card sizes (above) — reasonable but unverified against the box.
4. Cover rotation, crossfade and the dots' own focus-select, watched live with a remote — only proven here through Robolectric semantics, not real timing/paint.
5. `+ My List` on the cover, watched live — `catalogViewModel.setWatchlisted` is wired and unit-tested through `TvCatalogRoot`/`TvLibraryHomeFrame`, but never pressed on a real remote in this session.

## Unresolved questions

None blocking. The deviations above are each either a direct, documented consequence of a real bug found during the work (weight, race, missing effect, sibling caption) or a scope cut this phase's own success criteria already allows for (missing-key default; a lift phase 03 can add to when it needs it).
