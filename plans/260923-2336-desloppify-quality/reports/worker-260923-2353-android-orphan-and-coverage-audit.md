# Android orphan and test-coverage audit

## Result and scope

The audited [Kotlin state](../../../android/.desloppify/state-kotlin.json), last
seen `2026-09-23T22:43:03Z`, contains **145 orphan findings and 145 untested-module
findings against exactly the same files**. The scan assigns 148 files to
production and 79 to tests. These counts do not establish 145 unused or
untested modules: several claimed missing relationships are directly present
in source. One unused application file and narrower test gaps were identified
below. No source, scanner configuration, scanner implementation, or finding
state was changed.

The controller's validated Android run reports 377 passing JVM tests. Its
[log](../../../.desloppify/validated-android.log) runs `testDebugUnitTest` tasks,
including the data, playback, feature, and mobile UI modules. This proves tests
execute; it does not establish line coverage or execution of device tests.

## Finding categories and evidence

| Files in each finding set | Category | Verified interpretation |
|---:|---|---|
| 110 | Application Kotlin | The graph misses ordinary imports, same-package references, and framework entry points. Examples below refute the blanket result; individual unused files still need adjudication. |
| 19 | Authored convention-plugin/helper Kotlin | Gradle registrations and callbacks supply entry points. These remain authored executable build logic worth reviewing. |
| 14 | Gradle scripts | Build/configuration inputs are incorrectly measured as ordinary application modules needing source importers. Two additional short Gradle scripts are also in the production inventory but have no orphan finding. |
| 2 | `src/androidTest` Kotlin | Actual instrumentation tests are mis-zoned production and incorrectly expected to have their own tests/importers. |

Concrete missing relationships:

- **Package imports:** [MainActivity](../../../android/app/src/main/kotlin/com/mediagram/android/MainActivity.kt)
  imports `ui.MobileApp`, whose declaration lives at
  [ui-mobile/src/main/kotlin/MobileApp.kt](../../../android/ui-mobile/src/main/kotlin/MobileApp.kt),
  not a physical `ui/` directory. It also imports `data.WatchSync`. Package
  names cannot be resolved by assuming they match paths under the scan root.
- **Same-package symbols and multiple declarations per file:** `MobileApp`
  calls `CatalogAndPlayer`, declared in
  [LibraryFlow.kt](../../../android/ui-mobile/src/main/kotlin/LibraryFlow.kt).
  [Shelves.kt](../../../android/feature/catalog/src/main/kotlin/Shelves.kt)
  uses `NATURAL`, declared in
  [NaturalOrder.kt](../../../android/feature/catalog/src/main/kotlin/NaturalOrder.kt).
  These calls need no import, and neither symbol matches its filename.
- **Actual test consumers:**
  [WatchStateRepositoryTest](../../../android/core/data/src/test/kotlin/WatchStateRepositoryTest.kt)
  constructs `DefaultWatchStateRepository` eight times and checks real repository
  behavior. Both files declare `package data`, so there is no import of the
  tested class. Likewise,
  [CatalogViewModelTest](../../../android/feature/catalog/src/test/kotlin/CatalogViewModelTest.kt)
  constructs `CatalogViewModel`, and
  [NaturalOrderTest](../../../android/feature/catalog/src/test/kotlin/NaturalOrderTest.kt)
  exercises `NATURAL`. Their source files cannot honestly be called wholly untested.
- **Framework entry points:** the
  [manifest](../../../android/app/src/main/AndroidManifest.xml) declares
  `.MainActivity` and `.MediagramApp`; the namespace is supplied by
  [app/build.gradle.kts](../../../android/app/build.gradle.kts).
  [DataModule](../../../android/core/data/src/main/kotlin/di/DataModule.kt),
  [CoreModule](../../../android/app/src/main/kotlin/com/mediagram/android/di/CoreModule.kt),
  and [PlaybackModule](../../../android/feature/player/src/main/kotlin/di/PlaybackModule.kt)
  use Hilt `@Module`, `@InstallIn`, and `@Provides`. Ordinary class import counts
  do not describe their reachability.
- **Build entry points:** [settings.gradle.kts](../../../android/settings.gradle.kts)
  includes the application/library modules and `build-logic`.
  [Convention registrations](../../../android/build-logic/convention/build.gradle.kts)
  map plugin IDs to `implementationClass` names; module scripts apply those IDs
  through version-catalog aliases. `AndroidApplicationConventionPlugin` calls
  [configurePrintApksTask](../../../android/build-logic/convention/src/main/kotlin/config/PrintApksTask.kt),
  which registers variant tasks. Lack of a conventional importer is not disuse.
- **Mis-zoned tests:**
  [EncryptedSettingsTest](../../../android/core/data/src/androidTest/kotlin/settings/EncryptedSettingsTest.kt)
  and [CoreLoadsTest](../../../android/core/rust/src/androidTest/kotlin/rust/CoreLoadsTest.kt)
  contain `@RunWith(AndroidJUnit4::class)` and `@Test`. They belong in the test
  zone. The latter checks native-library loading, which a JVM compile cannot prove.

These are source-verified failures of the dependency/coverage result. The exact
adapter implementation cause was left to the separate upstream detector task.

## Concrete cleanup and test candidates

1. **Unused current application code:**
   [PackageSettings.kt](../../../android/core/data/src/main/kotlin/settings/PackageSettings.kt).
   Repository-wide searches for `PackageSettings`, `PackageCredentials`,
   `InMemoryPackageSettings`, and `EncryptedPackageSettings` found no runtime
   consumer or DI binding. Outside that file, executable uses are confined to
   `PackageSettingsTest` and `EncryptedSettingsTest`. The app build-script
   comment claiming MainActivity injects PackageSettings is stale: it injects
   WatchSync. Current settings DI binds Telegram, library, and TMDB stores.
   This is a concrete legacy cleanup candidate; preserve any explicitly chosen
   old-credential cleanup behavior when deciding its removal.
2. **Registered but unapplied build conveniences:**
   `AndroidTestConventionPlugin`, `KotlinSerializationConventionPlugin`,
   `DetektConventionPlugin`, and `SpotlessConventionPlugin` are registered but
   their custom `app.*` plugin IDs are not applied by current module scripts or
   other convention plugins. Classify these as unused local build capabilities,
   not broken application files. Registration makes them usable; pruning them
   depends on whether retaining this build toolkit is intended.
3. **Repository collection/write error contracts:**
   [WatchStateRepository.kt](../../../android/core/data/src/main/kotlin/WatchStateRepository.kt)
   implements `createList`, `renameList`, `deleteList`, and `setInList`, plus
   confirmation-before-refresh behavior. Its direct tests exercise profile
   selection, progress, and Kids, but not those collection operations or thrown
   write failures. Catalog tests use a fake repository and therefore do not
   cover this boundary. Add focused tests for success, refused writes, thrown
   writes, and unchanged snapshots; do not label the entire file untested.
4. **TMDB key persistence:**
   [TmdbSettingsTest](../../../android/core/data/src/test/kotlin/settings/TmdbSettingsTest.kt)
   tests the in-memory implementation. The instrumentation suite checks package,
   Telegram, and library preferences, but omits `EncryptedTmdbSettings` and
   `tmdb_settings`. A device test should verify the real store's round trip,
   clear behavior, and absence of plaintext key values. No storage defect was
   demonstrated by this audit.
5. **UI lifecycle wiring:**
   [PlayerScreenTest](../../../android/ui-mobile/src/test/kotlin/PlayerScreenTest.kt)
   explicitly tests only `shouldStopOnDispose`, not actual rotation/disposal
   wiring. [PlayerScreen](../../../android/ui-mobile/src/main/kotlin/PlayerScreen.kt)
   lines 69–89 connect open/stop/save to Compose effects and lifecycle events.
   The current tests have no Compose/Activity scenario exercising those paths.
   Similarly, LibraryScreen tests cover prompt mapping, not a rendered picker
   flow. These are real integration gaps, not evidence that every UI helper
   lacks tests.

No other wholly unused application file was confirmed. This is not an exhaustive
compiler reachability proof: candidate discovery used declaration-name searches,
then checked framework and Gradle registrations manually.

## Recommended adjudication

- Repair dependency resolution using declared Kotlin packages and exported
  symbols, including multiple declarations, top-level functions/properties,
  same-package references, aliases, and wildcard imports. Keep module/source-set
  boundaries in the graph. Recompute test associations from the corrected graph.
- Recognize manifest components, Hilt bindings, and Gradle plugin registrations
  as their specific kinds of entry points; do not exempt arbitrary Kotlin files.
- Zone `**/src/androidTest/**` as tests and `*.gradle.kts` as configuration.
  Keep authored convention-plugin/helper Kotlin inspectable, with build-aware
  reachability and meaningful validation rather than excluding all build logic.
- Existing generated UniFFI/build-output exclusions are distinct from authored
  code and should remain distinct. Avoid blanket orphan/test-coverage suppression.
- After repairs, rescan and adjudicate remaining files individually. Use the
  direct source/test counterexamples above to verify that the repairs work.
- Keep execution limits visible: JVM test success does not run the native-load
  or encrypted-preference instrumentation tests. The snapshot also reports
  reduced `ktlint_violation` coverage due to `parser_error`; this audit makes no
  claim that a complete lint analysis ran through Desloppify.

Unresolved decisions: retain or remove the unused legacy package-settings surface
and unapplied convention plugins; arrange device/emulator execution for the
existing instrumentation suite and any added storage/lifecycle checks.
