# Android System snapshot failure and retry

Status: DONE

## Cause and repair

`SystemViewModel` previously performed the core/catalog and cache snapshot reads in an uncaught flow upstream of `stateIn`. An `IOException` terminated that sharing coroutine, leaving an initial null snapshot or an old snapshot with no recovery path. `SystemScreen` returned immediately for a null snapshot, so an initial failure appeared blank.

The retained ViewModel now catches each snapshot attempt inside a retry-triggered flow before `stateIn`. Ordinary failures publish the fixed notice `System information could not be read. Try again.` and retain the last complete snapshot, if any. A successful retry replaces the snapshot and clears the notice. `CancellationException` is rethrown. The existing five-second `WhileSubscribed` retention and re-entry refresh behavior remain intact; retries remain available after repeated failures. `SystemUiState` and existing constructor parameters are unchanged.

`SystemScreen` collects the notice with lifecycle awareness, offers a retry button on initial and subsequent failures, and continues to render prior facts. It never displays the exception or private paths. The existing library-flow fixture gains only the approved failure-flow stub required by the real screen's new collection.

## Changed files

- `android/feature/system/src/main/kotlin/SystemViewModel.kt`
- `android/ui-mobile/src/main/kotlin/ui/system/SystemScreen.kt`
- `android/ui-mobile/src/test/kotlin/ui/system/SystemViewModelTest.kt`
- `android/ui-mobile/src/test/kotlin/ui/system/SystemScreenTest.kt`
- `android/ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt`

No dependencies, manifests, versions, scanner state, or git index were changed. Tests use the existing UI module's Robolectric and MockK support; the feature module has no equivalent test dependencies.

## Regression evidence

Before changing production, two tests collected the real `SystemViewModel.state` with failing core/catalog and cache snapshot boundaries. Both failed with uncaught `IOException`, rather than compilation or assertion setup failures: `/tmp/android-system-failure-red.log`, two tests, two failures.

The final focused command was:

```sh
JAVA_HOME=/home/andre/.local/opt/jdk-21.0.8 ANDROID_HOME=/home/andre/android-sdk \
  ./gradlew -Duser.country=US -Duser.language=en \
  :ui-mobile:testDebugUnitTest --tests 'ui.system.*' --tests ui.LibraryFlowTest
```

It passed in `/tmp/android-system-failure-green.log`: 32 tests, zero failures/errors/skips, confirmed from the five corresponding JUnit XML reports:

- `SystemViewModelTest`: 4 tests covering repeated core failure followed by successful retry, cache read failure followed by successful retry, preservation of an earlier snapshot, and cancellation after leaving followed by a fresh read on re-entry to the same ViewModel.
- `SystemScreenTest`: 2 real Compose renderer tests covering the initial error/retry transition and retry while prior facts remain visible.
- `LibraryFlowTest`: 6 existing navigation/retention tests, including the real System screen with its fixture ViewModel.
- `SystemRowsTest`: 11 existing formatting tests.
- `RefreshLineTest`: 9 existing refresh-display tests.

The snapshot tests exercise the real ViewModel and sharing flow with controlled `CoreClient` and `CacheProvider.occupancy` failures. The cache case verifies the provider-call failure boundary; it does not claim to induce a real filesystem or SimpleCache initialization failure. Compose tests exercise `SystemContent` and its callback; they do not claim a full Hilt screen plus real ViewModel integration. Together with the existing library-flow tests, they cover state recovery and screen wiring without new dependencies.

Scoped ktlint 1.8.0 verification passed for all five changed files: `/tmp/android-system-failure-format.log`. Scoped `git diff --check` also passed. Root independently reviewed production and tests and reported no defect. The broad Android gate remains root-owned.

## Process ownership

Both focused Gradle wrappers exited. The runs reused daemon 3120845 with the coordinated US/en JVM locale; no additional daemon was started or stopped. The Gradle slot was released to the profile worker after the green run, and no further Gradle command was started during the root's push-hook hold.

Concerns: None within the requested scope. The integration limits above are explicit.
