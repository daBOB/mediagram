# Android catalog updates

Status: implemented; ready for controller review.

The shared `core:data` LibraryUpdateCoordinator now awaits refresh, local catalog presentation, optional artwork fetching, and artwork presentation in one serialized operation. Manual updates live in CatalogViewModel's scope and survive loss of the Compose collector. Initial/settings reads do not fetch; manual refresh failures still permit enrichment of the held catalog; failed pushed refreshes skip enrichment.

ArtworkFetcher shares progress and results with the system facade, preserves quiet-fetch feedback policy, and releases its running flag and mutex on cancellation. LibraryFlow submits one update action and no longer observes transient refresh edges or forwards publication/artwork events. Refresh progress is independent of shelf regrouping. Failed local reads retain prior shelves with a notice, or produce Failed when nothing was loaded; a successful empty read still produces Empty.

## Evidence

- Before repair: the three new CatalogViewModel regressions failed as intended, 17 tests / 3 failures (`/tmp/android-catalog-before.log`). They cover retained shelves on read failure, first-read failure versus Empty, and regroup during a held refresh.
- After repair: `./gradlew :feature:catalog:testDebugUnitTest :feature:system:testDebugUnitTest :ui-mobile:testDebugUnitTest --no-daemon` passed (`/tmp/android-catalog-verified.log`). XML totals: catalog 96, system 12, mobile 105; 213 tests, no failures/errors/skips.
- Nine coordinator tests execute the production workflow with controlled repository/core boundaries: immediate/delayed completion, manual/pushed failure policy, quiet artwork publication, refresh-only behavior, cancellation during refresh/fetch, and serialization across updates. ViewModel tests cover no UI observer and composition disposal during a manual update.
- Existing tests that required observing an instantaneous refreshing edge now hold the refresh open before asserting its true/false transitions; completion itself no longer depends on those transitions.
- Scoped `git diff --check` passed. No live Telegram, user database, scanner-state, or commit operations.

## Integration

The core lifecycle worker confirmed LibraryEvents now follows selected-library changes, so CatalogViewModel no longer restarts the event collector using a reload counter. No Gradle dependency changes were required. App-wide Hilt/TV compilation remains the controller's integration gate. All Gradle processes started by this worker exited; the core lifecycle worker has the next Gradle slot.

## Persistence error follow-up

ArtworkFetcher now catches key-store read/write failures and reports fixed, actionable artwork errors; exception text and keys are never copied into UI state. A failed save preserves the previously known key status, a successful retry clears the storage failure, and cancellation still propagates. FetchViewModel's existing facade and public signatures are unchanged.

CatalogViewModel now reports thrown or refused create/rename/delete/membership writes through the existing catalog notice while retaining shelves and the repository snapshot. A successful retry clears its own notice; unrelated refresh notices remain. Cancellation produces no error notice.

Evidence: `/tmp/android-persistence-before.log` records 26 selected tests with five expected failures (three key-storage and two collection cases). The targeted rerun passed. Final `./gradlew :feature:catalog:testDebugUnitTest :feature:system:testDebugUnitTest :ui-mobile:testDebugUnitTest --no-daemon` passed in `/tmp/android-persistence-verified.log`: catalog 101, system 18, mobile 105; 224 tests, no failures/errors/skips. Scoped diff-check passed. No API changes, live services, scanner writes, or retained Gradle processes. The ProfileViewModel part remains controller-owned.
