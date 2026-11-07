# TV countdown leader — report (2026-10-10 17:00)

Branch `feat/tv-parity`, 0.120.4 → 0.120.5. The TV's waits now show the film
leader, the 3·2·1 countdown from the web player and the tablet. The user
chose it for every surface.

## Shared files (copied verbatim from feat/motion @ 1cbf6bbe)

- `android/core/designsystem/src/main/kotlin/CountdownLeader.kt`
- `android/core/designsystem/src/main/kotlin/Motion.kt`
- `android/core/designsystem/src/test/kotlin/CountdownLeaderTest.kt`
- `android/core/designsystem/build.gradle.kts`: also copied whole, because
  the test needs feat/motion's test dependencies (activity-compose,
  ui-test-junit4, ui-test-manifest, `isIncludeAndroidResources`). This
  build file's only difference between the branches was that block. Its
  comment mentions "motion modifiers", which only exist on feat/motion; the
  wording becomes accurate once the branches merge.

None of the four was edited. `git diff feat/motion --` on all four is empty.

## Reduced motion at the TV root

`ui/tv/TvTheme.kt`: `TvTheme`'s `CompositionLocalProvider` now also provides
`LocalReducedMotion provides rememberReducedMotion()`. This mirrors how
feat/motion's `MediagramTheme` (designsystem `Theme.kt:65`) does it. The
phone path is untouched on this branch.

## Spots changed

| Spot | Before | Now |
|---|---|---|
| `catalog/TvCatalogBody.kt` `CatalogUiState.Loading` (the cold start the user saw) | centred words | `TvLibraryLoading()` |
| `setup/TvSetupStep.kt` `SetupUiState.Checking` | spinner, no words | `TvLibraryLoading()` |
| `profile/TvProfilePicker.kt` `ProfileUiState.Loading` | spinner, no words | `TvLibraryLoading()` |
| `catalog/TvGenre.kt` not Ready | focusable words | leader above the same focusable words |
| `catalog/TvSearchResults.kt` `LOADING` | quiet line under the field | leader above the quiet line, under the field |
| `TvLibraryFrames.kt` `TvResolvedBranch` `FrameResolution.Loading` | spinner | `TvCentredLoading("Loading your library…")` |
| `catalog/TvPersonPage.kt` loading | spinner | `TvCentredLoading("Loading your library…")` |
| `setup/TvLibraryChoiceScreen.kt` `LibraryPrompt.Waiting` | spinner | `TvCentredLoading("Reading the account's channels…")` |
| `player/TvPlayerFailure.kt` `TvPlayerStatus` `Preparing` | spinner | `TvPreparingLeader()` |

`TvLoadingIndicator` (the spinner) had no callers left, so I deleted it.
I also removed the reference to it in the `TvPlateMarks.kt` comment.

Three things stayed as they were:

- Settings quiet lines are still words, as on the web: "Reading the
  cache…", "Reading the account's sessions…", "Loading profiles…" and
  "Reading system information…".
- `TvStatsPage` `Loading -> Unit` still draws nothing, which is also what
  the tablet does.
- The generated baseline profiles still name `TvLoadingIndicator`. Those
  entries are now unused, which does no harm, and they go away the next
  time the profiles are generated.

## Placement

The new composables are in `ui-tv/src/main/kotlin/ui/tv/TvLoadingLeader.kt`.
Size is `96.tvDp` (72dp) and the gap is `24.tvDp` (18dp), from
`index.html:101` and `shell.css:175`.

- **Cold start (`TvLibraryLoading`).** The leader sits above "Loading your
  library…", left-aligned with the words, with the gap between them. The
  TV draws no cover-shaped block, so the pair sits where the TV's wait
  already sat: centred in the overscan-safe area (under the bar, for the
  catalogue). The leader uses its own inks for a dark ground, as the
  tablet's `LibraryLoading` does, because the TV is always dark. The words
  keep the TV's existing style (`bodyLarge`, `onSurfaceVariant`).
- **In-page waits (`TvPageLeader` / `TvCentredLoading`).** The leader is
  above the words in the page's inks (`ink = onSurfaceVariant`,
  `numeralInk = onSurface`).
  - Frames, the person page and the library choice centre the pair, like
    the tablet's `CenteredLoading`.
  - Genre centres the leader above the focusable paragraph. The paragraph
    still takes the remote.
  - Search puts the pair under the field, start-aligned, where the line
    already stood.
- **Player.** The leader is centred with no words, its Box carries
  `semantics { contentDescription = "Loading" }`, and the accent is
  `Accent.CORAL.dark` (`--stage-accent`).
- **Accent.** Every TV leader passes tv-material's `colorScheme.primary`
  explicitly. `CountdownLeader`'s default accent reads M3's `MaterialTheme`,
  which the TV never provides, so left to the default it would sweep in
  M3's baseline violet.

The web line numbers in the comments come from feat/motion's web files
(`index.html:119-121`, `:147`, `shell.css:175`, `film-leader.css`), as the
shared `CountdownLeader.kt` and the tablet cite them. The web on this
branch has no leader yet, so those line numbers only match after the merge.

## Shared start

**Not wired.** feat/motion's committed `CountdownLeader.kt` (1cbf6bbe) has
no shared start. The uncommitted working tree at
`/home/andre/Workspace/mediagram-motion` does have one in progress:
`class CountdownStart` and
`LocalCountdownStart = staticCompositionLocalOf<CountdownStart?> { null }`,
with uncommitted changes to `MobileApp.kt` too. I did not copy uncommitted
work. When it lands, the controller should re-sync `CountdownLeader.kt` and
provide `LocalCountdownStart` in `TvApp`/`TvTheme`, as `MobileApp` does.

Until then, a cold start whose setup check, profile read and catalogue load
run back to back starts the countdown again at 3 on each of them. The
words also move a little between the full-screen waits and the catalogue's
wait under the bar.

## Tests

New: `ui-tv/src/test/kotlin/ui/tv/TvLoadingLeaderTest.kt` (8 tests).

- Cold-start geometry: the leader is 72dp wide, its left edge lines up with
  the words', and the gap is 18dp.
- Profile read: the leader and "Loading your library…".
- In-page geometry: the leader is centred over the words with an 18dp gap
  (person page).
- Leader plus words at each in-page wait: frames, genre, search, and the
  library choice with "Reading the account's channels…".
- Player: the leader is 72dp, there is a node with contentDescription
  "Loading", and no "Loading" text is drawn.

Added to existing tests:

- `TvSetupStepTest.checkingCountsDownAboveTheLibrarysWords`
- `TvCatalogScreenStateTest.theLibraryLoadingCountsDownAboveItsWords`
- `TvThemeTest.reducedMotionFollowsTheSystemsAnimatorScale`, which checks
  that the theme reads false, then true once `ANIMATOR_DURATION_SCALE` is 0.

Results, from the last full run, which exited 0:

- `:core:designsystem:testDebugUnitTest`: 31/31 (9 suites), including
  `CountdownLeaderTest` 4/4.
- `:ui-tv:testDebugUnitTest`: 634/634 (99 suites), 0 failures and 0
  skipped. That is 623 existing tests plus the 11 new ones.
- `:ui-tv:lintDebug`: clean. The first run flagged `RememberInComposition`
  in the new player test, because it created the `FocusRequester` inside
  composition; it is now created outside.
- `:ui-mobile:compileDebugKotlin`: OK.

## Commits

- `9b5e2d62 feat(tv): loading shows the web's film leader counting down`
- `9cc6b1bb chore: 0.120.5 — the TV's loading is the film leader`
  (Cargo.toml, Cargo.lock, web/package.json, android/app/build.gradle.kts)

No release was built and no device was touched.
