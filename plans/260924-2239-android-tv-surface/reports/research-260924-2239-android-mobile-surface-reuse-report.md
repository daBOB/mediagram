# Android phone surface — what TV can reuse

Paths relative to `android/`. Verdict: every ViewModel/UiState is reusable as-is; the glue (navigation, lifecycle, ≈30 pure formatters) is `internal` to `ui-mobile`.

## Modules
- `settings.gradle.kts`: `:app`, `:ui-mobile`, `:ui-tv`, `:feature:{catalog,player,setup,system}`, `:core:{designsystem,data,rust,model,playback}`. Rule: `feature/*` = ViewModels + UiState, no composables, no feature→feature deps.
- `ui-mobile/build.gradle.kts`: plugins `app.android.library`, `.library.compose`, `.mobile.screen` (convention `build-logic/convention/src/main/kotlin/AndroidMobileScreenConventionPlugin.kt` adds coil-compose, lifecycle-runtime-compose, hilt-lifecycle-viewmodel-compose, material3-adaptive). Deps: all features, `core:designsystem/model/data/playback`, activity-compose, material-icons-core. Tests: robolectric, mockk, ui-test-junit4.
- `ui-tv/build.gradle.kts`: library + compose; deps `feature:catalog`, `feature:player`, `core:designsystem`, `core:model` only. **Missing:** `feature:setup`, `feature:system`, `core:playback`, hilt-compose, lifecycle-compose, coil, activity-compose, test deps.
- `gradle/libs.versions.toml`: compose-bom 2026.06.01, material3 1.4.0, media3 1.10.1 (session declared, unused), navigation3 1.1.4 declared unused, Hilt 2.60.1. **No `androidx.tv`.** Current stable `androidx.tv:tv-material` = **1.1.0** (Google Maven, checked 2026-09-24).

## Phone screens (hand-rolled nav, no nav library)
- `ui-mobile/src/main/kotlin/ui/MobileApp.kt`: `MediagramTheme`; `SetupViewModel` `recheck()` on resume; `Ready` → library, else `SetupStep`: Checking / `TelegramApplicationScreen` / `SignIn` (`LoginViewModel`, `LoginScreen`, pure `promptFor`) / `LibraryScreen` (`libraryPromptFor`) / Failed. `WithStartOver` + `StartOverAction.kt`.
- `ui/profile/ProfileGate.kt` → `ProfilePickerScreen.kt` (tiles, add, name dialog, Stay, Retry).
- `ui/LibraryFlow.kt` `Library()`: `CatalogViewModel` + `FetchViewModel`; positions in `ui/LibraryPositions.kt` (6 `rememberSaveable`: `setId`, `titleId`, `collection`, `season`, `listId`, `menuScreen` {System, TmdbKey, Settings}). Priority: player → menu screen → title → season → collection → list → catalog. `SettingsOutcomes`; `FetchResultDialog`. Chrome `ui/AppChrome.kt` (`Destination`, `barTitleFor`, `backLabelFor`), `ui/OverflowMenu.kt` (`MenuActions`: System, Settings, Update library, TMDB key…, Start over).
- `ui/catalog/`: `CatalogScreen` (tabs Home + shelves + Continue/Watchlist/Collections/Kids; `posterColumnsFor(WindowSizeClass)`), `ShelfTabs`, `HomeScreen` (`homeRowsOf`), `ShelfWall`, `KeptWall`, `KidsWall`, `ListsScreen`, `ListScreen`, `PosterCard`/`PosterArt` (coil, initials, progress), `SetPlate`, `CollectionScreen` (tree; document rows disabled), `SeasonWall`, `TitleDetailScreen` (`rememberTitleInfo`), `UpdateLibrary`, `FetchReportSentence`.
- `ui/player/`: `PlayerScreen`, `PlayerControls`, `PlayerMarks`, `AddToListDialog`, `PlaybackStatsOverlay`, `PlaybackStatRows`, `ControlsVisibility`, `PlayerClock`, `PlayerScreenParts`, `PlayerControlParts`.
- `ui/settings/SettingsScreen.kt`, `CacheBudgetBlock.kt`, `TmdbKeyScreen.kt`; `ui/system/SystemScreen.kt`, `SystemRows.kt`.
- **No search.**

## Feature modules (all Compose-free → reusable)
- catalog: `CatalogViewModel` (`CatalogUiState` Loading/Ready/Empty/KidsEmpty/Failed; `reload`, `update`, `titleInfo`, `posterPath`, list ops), `ProfileViewModel` (Loading/Picking/Chosen). Pure: `Shelf`, `Entry`, `homeRowsOf`, `continueWall`/`watchlistWall`/`kidsShelf`, `underwayOf`/`nextAfter`/`playOrder`, `seasonPlatesOf`, `resumeLine`, `episodeLabel`.
- player: `PlayerViewModel` (`PlayerUiState`, `player: StateFlow<Player?>`, `marks`, `actionNotice`, `totals`; `open`, `stop`, `save`, toggles). Transport state from media3 holders, not VM.
- setup: `SetupViewModel`, `LoginViewModel`, `SettingsViewModel` (+`completions`). `SetupInput.kt` validation `internal`.
- system: `SystemViewModel`, `FetchViewModel`, `CacheBudgetViewModel`.

## Trapped in ui-mobile (TV needs)
- Nav/state: `LibraryPositions`, `MenuScreen`, branch priority + back order, `Destination`/`barTitleFor`/`backLabelFor`, tab ordering, SignIn completion wiring, `SettingsOutcomes`.
- Formatters: `technicalLine`, `hdrLabel`, `bitrateLabel`, `factsLine`, `humanDuration`, `ratingLabel`, `updateDisabledReason`, `isReadingChannel`, `fetchResultMessage`, `fetchSentence`, `extentOf`, `keyOf`, `rowsOf`, `initialsOf`, `watchedFractionOf`, `kidsLabel`, `clockTime(ms)`, stat lines, `humanSize`, `heldOfBudget`, system rows, `promptFor`, `libraryPromptFor`.
- Player: `controlsMayShow`, `controlsShouldFade`, `CONTROLS_LINGER_MS`, `TICK_MS`; lifecycle (`open` in `LaunchedEffect`, `stop()` on dispose unless `isChangingConfigurations`, `save()` on `ON_STOP`, `KeepScreenOnWhile`).
- Composable helpers: `rememberTitleInfo`, `rememberPosterPath`.

## Design system
- `core/designsystem`: `MediagramTheme` always dark, M3 `darkColorScheme` from `Palette.kt` (Ground `#16130F`, Page `#1E1B16`, Text `#E8E2D4`, Imprint `#D26A55`…). `Type.kt` Fraunces/Newsreader, phone sizes (title 23sp, body 15sp). `Spacing.kt` 4–32dp. **No overscan tokens, no shared components.** `DESIGN.md` ~262–275.

## Player hosting
- `feature/player/.../di/PlaybackModule.kt`: `@Singleton Deferred<ExoPlayer>` via `core/playback/.../PlayerFactory.kt`. Compose only: `PlayerSurface`, `rememberPresentationState`, media3 button/progress state holders, M3 `Slider`.
- Touch-only: tap toggles controls (only way back), drag scrub, glyph buttons, snackbar. **No key handling, no focus, no MediaSession.**

## TV detection
- `app/.../SurfaceSelection.kt` `isTelevision()` via `UiModeManager`. `MainActivity.kt` TV branch = `MaterialTheme { Surface { TvPlaceholder() } }` (not even `MediagramTheme`). Manifest: leanback/touchscreen `required=false`, banner, LEANBACK_LAUNCHER.

## Tests
- ui-mobile: all `src/test`, Robolectric `@Config(sdk=[35])` Compose tests via test activities + `LocalViewModelStoreOwner` fixtures (no Hilt testing); many pure-function tests. No androidTest. `SystemViewModelTest` oddly in ui-mobile.
- feature/*: JVM tests with own `FakeCore`, `MainDispatcherRule`.

## Reuse risks
1. Internal pure logic → copy vs move. 2. Hand-rolled nav duplicated. 3. Touch-only player. 4. Menus only in phone overflow. 5. Text entry by remote. 6. Phone-scale theme, no overscan; tv-material has own `MaterialTheme`. 7. `WindowSizeClass`/tab row assume touch. 8. ui-tv deps incomplete. 9. Many AlertDialogs — focus/back on TV. 10. `enableEdgeToEdge` runs on TV; only one player screen at a time.
