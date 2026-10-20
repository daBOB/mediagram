# Preload an episode — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Request (2026-10-20):** "we should also have a preload in shows for an episode" and "then give me an indicator how much preloaded an episode is."

**Today (Android only; the web player has no preload by an earlier decision, `TitlePreloadViewModel.kt`'s doc):**
- **Films** have a Preload control on their title page: `ui-common/.../catalog/FilmPreloadUi.kt` `rememberFilmPreloadUi(set, catalogState, onOpenStorage)`. It is offered only for `Kind.MOVIE` (`ui-mobile/.../LibraryTitleBranches.kt:67`) and driven by `feature/player/.../TitlePreloadViewModel.kt`, `FilmPreloadLabels.kt` and `core/playback/.../FilmPreloader.kt` (whole-title write into the device cache, a queue, a budget, retries).
- **The queue** shows in Storage → Preloads (`ui-mobile/.../catalog/PreloadsScreen.kt`, `FilmPreloadRow`; TV `TvPreloadsFrame`).
- **Series** preload their next episodes automatically while one plays (`core/playback/.../SeriesPreloader.kt`).
- **Show pages** list episodes (phone/tablet `ui-mobile/.../catalog/SeriesPage.kt` `seriesEpisodes(...)` with `heldIds`; TV `ui-tv/.../catalog/TvSeriesPage.kt`). A fully held episode already gets a badge from `heldIds`, but nothing shows a partly held one, and no row offers a preload.

## Decisions (controller's; the user asked for the feature and the indicator)

1. **Every episode row gets the same Preload control a film page has**, through the same engine, queue, budget and words (`FilmPreloader`, `TitlePreloadViewModel`, `FilmPreloadLabels`). Nothing new is invented for episodes; the film control's tap rules (`preloadTapAction`) apply: preload, cancel while queued or running, and remove once done. The row form is compact (a trailing control), since a show can have hundreds of rows.
2. **Every episode row shows how much of it is on this device**: held bytes over the episode's size, counted from the cache (`HeldSets`), whatever put it there — a manual preload, the series preloader, or watching. A small percentage beside the control (e.g. "42%"), with live progress while it preloads, and the existing held badge words at 100%. Nothing is shown at 0%. It is kept visually apart from the watched-progress line, so the two never read as one.
3. **The Preloads screen names an episode the way Continue does** ("Show S1E4"), on phone, tablet and TV.
4. **Kids:** the same filtering as films (`catalogState.resolvableQueueRows`): a kid never sees a title its profile hides in "Queued · after …".
5. **Android only**, phone, tablet and TV. The web player still has no preload.

## Global Constraints
- Kotlin files over 200 lines must not grow; new files under 200 lines. Reuse words from `FilmPreloadLabels.kt` and the held badge; retype nothing.
- Episode rows are lazy and can number in the hundreds: per-row state must be collected only for rows on screen, and keyed (no re-collection on every recomposition; see `FilmPreloadUi.kt`'s doc on keying).
- TV focus traps (memory `tv-compose-focus-gotchas`): keyed rows, stable modifier chains, no restorers spanning regions.
- Commits conventional, no AI references, no plan labels in code, test names or messages. Never `git stash`.
- Devices pinned by serial (Pad 5 Pro `d54bc94f`, tablet `caad49da`, box `192.168.0.56:5555`); test profiles only; never change device settings.
- Version: one patch bump at the merge.
- Branch `feat/episode-preload`, worktree `../mediagram-episode-preload`, from `main` 055d616e.

## Tasks

### Task 1: Phone and tablet — preload an episode, and see how much is held
**Files:** `ui-mobile/.../catalog/SeriesPage.kt` (only the call; it is over 200 lines) and a new episode-row preload file beside it; `ui-common/.../catalog/FilmPreloadUi.kt` (reuse for any set, or a thin episode variant); `feature/player/.../TitlePreloadViewModel.kt` only if a held-percentage flow per set is missing; `PreloadsScreen.kt` / `FilmPreloadRow` naming for episodes.
**Tests (Robolectric Compose + view model):** `anEpisodeRowOffersPreload`; `pressingPreloadQueuesTheEpisode` (same queue as films); `aPartlyHeldEpisodeShowsItsPercentage`; `aFullyHeldEpisodeShowsTheHeldBadge`; `nothingShowsForAnEpisodeWithNothingHeld`; `thePreloadsScreenNamesAnEpisodeLikeContinue`.
**Commit** `feat(android): preload an episode from a show page, and see how much of it is held`.

### Task 2: TV
**Files:** `ui-tv/.../catalog/TvSeriesPage.kt` (only the call if over 200) and a new TV episode-row preload part; TV Preloads naming.
**Tests (real D-pad keys in the TV harness):** `anEpisodeRowsPreloadIsReachableWithTheRemote`; `aPartlyHeldEpisodeShowsItsPercentageOnTv`; `focusStaysOnTheRowWhenItsPreloadStateChanges`.
**Commit** `feat(android): preload an episode from a show page on the TV`.

### Task 3: Device check, docs, version, merge
On the Pad 5 Pro: preload one episode of a show, watch the percentage climb on its row and in Preloads, cancel one, and check a partly watched episode shows its held percentage. Changelog entry, version patch, merge with the user's word.
