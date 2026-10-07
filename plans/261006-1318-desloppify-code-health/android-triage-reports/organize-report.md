# ORGANIZE report: Android app (141 open review issues)

The reflect blueprint is executed as written. All 141 open review issues are accounted for:
- 101 issues are in 19 manual clusters, with 66 steps.
- 26 issues are skipped permanently on the merits.
- 14 issues are deferred with a temporary skip whose note starts "deferred: awaiting user decision". None of them is in the execution queue.

No issue is in both a cluster and a skip, and none is in neither.

Every step was re-checked against the source at 31cb8561 by five read-only verifiers, one per cluster group. Each step names its files with verified line numbers (or a reproducible grep where there are many sites), the change, the tests, and the verify command run from android/: `./gradlew testDebugUnitTest :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin`, plus the module tasks for that step. Once parity-fixtures-and-test-gates lands, the command also compiles `:core:rust:compileDebugAndroidTestKotlin :core:data:compileDebugAndroidTestKotlin`. Each step that has a device check names it as the user's to run, and each step reminds the executor to bump the three manifests (CLAUDE.md § Versioning). No source file was modified.

## Clusters

Listed in execution priority order. Every cluster has fewer steps than issues, except the 1-issue system-telegram-row-live (1 step).

| P | Cluster | Issues | Steps | Depends on |
|---|---|---|---|---|
| 1 | catalog-mark-writes-guarded | 3 | 2 | none |
| 2 | lan-cache-store-failures | 4 | 3 | none |
| 3 | secret-stores-main-safe | 3 | 2 | none |
| 4 | system-telegram-row-live | 1 | 1 | none |
| 5 | failure-handling-one-way | 11 | 6 | catalog-mark-writes-guarded, lan-cache-store-failures, secret-stores-main-safe |
| 6 | parity-fixtures-and-test-gates | 5 | 4 | catalog-mark-writes-guarded, lan-cache-store-failures, system-telegram-row-live |
| 7 | profile-rules-one-source | 2 | 1 | none |
| 8 | dead-aliases-and-pass-throughs | 5 | 3 | none |
| 9 | playback-bindings-out-of-feature-player | 5 | 3 | lan-cache-store-failures, failure-handling-one-way |
| 10 | player-and-preloader-contracts-in-code | 6 | 4 | failure-handling-one-way, playback-bindings-out-of-feature-player |
| 11 | typed-department-and-tab-identity | 9 | 5 | catalog-mark-writes-guarded, parity-fixtures-and-test-gates, dead-aliases-and-pass-throughs |
| 12 | typed-navigation-ids | 2 | 1 | catalog-mark-writes-guarded, typed-department-and-tab-identity |
| 13 | tv-focus-targets-and-effects | 7 | 5 | typed-department-and-tab-identity |
| 14 | shared-surface-glue-in-ui-common | 6 | 3 | failure-handling-one-way, playback-bindings-out-of-feature-player, typed-department-and-tab-identity, typed-navigation-ids |
| 15 | process-globals-to-owned-state | 6 | 3 | tv-focus-targets-and-effects, shared-surface-glue-in-ui-common |
| 16 | build-deadwood | 2 | 1 | none |
| 17 | catalog-records-mirrored-in-core-model | 4 | 3 | catalog-mark-writes-guarded, failure-handling-one-way, parity-fixtures-and-test-gates, typed-department-and-tab-identity, typed-navigation-ids, shared-surface-glue-in-ui-common |
| 18 | comments-to-invariants | 8 | 5 | catalog-records-mirrored-in-core-model, playback-bindings-out-of-feature-player, player-and-preloader-contracts-in-code, typed-department-and-tab-identity, tv-focus-targets-and-effects, shared-surface-glue-in-ui-common |
| 19 | single-package-move-pass | 12 | 7 | lan-cache-store-failures, secret-stores-main-safe, player-and-preloader-contracts-in-code, typed-navigation-ids, process-globals-to-owned-state, comments-to-invariants |

Every dependency comes from a real file overlap found while verifying. For example:
- LanWriteQueue and LanCacheModule are edited by clusters 2, 5 and 9.
- CatalogViewModel.writeState is edited by clusters 1 and 5.
- SearchGroups.kt is edited by clusters 12 and 17.
- RememberLookups.kt is edited by clusters 14 and 17.
- TvFocus.kt is edited by clusters 13 and 15.
- The preload-wiring doc paths are edited by clusters 9 and 14.
- CatalogRepository.kt:84 is edited by clusters 17 and 18.
- TvLanCacheBlock is edited in cluster 2 and moved in cluster 19.

## Deviations from the blueprint (and why)

1. **catalog-records-mirrored-in-core-model moved from position 9 to 17.** It waits for the user's UniFFI-reach answer and shares SearchGroups.kt, RememberLookups.kt and the title screens with clusters 11, 12 and 14. If those clusters depended on it, the user's answer would block them. With it after them, only clusters 17 to 19 wait for that answer. Its ui-tv core:rust step and its build-file sentence hold whatever the user decides.
2. **Steps were consolidated so each cluster has fewer steps than issues:**
   - catalog-mark-writes-guarded: from 4 steps to 2.
   - lan-cache-store-failures: from 4 to 3. The writer-path catch and the sharing-switch write gate are now one step.
   - secret-stores-main-safe: from 5 to 2.
   - system-telegram-row-live: from 2 to 1.
   - parity-fixtures-and-test-gates: from 5 to 4. The alias rename is folded into the shared-fakes step.
   - profile-rules-one-source: from 2 to 1.
   - dead-aliases-and-pass-throughs: from 5 to 3.
   - catalog-records-mirrored-in-core-model: from 4 to 3. The mapping test is folded into the mirror step.
   - typed-department-and-tab-identity: from 6 to 5. Tests are folded into each step.
   - typed-navigation-ids: from 2 to 1.
   - tv-focus-targets-and-effects: from 6 to 5.
   - shared-surface-glue-in-ui-common: from 4 to 3.
   - build-deadwood: from 3 to 1.
3. **Facts the verifiers corrected in the step text:**
   - Libraries.kt:39/:43/:45: all three wrappers are redundant once the stores dispatch, not just :43.
   - The package_settings test is a JVM CoreStorageTest with a temp dir. core:data has no Robolectric.
   - FakeCore gets an accountGate knob for the System timeout test.
   - core:testing cannot reach core:playback, so the shared preload/LAN fakes go to core:playback testFixtures, with a fallback.
   - SettingsViewModel has 7 catches, not 17. There are 57 inline cancellation catches, and they stay unswept.
   - AppUpdater's fixed refusal sentence is at :127.
   - The Shelves call is at CatalogScreen.kt:118-122.
   - FakeCore and CatalogRepositoryTest must keep the generated TitleInfo and SearchHit.
   - LanCacheModule's PlaybackModule pointers are wrong; they belong to PreloadModule.
   - limitedParallelism(1) probably breaks the three UnconfinedTestDispatcher setups. The step keeps the KDoc fallback.
   - The TV tab is persisted as a bare Int. Both surfaces now persist it by key.
   - TvDocumentariesDepartmentPage has the same off-by-one as Movies.
   - There are 25 pointer comments in 20 files and 27 own-requester values.
   - A third positional Boolean call site exists at TvPlayerKeyHolder.kt:76.
   - 15 TV Settings files move, not 9.
   - The version catalog has 27 orphaned version keys, not 28.
   - The portrait lambda is threaded through 17 files, not 11.
   - The line-guideline grep must match "guideline", because the phrase wraps across lines.
   - Removing MenuScreen.destination also edits ui-common LibraryPositionsTest.kt:83.
4. **Decision-independent halves of three deferred issues are carried as named steps.** In each case the deferred issue is named in the step detail, not in issue-refs, so it stays deferred until the user decides:
   - lan_read_write_gates_diverge: the write gate is in lan-cache-store-failures step 1.
   - start_over_secret_coverage_uneven: deleting package_settings is in secret-stores-main-safe step 2. The LAN token is not cleared.
   - build_logic_template_scaffold: the dead serialization convention and the false Dependency Guard header are in build-deadwood step 1.

## Skips

**Permanent (26 issues, 14 reasons).** Each skip carries the reflect reason specific to that issue, attested as reviewed and not gaming:

| Reason | Issues |
|---|---|
| player-facade-keeps-controllers-private | 2 |
| interface-defaults-serve-test-doubles | 2 |
| test-fixture-knobs-no-production-impact | 1 |
| explicit-compose-parameters-are-idiomatic | 1 |
| restore-helper-fits-half-the-sites | 1 |
| cache-provider-global-encodes-simplecache-limit | 1 |
| harmless-unused-result-fold-when-touched | 2 |
| correct-five-line-loop-already-commented | 1 |
| explicit-short-resets-no-missed-field | 1 |
| rename-churn-without-misleading-reader | 7 |
| web-reference-uses-the-same-collection-split | 1 |
| subpackaging-churn-for-navigation-only | 4 |
| validated-on-load-two-local-literals | 1 |
| two-comparisons-in-one-module | 1 |

**Deferred for the user (14 issues, temporary, kept out of the queue):**

| Decision | Issues | Question for the user |
|---|---|---|
| ktlint | 1 | Spotless, Detekt or ktlint tooling? |
| secret-storage | 2 | security-crypto migration, or an exit plan? |
| lan-pairing | 1 | Should LAN reads require pairing? |
| m3-adaptive-pin | 1 | Keep the rc pin? |
| player-viewmodel-size | 6 | Allow an opener class past the 200-line guideline? |
| uniffi-surface | 2 | A module-wide rule for generated records |
| start-over-lan-token | 1 | Should Start over clear the LAN token? |

The CLI required the attestation "I have actually reflected and I am not deferring this for lazy reasons." on the 6-item player-viewmodel-size deferral. It is true: these 6 wait only for the user's answer on the 200-line guideline.

## Backlog decisions and leftover clusters

- **Backlog decisions.** All four reflect backlog decisions are "skip", so no CLI action was taken. These auto-clusters stay in the backlog with open non-review items:
  - auto/ktlint_violation (2906)
  - auto/test_coverage-untested_module (23)
  - auto/stale_exclude (16)
  - auto/test_coverage-transitive_only (2)

  Nothing was promoted.
- **Leftover clusters deleted.** These held no open issues:
  - local-simplification (5 issues), events-and-sync (8) and catalog-update (8). They were done earlier this cycle and all their members are fixed. They were not reopened.
  - auto/unused, auto/test_coverage-untested_critical and auto/stale-review, which were empty.

## Device validation the user owns

Each relevant step names its device check. All walks navigate only and change no settings, ANDROID_SERIAL is pinned, and nobody runs Start over on a real device.
- **Required walks** on the tablet (caad49da) and the TV box (192.168.0.35:5555):
  - typed-department-and-tab-identity
  - typed-navigation-ids
  - tv-focus-targets-and-effects
- **Device checks:** catalog-mark-writes-guarded, lan-cache-store-failures, system-telegram-row-live, player-and-preloader-contracts-in-code, shared-surface-glue-in-ui-common and process-globals-to-owned-state.
- **Smoke checks only:** secret-stores-main-safe, catalog-records-mirrored-in-core-model, playback-bindings-out-of-feature-player and single-package-move-pass.

