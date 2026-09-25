# Android core ownership and catalog enrichment reassessment

Status: DONE

Completed 2026-09-24 within the delegated provider/enrichment scope. No scanner state, scores, manifests, commits, or UI/navigation source were changed. Existing shared-workspace edits were preserved.

## Changes and evidence

`StoredCoreProvider` now owns each constructed candidate before returning across a cancellable dispatcher boundary. A failed supply or replacement closes its unpublished candidate after validation/persistence failure or cancellation. Cleanup runs under `NonCancellable` on the configured dispatcher, and a close failure is suppressed without replacing the primary failure. Successful candidates remain published and open. Retry tests confirm rejected candidates are never exposed and a later successful candidate can be installed.

The same construction-return leak existed in `coreOrNull`. After root explicitly extended this scope, a deterministic cancellation test proved that failure too, and the same ownership guard was applied. All three creation paths now protect the handoff.

An intermediate regression also caught coroutine stack recovery discarding suppression added before another dispatcher throw. The final helper waits for cleanup to return under a noncancellable parent, then attaches the cleanup failure and rethrows. Tests verify both the original persistence/account exception in the causal chain and the suppressed close error. `CoreProvider.kt` remains 195 lines after ktlint formatting.

`ArtworkFetcher.kt`/`ArtworkFetcher` and `ArtworkState` are now `CatalogEnrichmentFetcher.kt`/`CatalogEnrichmentFetcher` and `CatalogEnrichmentState`. Direct coordinator/ViewModel properties and test references use `enrichment`; `FetchUiState` retains its public alias name. The credential-persistence test file/class was renamed consistently. Poster-specific reread callbacks and UI behavior remain unchanged. Authored-source search finds no old fetcher/state names.

The fetch KDoc now states that an overlapping call returns immediately without joining, lists the nullable outcomes, explains quiet error/result preservation, and documents cancellation propagation. Two direct production-fetcher tests verify overlapping admission and quiet-failure behavior. `CatalogRepository` interface KDoc describes propagated read failures, ordinary nullable absence, operational refresh failures in `Result`, and cancellation exceptions.

## Validation

All tests use actual provider/fetcher orchestration with the existing raw core/settings test boundaries; no native library, Telegram, or user data is needed.

| Check | Result | Evidence |
| --- | --- | --- |
| New supply/replacement regressions before repair | 8 tests, 7 failed; validation-cancellation control passed | `/tmp/android-core-candidate-red.log` |
| Intermediate suppression diagnosis | Two dual-failure tests still failed, identifying lost suppression | `/tmp/android-core-candidate-diagnostics.log` |
| `:core:data:testDebugUnitTest :feature:catalog:testDebugUnitTest :feature:system:testDebugUnitTest` | All three module suites passed; direct callers compiled | `/tmp/android-core-enrichment-green.log` |
| Added `coreOrNull` handoff regression before its repair | 9 candidate tests, only the new case failed | `/tmp/android-core-lazy-candidate-red.log` |
| Final `CoreCandidateCleanupTest`, `CoreProviderTest`, `CoreProviderReplaceTest` | All 22 focused tests passed | `/tmp/android-core-candidate-final.log` |
| ktlint 1.8.0 over the 12 explicitly owned authored files | Format and subsequent check passed | Explicit-path CLI invocation |
| Relevant tracked diff whitespace and stale-name checks | Passed | `git diff --check`; authored-source `rg` |

The full three-module run preceded the final `coreOrNull` addition; the subsequent 22-test provider gate covered that addition and recompiled core/data. Root retains ownership of final broad Android gates, scan, and finding resolution.

## Coordination and remaining concerns

Root approved the mechanical `FetchUiState` alias and `CatalogViewModelTest` caller edits. The UI worker confirmed no conflicting ownership. CoreClient/DefaultCoreClient, settings implementations, SettingsViewModel, PlayerViewModel, and UI/navigation sources were left to their owners.

Gradle initially created daemon 3092229 because the prior daemon used `user.country=US`, while this shell uses an empty country and `user.language=en`. Root accepted explicit ownership of daemon 3092229 for subsequent workers/final cleanup and coordinated retirement of the older daemon; all my Gradle commands exited. No independent background process remains owned by this task.

The Gradle logs retain existing unresolved Compose opt-in-marker warnings in non-UI modules. There are no known remaining defects in this slice; final project-wide validation remains with root.
