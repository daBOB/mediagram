# Phase 01 — Stateful FakeCore watch state + contract cases

## Context links

- Rust truth: `crates/mediagram-core/src/state/rows.rs` (set_progress :48-65 + read order
  :30-44, clear_progress :68-74, set_watched true/false :109-126, snapshot hides tombstones
  :79, watchlist/kids tombstones :143-191), `api/state.rs` (:100, :105, :156 — kids global,
  snapshot fails soft), `state/mod.rs:71-78` (writes never throw), tests
  `state/rows_tests.rs`, `state/kids_profile_tests.rs`.
- Android: `android/core/testing/src/main/kotlin/{FakeCore.kt (state stubs :439-462,
  profiles :392-437), CoreContract.kt (:41-98), FakeCoreProvider.kt}`,
  `core/testing` unit runner `FakeCoreContractTest`, device runner
  `android/core/rust/src/androidTest/kotlin/rust/RealCoreContractTest.kt`,
  `android/core/data/src/test/kotlin/WatchStateRepositoryTest.kt` (its private `StateCore`),
  `WatchStateRepository.kt` (`DefaultWatchStateRepository`, `writing{}`/`refreshSnapshot`).

## Overview

P2 · pending. Give `FakeCore` the core's watch-state behaviour (per profile, with a clock the
test controls), pin that behaviour with new `CoreContract` cases that run against both the
fake and the real core, and replace `WatchStateRepositoryTest`'s own `StateCore` with it.
No production code changes in this phase.

## Requirements

- FakeCore state: progress (upsert, `at` clamped ≥ 0, `updatedAt` = clock, snapshot order
  newest first), `clearProgress` (plain delete, stamps nothing — mirror the core even though
  the repository will stop exposing it), `setWatched(true)` (one step: re-stamp
  `finishedAt = max(now, removedAt+1)` every time and clear the position), `setWatched(false)`
  (tombstone `removedAt = max(now, finishedAt+1)`, position untouched, snapshot hides it),
  watchlist and kids with tombstones and idempotent re-adds, kids GLOBAL across profiles,
  editor's choice as the core does, lists (create/rename/delete/setInList) as the core does —
  read the Rust for each; per-profile snapshots.
- A clock the tests control (e.g. `FakeCore(clock = { … })` or a settable `now`), default
  monotonic so existing users need no change.
- Writes never throw (like the core); keep any existing failure hooks FakeCore already has.
- `CoreContract` gains watch-state cases for every rule above (names describe behaviour, e.g.
  `finishingATitleAlwaysReStampsAndClearsItsPosition`). They must pass on FakeCore now
  (unit) and be written so they pass on the real core (they only touch the state database —
  no network, no session). Where the real core's timestamps are wall-clock, assert
  relations (newer/older, cleared, present), not exact values.
- `WatchStateRepositoryTest` uses FakeCore instead of `StateCore`; its 9 tests keep their
  intent (fix any that encoded the old fake's wrong rules, and say which in the report).
- Every existing FakeCore user must stay green (`./gradlew testDebugUnitTest` whole
  project) — some tests may have relied on writes being no-ops; if a test's intent was
  "nothing changes", adjust it and list it.

## Steps

1. Read the Rust rules + tests; write the contract cases first (they fail on today's stubs).
2. Implement FakeCore state until the cases pass; add the clock.
3. Swap `StateCore` in WatchStateRepositoryTest.
4. `./gradlew :core:testing:testDebugUnitTest :core:data:testDebugUnitTest` then whole
   project `testDebugUnitTest lint`. Compile the instrumented runner
   (`:core:rust:compileDebugAndroidTestKotlin`) — do NOT run it on a device in this phase
   (the tablet is in use); phase 04 runs it.
5. Patch bump by regex (`Cargo.toml` + Cargo.lock member versions, `web/package.json`,
   `android/app/build.gradle.kts` versionName) — this branch is off main at 0.69.4 → 0.69.5
   (renumbered at merge if needed); changelog entry. Do not commit.

## Todo

- [x] contract cases for every rule (fail first)
- [x] stateful FakeCore + clock
- [x] WatchStateRepositoryTest on FakeCore
- [x] whole-project tests + lint green; androidTest compiles
- [x] version, changelog

## Success criteria

A regression of cc8bf95d (finish without re-stamp) or 40a43589 (finish that leaves the
position) in FakeCore fails a contract case; the same cases compile for the device runner.

## Risks

- Hidden reliance on no-op writes in feature tests that use FakeCore through
  `FakeCoreProvider` (setup, settings, TV fixtures) — run everything.
- Real-core wall-clock timestamps: assert order, not values.
