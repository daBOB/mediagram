# Android focused commits

Status: DONE

Committed the verified authored Android delivery on `desloppify/quality-20260923`
without source edits, hook changes, version changes, push, or scanner mutations.
Final HEAD: `23ac5bf048dc7e687fbd55ac2fc5366522d26697`. The index is empty.

## Grouping

1. Build conventions, explicit JVM configuration, unused UI dependency pruning,
   the Compose naming configuration, and associated formatting.
2. Core candidate ownership/cancellation, library selection/event ownership,
   settings implementations and their tests. The setup LibrarySettings test
   implementation travels with the added selections contract.
3. Real persisted-cache budget coverage plus media reader/evictor boundary tests
   and formatting within the same playback module.
4. Coupled feature and UI changes: profile repository method names and every
   consumer, catalog update/enrichment ownership, setup login relocation,
   retained Settings completions, player failure feedback, mobile package moves,
   and real navigation/playback coverage. Separating these current file changes
   would strand renamed imports, repository implementations or UI consumers.
   Git reports 159 rename-aware files; the exact name/status list below records
   old and new paths. Nearby model/design-system/app formatting stays with its
   owning delivery. No artificial intermediate source versions were introduced.

The existing encrypted package settings/adapter were preserved, including their
formatting. The controller retained the accepted design and recorded the unused
provisioning finding as wontfix; this task did not remove that surface.

## Checks

- Read the active plan, Android implementation/review reports, repository rules,
  and git commit/safety/context instructions before staging explicit paths.
- `/tmp/android-final-quality-gate.log`: broad Android unit tests, lint, app and
  instrumentation Kotlin compilation completed successfully (504 tasks).
- Current unit-test XML: **579 tests, 0 failures, 0 errors, 0 skips** across app,
  core/data, core/designsystem, core/playback, feature/catalog/player/setup/system,
  and ui-mobile. This is inspected existing evidence; no duplicate Gradle run.
- Existing independent Settings review and provider/player/navigation/cache
  reports supply the behavior and failing-before evidence for this delivery.
- Each staged group passed `git diff --cached --check`; reviewed added-line
  credential/security matches are API identifiers, explanatory comments or
  synthetic fixtures. No real credentials, dotenv files or private state staged.
- Every committed path is authored Android source/test/build configuration.
  Generated `android/core/rust/src/main/kotlin/uniffi/` was excluded explicitly.
- Normal git commit execution succeeded. `core.hooksPath` remains `.githooks`;
  the repository has a pre-push hook and no pre-commit hook. No hook was bypassed.
- Cargo workspace, web package and Android versionName remain synchronized at
  **0.40.2**. No new version bump was needed for this same delivery.

The supplied broad gate covers the integrated working tree, including generated
bindings prepared by the controller. No per-commit Gradle snapshot build is
claimed. Group dependency checks used the actual interface/import diffs.

## Remaining scope

Only `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt`
remains dirty under Android, as requested for normal binding regeneration.
Rust, web, scripts, docs, plans and ignored state were neither staged nor committed.
The controller owns final integrated gates, binding regeneration, finding
commit-log recording and any later publication. This task started no process.

## Exact commits and paths

### ed97e7e9d50e6a8d6e6d0cd471ed615996747f46 — build(android): simplify conventions and trim unused UI dependencies

```text
A	android/.editorconfig
M	android/build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt
M	android/build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt
M	android/build-logic/convention/src/main/kotlin/AndroidLintConventionPlugin.kt
M	android/build-logic/convention/src/main/kotlin/AndroidMobileScreenConventionPlugin.kt
M	android/build-logic/convention/src/main/kotlin/AndroidTestConventionPlugin.kt
M	android/build-logic/convention/src/main/kotlin/DetektConventionPlugin.kt
M	android/build-logic/convention/src/main/kotlin/HiltConventionPlugin.kt
M	android/build-logic/convention/src/main/kotlin/JvmLibraryConventionPlugin.kt
M	android/build-logic/convention/src/main/kotlin/SpotlessConventionPlugin.kt
M	android/build-logic/convention/src/main/kotlin/config/AndroidCompose.kt
M	android/build-logic/convention/src/main/kotlin/config/AndroidInstrumentationTest.kt
M	android/build-logic/convention/src/main/kotlin/config/GradleManagedDevices.kt
M	android/build-logic/convention/src/main/kotlin/config/KotlinAndroid.kt
M	android/build-logic/convention/src/main/kotlin/config/PrintApksTask.kt
M	android/core/rust/build.gradle.kts
M	android/gradle/libs.versions.toml
```

### 6490857a3bdd423cf55ac4029fcc8ed6a6af1cae — fix(android-core): preserve session and library event ownership

```text
M	android/app/src/main/kotlin/com/mediagram/android/di/CoreModule.kt
M	android/core/data/src/androidTest/kotlin/settings/EncryptedSettingsTest.kt
M	android/core/data/src/main/kotlin/CatalogRepository.kt
M	android/core/data/src/main/kotlin/CoreClient.kt
M	android/core/data/src/main/kotlin/CoreErrors.kt
M	android/core/data/src/main/kotlin/CoreProvider.kt
M	android/core/data/src/main/kotlin/CoreStorage.kt
M	android/core/data/src/main/kotlin/DefaultCoreClient.kt
M	android/core/data/src/main/kotlin/LibraryEvents.kt
M	android/core/data/src/main/kotlin/RefreshLog.kt
M	android/core/data/src/main/kotlin/ResumePoint.kt
M	android/core/data/src/main/kotlin/di/DataModule.kt
M	android/core/data/src/main/kotlin/settings/EncryptedPreferences.kt
M	android/core/data/src/main/kotlin/settings/LibrarySettings.kt
M	android/core/data/src/main/kotlin/settings/PackageSettings.kt
M	android/core/data/src/main/kotlin/settings/TelegramSettings.kt
M	android/core/data/src/main/kotlin/settings/TmdbSettings.kt
M	android/core/data/src/test/kotlin/CatalogRepositoryTest.kt
A	android/core/data/src/test/kotlin/CoreCandidateCleanupTest.kt
M	android/core/data/src/test/kotlin/CoreErrorsTest.kt
M	android/core/data/src/test/kotlin/CoreProviderReplaceTest.kt
M	android/core/data/src/test/kotlin/CoreProviderTest.kt
M	android/core/data/src/test/kotlin/CoreStorageTest.kt
M	android/core/data/src/test/kotlin/FakeCore.kt
A	android/core/data/src/test/kotlin/LibraryEventsLifecycleTest.kt
M	android/core/data/src/test/kotlin/LibraryEventsTest.kt
M	android/core/data/src/test/kotlin/RefreshLogTest.kt
M	android/core/data/src/test/kotlin/ResumePointFixtureTest.kt
M	android/core/data/src/test/kotlin/settings/LibrarySettingsTest.kt
M	android/core/data/src/test/kotlin/settings/PackageSettingsTest.kt
M	android/core/data/src/test/kotlin/settings/TelegramSettingsTest.kt
M	android/core/data/src/test/kotlin/settings/TmdbSettingsTest.kt
M	android/core/model/src/main/kotlin/WatchSnapshot.kt
M	android/feature/setup/src/test/kotlin/RefusingLibrarySettings.kt
```

### bb1105a1d01c56cc95360d021b99451b746b8a8b — test(android-playback): cover cache budgets and media read boundaries

```text
M	android/core/playback/src/main/kotlin/AdjustableLruEvictor.kt
M	android/core/playback/src/main/kotlin/CacheBudgetSettings.kt
M	android/core/playback/src/main/kotlin/CacheProvider.kt
M	android/core/playback/src/main/kotlin/MlibDataSource.kt
M	android/core/playback/src/main/kotlin/PlaybackCounters.kt
M	android/core/playback/src/main/kotlin/PlayerFactory.kt
M	android/core/playback/src/test/kotlin/AdjustableLruEvictorTest.kt
M	android/core/playback/src/test/kotlin/CacheBudgetSettingsTest.kt
M	android/core/playback/src/test/kotlin/CacheProviderTest.kt
M	android/core/playback/src/test/kotlin/FakeCore.kt
M	android/core/playback/src/test/kotlin/MlibDataSourceTest.kt
M	android/core/playback/src/test/kotlin/PlaybackCountersTest.kt
M	android/core/playback/src/test/kotlin/PlayerFactoryTest.kt
```

### 23ac5bf048dc7e687fbd55ac2fc5366522d26697 — fix(android): retain feature state across updates and lifecycle changes

```text
M	android/app/src/main/kotlin/com/mediagram/android/MainActivity.kt
M	android/app/src/test/kotlin/com/mediagram/android/SurfaceSelectionTest.kt
A	android/core/data/src/main/kotlin/CatalogEnrichmentFetcher.kt
A	android/core/data/src/main/kotlin/LibraryUpdateCoordinator.kt
M	android/core/data/src/main/kotlin/WatchStateRepository.kt
M	android/core/data/src/main/kotlin/WatchSync.kt
M	android/core/data/src/test/kotlin/WatchStateRepositoryTest.kt
M	android/core/data/src/test/kotlin/WatchSyncTest.kt
M	android/core/designsystem/src/main/kotlin/Theme.kt
M	android/core/designsystem/src/main/kotlin/Type.kt
M	android/core/designsystem/src/test/kotlin/CatalogueColorsTest.kt
M	android/core/rust/src/androidTest/kotlin/rust/CoreLoadsTest.kt
M	android/feature/catalog/src/main/kotlin/CatalogUiState.kt
M	android/feature/catalog/src/main/kotlin/CatalogViewModel.kt
M	android/feature/catalog/src/main/kotlin/Collection.kt
M	android/feature/catalog/src/main/kotlin/HomeShelves.kt
M	android/feature/catalog/src/main/kotlin/KeptShelves.kt
M	android/feature/catalog/src/main/kotlin/NaturalOrder.kt
M	android/feature/catalog/src/main/kotlin/NextUp.kt
D	android/feature/catalog/src/main/kotlin/ProfileViewModel.kt
M	android/feature/catalog/src/main/kotlin/ResumeLine.kt
M	android/feature/catalog/src/main/kotlin/SeasonWall.kt
M	android/feature/catalog/src/main/kotlin/Shelves.kt
D	android/feature/catalog/src/main/kotlin/login/LoginViewModel.kt
A	android/feature/catalog/src/main/kotlin/profile/ProfileViewModel.kt
M	android/feature/catalog/src/test/kotlin/AgeRatingTest.kt
A	android/feature/catalog/src/test/kotlin/CatalogCoreFixture.kt
M	android/feature/catalog/src/test/kotlin/CatalogUiStateTest.kt
M	android/feature/catalog/src/test/kotlin/CatalogViewModelTest.kt
M	android/feature/catalog/src/test/kotlin/FakeCatalogRepository.kt
M	android/feature/catalog/src/test/kotlin/HomeShelvesTest.kt
M	android/feature/catalog/src/test/kotlin/KeptShelvesTest.kt
A	android/feature/catalog/src/test/kotlin/LibraryUpdateCoordinatorTest.kt
M	android/feature/catalog/src/test/kotlin/NaturalOrderTest.kt
M	android/feature/catalog/src/test/kotlin/NextUpFixtureTest.kt
M	android/feature/catalog/src/test/kotlin/PlayOrderTest.kt
D	android/feature/catalog/src/test/kotlin/ProfileViewModelTest.kt
M	android/feature/catalog/src/test/kotlin/SeasonWallTest.kt
M	android/feature/catalog/src/test/kotlin/ShelvesTest.kt
D	android/feature/catalog/src/test/kotlin/login/LoginViewModelTest.kt
A	android/feature/catalog/src/test/kotlin/profile/ProfileViewModelTest.kt
M	android/feature/player/src/main/kotlin/DefaultPlayerHandle.kt
M	android/feature/player/src/main/kotlin/PlayerHandle.kt
M	android/feature/player/src/main/kotlin/PlayerUiState.kt
M	android/feature/player/src/main/kotlin/PlayerViewModel.kt
M	android/feature/player/src/main/kotlin/ProgressRecorder.kt
M	android/feature/player/src/main/kotlin/di/PlaybackModule.kt
M	android/feature/player/src/test/kotlin/DefaultPlayerHandleTest.kt
M	android/feature/player/src/test/kotlin/FakePlayerHandle.kt
M	android/feature/player/src/test/kotlin/FakeWatchStateRepository.kt
A	android/feature/player/src/test/kotlin/PlayerActionFailureTest.kt
A	android/feature/player/src/test/kotlin/PlayerActionNoticeTest.kt
M	android/feature/player/src/test/kotlin/PlayerConstructionFailureTest.kt
M	android/feature/player/src/test/kotlin/PlayerMarksTest.kt
M	android/feature/player/src/test/kotlin/PlayerReopenTest.kt
M	android/feature/player/src/test/kotlin/PlayerViewModelTest.kt
M	android/feature/player/src/test/kotlin/ProgressRecorderTest.kt
M	android/feature/player/src/test/kotlin/TestPlayerViewModel.kt
M	android/feature/setup/src/main/kotlin/Libraries.kt
M	android/feature/setup/src/main/kotlin/LibraryOption.kt
M	android/feature/setup/src/main/kotlin/SettingsUiState.kt
M	android/feature/setup/src/main/kotlin/SettingsViewModel.kt
M	android/feature/setup/src/main/kotlin/SetupInput.kt
M	android/feature/setup/src/main/kotlin/SetupUiState.kt
M	android/feature/setup/src/main/kotlin/SetupViewModel.kt
R089	android/feature/catalog/src/main/kotlin/login/LoginUiState.kt	android/feature/setup/src/main/kotlin/login/LoginUiState.kt
A	android/feature/setup/src/main/kotlin/login/LoginViewModel.kt
M	android/feature/setup/src/test/kotlin/FakeCore.kt
M	android/feature/setup/src/test/kotlin/RefusingTelegramSettings.kt
M	android/feature/setup/src/test/kotlin/SettingsViewModelTest.kt
M	android/feature/setup/src/test/kotlin/SetupFixture.kt
M	android/feature/setup/src/test/kotlin/SetupInputTest.kt
M	android/feature/setup/src/test/kotlin/SetupRecoveryTest.kt
M	android/feature/setup/src/test/kotlin/SetupViewModelTest.kt
R072	android/feature/catalog/src/test/kotlin/login/FakeCore.kt	android/feature/setup/src/test/kotlin/login/FakeCore.kt
A	android/feature/setup/src/test/kotlin/login/LoginViewModelTest.kt
M	android/feature/system/src/main/kotlin/CacheBudgetViewModel.kt
M	android/feature/system/src/main/kotlin/FetchUiState.kt
M	android/feature/system/src/main/kotlin/FetchViewModel.kt
M	android/feature/system/src/main/kotlin/SystemViewModel.kt
M	android/feature/system/src/test/kotlin/CacheBudgetChoicesTest.kt
A	android/feature/system/src/test/kotlin/CatalogEnrichmentFetcherTest.kt
A	android/feature/system/src/test/kotlin/CatalogEnrichmentPersistenceTest.kt
M	android/feature/system/src/test/kotlin/FakeCore.kt
M	android/feature/system/src/test/kotlin/FetchViewModelTest.kt
M	android/ui-mobile/build.gradle.kts
D	android/ui-mobile/src/main/kotlin/LibraryFlow.kt
D	android/ui-mobile/src/main/kotlin/SettingsOutcomes.kt
R076	android/ui-mobile/src/main/kotlin/AppChrome.kt	android/ui-mobile/src/main/kotlin/ui/AppChrome.kt
A	android/ui-mobile/src/main/kotlin/ui/LibraryFlow.kt
R087	android/ui-mobile/src/main/kotlin/LibraryPositions.kt	android/ui-mobile/src/main/kotlin/ui/LibraryPositions.kt
R078	android/ui-mobile/src/main/kotlin/MobileApp.kt	android/ui-mobile/src/main/kotlin/ui/MobileApp.kt
R087	android/ui-mobile/src/main/kotlin/OverflowMenu.kt	android/ui-mobile/src/main/kotlin/ui/OverflowMenu.kt
R084	android/ui-mobile/src/main/kotlin/CatalogScreen.kt	android/ui-mobile/src/main/kotlin/ui/catalog/CatalogScreen.kt
R083	android/ui-mobile/src/main/kotlin/CollectionScreen.kt	android/ui-mobile/src/main/kotlin/ui/catalog/CollectionScreen.kt
R060	android/ui-mobile/src/main/kotlin/FetchReportSentence.kt	android/ui-mobile/src/main/kotlin/ui/catalog/FetchReportSentence.kt
R065	android/ui-mobile/src/main/kotlin/HomeScreen.kt	android/ui-mobile/src/main/kotlin/ui/catalog/HomeScreen.kt
R072	android/ui-mobile/src/main/kotlin/KeptWall.kt	android/ui-mobile/src/main/kotlin/ui/catalog/KeptWall.kt
R098	android/ui-mobile/src/main/kotlin/ListNameDialog.kt	android/ui-mobile/src/main/kotlin/ui/catalog/ListNameDialog.kt
R086	android/ui-mobile/src/main/kotlin/ListScreen.kt	android/ui-mobile/src/main/kotlin/ui/catalog/ListScreen.kt
R084	android/ui-mobile/src/main/kotlin/ListsScreen.kt	android/ui-mobile/src/main/kotlin/ui/catalog/ListsScreen.kt
R089	android/ui-mobile/src/main/kotlin/PosterCard.kt	android/ui-mobile/src/main/kotlin/ui/catalog/PosterCard.kt
R087	android/ui-mobile/src/main/kotlin/SeasonWall.kt	android/ui-mobile/src/main/kotlin/ui/catalog/SeasonWall.kt
R088	android/ui-mobile/src/main/kotlin/SetPlate.kt	android/ui-mobile/src/main/kotlin/ui/catalog/SetPlate.kt
R086	android/ui-mobile/src/main/kotlin/ShelfTabs.kt	android/ui-mobile/src/main/kotlin/ui/catalog/ShelfTabs.kt
R070	android/ui-mobile/src/main/kotlin/ShelfWall.kt	android/ui-mobile/src/main/kotlin/ui/catalog/ShelfWall.kt
R089	android/ui-mobile/src/main/kotlin/TechnicalLine.kt	android/ui-mobile/src/main/kotlin/ui/catalog/TechnicalLine.kt
R087	android/ui-mobile/src/main/kotlin/TitleDetailScreen.kt	android/ui-mobile/src/main/kotlin/ui/catalog/TitleDetailScreen.kt
R082	android/ui-mobile/src/main/kotlin/TitleFacts.kt	android/ui-mobile/src/main/kotlin/ui/catalog/TitleFacts.kt
R053	android/ui-mobile/src/main/kotlin/UpdateLibrary.kt	android/ui-mobile/src/main/kotlin/ui/catalog/UpdateLibrary.kt
A	android/ui-mobile/src/main/kotlin/ui/components/Block.kt
A	android/ui-mobile/src/main/kotlin/ui/formatting/ByteSize.kt
R093	android/ui-mobile/src/main/kotlin/AddToListDialog.kt	android/ui-mobile/src/main/kotlin/ui/player/AddToListDialog.kt
R080	android/ui-mobile/src/main/kotlin/ControlsVisibility.kt	android/ui-mobile/src/main/kotlin/ui/player/ControlsVisibility.kt
R085	android/ui-mobile/src/main/kotlin/PlaybackStatRows.kt	android/ui-mobile/src/main/kotlin/ui/player/PlaybackStatRows.kt
R088	android/ui-mobile/src/main/kotlin/PlaybackStatsOverlay.kt	android/ui-mobile/src/main/kotlin/ui/player/PlaybackStatsOverlay.kt
R098	android/ui-mobile/src/main/kotlin/PlayerClock.kt	android/ui-mobile/src/main/kotlin/ui/player/PlayerClock.kt
R098	android/ui-mobile/src/main/kotlin/PlayerControlParts.kt	android/ui-mobile/src/main/kotlin/ui/player/PlayerControlParts.kt
R093	android/ui-mobile/src/main/kotlin/PlayerControls.kt	android/ui-mobile/src/main/kotlin/ui/player/PlayerControls.kt
R082	android/ui-mobile/src/main/kotlin/PlayerMarks.kt	android/ui-mobile/src/main/kotlin/ui/player/PlayerMarks.kt
R071	android/ui-mobile/src/main/kotlin/PlayerScreen.kt	android/ui-mobile/src/main/kotlin/ui/player/PlayerScreen.kt
R093	android/ui-mobile/src/main/kotlin/PlayerScreenParts.kt	android/ui-mobile/src/main/kotlin/ui/player/PlayerScreenParts.kt
R088	android/ui-mobile/src/main/kotlin/ProfileGate.kt	android/ui-mobile/src/main/kotlin/ui/profile/ProfileGate.kt
R073	android/ui-mobile/src/main/kotlin/ProfilePickerScreen.kt	android/ui-mobile/src/main/kotlin/ui/profile/ProfilePickerScreen.kt
R085	android/ui-mobile/src/main/kotlin/CacheBudgetBlock.kt	android/ui-mobile/src/main/kotlin/ui/settings/CacheBudgetBlock.kt
A	android/ui-mobile/src/main/kotlin/ui/settings/SettingsOutcomes.kt
R076	android/ui-mobile/src/main/kotlin/SettingsScreen.kt	android/ui-mobile/src/main/kotlin/ui/settings/SettingsScreen.kt
R082	android/ui-mobile/src/main/kotlin/TmdbKeyScreen.kt	android/ui-mobile/src/main/kotlin/ui/settings/TmdbKeyScreen.kt
R057	android/ui-mobile/src/main/kotlin/LibraryScreen.kt	android/ui-mobile/src/main/kotlin/ui/setup/LibraryScreen.kt
R071	android/ui-mobile/src/main/kotlin/LoginScreen.kt	android/ui-mobile/src/main/kotlin/ui/setup/LoginScreen.kt
R093	android/ui-mobile/src/main/kotlin/StartOverAction.kt	android/ui-mobile/src/main/kotlin/ui/setup/StartOverAction.kt
R093	android/ui-mobile/src/main/kotlin/TelegramApplicationScreen.kt	android/ui-mobile/src/main/kotlin/ui/setup/TelegramApplicationScreen.kt
R067	android/ui-mobile/src/main/kotlin/SystemRows.kt	android/ui-mobile/src/main/kotlin/ui/system/SystemRows.kt
R051	android/ui-mobile/src/main/kotlin/SystemScreen.kt	android/ui-mobile/src/main/kotlin/ui/system/SystemScreen.kt
D	android/ui-mobile/src/test/kotlin/PlayerScreenTest.kt
R099	android/ui-mobile/src/test/kotlin/AppChromeTest.kt	android/ui-mobile/src/test/kotlin/ui/AppChromeTest.kt
A	android/ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt
A	android/ui-mobile/src/test/kotlin/ui/LibraryFlowTest.kt
A	android/ui-mobile/src/test/kotlin/ui/LibraryFlowTestActivity.kt
R078	android/ui-mobile/src/test/kotlin/LibraryPositionsTest.kt	android/ui-mobile/src/test/kotlin/ui/LibraryPositionsTest.kt
R098	android/ui-mobile/src/test/kotlin/CatalogScreenDensityTest.kt	android/ui-mobile/src/test/kotlin/ui/catalog/CatalogScreenDensityTest.kt
R078	android/ui-mobile/src/test/kotlin/FetchReportSentenceTest.kt	android/ui-mobile/src/test/kotlin/ui/catalog/FetchReportSentenceTest.kt
A	android/ui-mobile/src/test/kotlin/ui/catalog/OptionalMetadataTest.kt
R098	android/ui-mobile/src/test/kotlin/PosterCardTest.kt	android/ui-mobile/src/test/kotlin/ui/catalog/PosterCardTest.kt
R078	android/ui-mobile/src/test/kotlin/TechnicalLineTest.kt	android/ui-mobile/src/test/kotlin/ui/catalog/TechnicalLineTest.kt
R099	android/ui-mobile/src/test/kotlin/TitleFactsTest.kt	android/ui-mobile/src/test/kotlin/ui/catalog/TitleFactsTest.kt
A	android/ui-mobile/src/test/kotlin/ui/catalog/UpdateLibraryTest.kt
R098	android/ui-mobile/src/test/kotlin/ControlsVisibilityTest.kt	android/ui-mobile/src/test/kotlin/ui/player/ControlsVisibilityTest.kt
R094	android/ui-mobile/src/test/kotlin/PlaybackStatRowsTest.kt	android/ui-mobile/src/test/kotlin/ui/player/PlaybackStatRowsTest.kt
R098	android/ui-mobile/src/test/kotlin/PlayerClockTest.kt	android/ui-mobile/src/test/kotlin/ui/player/PlayerClockTest.kt
R084	android/ui-mobile/src/test/kotlin/PlayerKidsLabelTest.kt	android/ui-mobile/src/test/kotlin/ui/player/PlayerKidsLabelTest.kt
A	android/ui-mobile/src/test/kotlin/ui/player/PlayerLifecycleFixture.kt
A	android/ui-mobile/src/test/kotlin/ui/player/PlayerLifecycleTest.kt
A	android/ui-mobile/src/test/kotlin/ui/player/PlayerTestActivity.kt
R074	android/ui-mobile/src/test/kotlin/SettingsRowsTest.kt	android/ui-mobile/src/test/kotlin/ui/settings/SettingsRowsTest.kt
R099	android/ui-mobile/src/test/kotlin/LibraryScreenTest.kt	android/ui-mobile/src/test/kotlin/ui/setup/LibraryScreenTest.kt
R094	android/ui-mobile/src/test/kotlin/LoginScreenTest.kt	android/ui-mobile/src/test/kotlin/ui/setup/LoginScreenTest.kt
R099	android/ui-mobile/src/test/kotlin/RefreshLineTest.kt	android/ui-mobile/src/test/kotlin/ui/system/RefreshLineTest.kt
R098	android/ui-mobile/src/test/kotlin/SystemRowsTest.kt	android/ui-mobile/src/test/kotlin/ui/system/SystemRowsTest.kt
```

Concerns/Blockers: none for the authored Android commit scope. Generated bindings
and whole-project final verification remain with the controller.
