# Phase 1: Move shared rules out of `ui-mobile`

**Context:** [plan.md](plan.md) · [Android reuse map](reports/research-260924-2239-android-mobile-surface-reuse-report.md)

## Overview

- **Priority:** High. Everything after this assumes TV can call these.
- **Status:** done — commits 4a91c94..ed7e6b2. Deviations: byte-size formatting landed in `core:model` (both player and system need it); fetch/update helpers in `feature:catalog` and `telegramRows` in `feature:setup` (the modules owning the state they read); `:ui-common` also depends on `core:data` (for `TitleInfo`) and not on `feature:system` (unused).
- **Deliverable:** every pure rule and lifecycle helper TV needs is reachable outside `ui-mobile`, exists once, and the phone behaves exactly as before.

## Key insights

- ViewModels are already surface-free. What is not: ≈30 `internal` formatters, the library-position model, and four small composables of lifecycle glue. Copying them into `ui-tv` would give two copies that drift — the exact failure the Surface Parity rule names.
- Pure functions go to the **feature module that owns the state they read**. Precedent: `feature:catalog` already exports `resumeLine`, `episodeLabel`, `homeRowsOf`. That keeps "`feature/*` has no composables" true.
- Composable glue cannot live in `feature/*` or `core/*` (core cannot see features). It gets a small `:ui-common` library: Compose runtime + lifecycle only, **no material of either kind**, so both surfaces can depend on it without dragging the other's components in.
- Pure move: package/visibility change + import edits. No logic edits. Tests move with their subject.

## Requirements

- Functional: none visible. The phone app is unchanged.
- Non-functional: `./gradlew :ui-mobile:testDebugUnitTest` and all `feature:*` tests pass; `:ui-common` has no dependency on `androidx.compose.material3` or `androidx.tv`.

## Architecture — where each thing lands

| Destination | Moves in (from `ui-mobile`, made `public`) |
|---|---|
| `feature:catalog` | `factsLine`, `humanDuration`, `ratingLabel`, `initialsOf`, `watchedFractionOf`, `extentOf`, `keyOf`, `rowsOf` (+ its `Row`), tab ordering (`HOME` + shelves + `KEPT_TITLES`, `firstKept`), the pure position model from `LibraryPositions.kt` (the six keys, their resolution against `CatalogUiState`, branch priority, back order), `MenuScreen`, `Destination`, `barTitleFor`, `backLabelFor` |
| `feature:player` | `technicalLine`, `hdrLabel`, `bitrateLabel`, `clockTime(ms)`, stat-line builders from `PlaybackStatRows.kt`, `kidsLabel`, `controlsMayShow`, `controlsShouldFade`, `CONTROLS_LINGER_MS`, `TICK_MS` |
| `feature:system` | `humanSize`, `heldOfBudget` (`ui/formatting/ByteSize.kt`), `SystemRows.kt` builders (`telegramRows`, `cacheRows`, `upstreamRows`, `refreshLine`, `uptimeLine`, `cacheReadsLine`, `telegramLine`), `updateDisabledReason`, `isReadingChannel`, `fetchResultMessage`, `fetchSentence`; `SystemViewModelTest` moves here from `ui-mobile` too |
| `feature:setup` | `promptFor`, `libraryPromptFor`; `SetupInput.kt` validation made public |
| `:ui-common` (new) | `rememberLibraryPositions` (the `rememberSaveable` wrapper over the pure model), `rememberTitleInfo`, `rememberPosterPath`, `PlayerLifecycle` (open in `LaunchedEffect`, `stop()` on dispose unless `isChangingConfigurations`, `save()` on `ON_STOP`), `KeepScreenOnWhile`, `SettingsOutcomes`, the sign-in completion wiring from `MobileApp.kt` |

If a listed item turns out to depend on a material type (e.g. a `Color` from M3), split: the pure part moves, the styled part stays.

## Related code files

- Create: `android/ui-common/build.gradle.kts`, `android/ui-common/src/main/kotlin/…`, `android/ui-common/src/test/kotlin/…`
- Modify: `android/settings.gradle.kts`, `android/ui-mobile/build.gradle.kts`, `android/ui-tv/build.gradle.kts` (add `:ui-common` only), the source files above, their tests
- Delete: the moved originals in `ui-mobile` (no shims, no re-export typealiases)

## Implementation steps

### Task 1: Pure formatters into their feature modules
- [ ] **1.1** For each row of the table except `:ui-common`: `git mv` the file (or cut the functions if the file also holds composables), fix package, make public, fix imports in `ui-mobile`.
- [ ] **1.2** `git mv` the matching tests (`TitleFacts`, `TechnicalLine`, `PlayerClock`, `PlaybackStatRows`, `PlayerKidsLabel`, `ControlsVisibility`, `SystemRows`, `RefreshLine`, `SettingsRows`, `UpdateLibrary`, `FetchReportSentence`, `LibraryScreen`, `LoginScreen`, `PosterCard` pure parts). Feature modules run JVM tests; drop Robolectric from a moved test only if it no longer needs Android.
- [ ] **1.3** `cd android && ./gradlew testDebugUnitTest`. Expected: PASS, same test count ± moved.
- [ ] **1.4** Commit — `refactor(android): move surface-neutral formatting into feature modules`.

### Task 2: Library position model into `feature:catalog`
- [ ] **2.1** Split `LibraryPositions.kt`: the data (six keys), `resolve(state)`, branch priority and "what does Back clear next" become a plain class in `feature:catalog`; only the `rememberSaveable` holder stays composable.
- [ ] **2.2** Move `LibraryPositionsTest` + `AppChrome` pure tests alongside. Add one test pinning the back order: player → menu → title → season → collection → list → catalog.
- [ ] **2.3** Tests pass. Commit — `refactor(android): make the library position model shareable`.

### Task 3: `:ui-common`
- [ ] **3.1** New module with `app.android.library` + `app.android.library.compose`; deps: `feature:catalog`, `feature:player`, `feature:setup`, `feature:system`, lifecycle-runtime-compose, hilt-lifecycle-viewmodel-compose. **Not** material3, not tv.
- [ ] **3.2** Move the composables from the table. `PlayerScreen` calls `PlayerLifecycle(viewModel, setId, fsk)` instead of inlining the effects.
- [ ] **3.3** Move `PlayerLifecycleTest` (Robolectric) to `ui-common/src/test`.
- [ ] **3.4** Commit — `refactor(android): share lifecycle glue between surfaces`.

### Task 4: Prove the phone is unchanged
- [ ] **4.1** `scripts/check.sh` (clippy/cargo skip nothing; gradle test + lint must pass).
- [ ] **4.2** Confirm no ViewModel/UiState changed: `git diff --stat main -- 'android/feature/**/*ViewModel.kt' 'android/feature/**/*UiState.kt'` → empty.
- [ ] **4.3** Confirm `:ui-common` is material-free: `./gradlew :ui-common:dependencies --configuration releaseRuntimeClasspath | grep -E 'material3|androidx.tv'` → no output.

## Todo list
- [ ] Formatters moved with tests
- [ ] Position model moved, back order pinned
- [ ] `:ui-common` created, material-free
- [ ] Phone suite + lint green, no VM/UiState diff

## Success criteria
`ui-mobile` holds only composables that draw; every rule TV needs is public in exactly one place; phone tests pass unchanged in substance.

## Risk assessment
| Risk | Mitigation |
|---|---|
| A "pure" helper secretly uses M3 types | Split rule in Architecture; compile of `feature:*` (no Compose) catches it |
| Moving breaks Robolectric tests' resource access | Keep Robolectric only where Android is really touched; `isIncludeAndroidResources` in the new module if needed |
| Main moves under the branch and edits a moved file | Rebase early (before phase 2); moves conflict loudly, not silently |

## Security considerations
None — no behaviour change.

## Next steps
Phase 2.
