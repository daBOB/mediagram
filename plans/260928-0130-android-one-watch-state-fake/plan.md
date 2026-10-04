---
title: "Android: one watch-state fake that follows the core's rules"
description: "Make FakeCore's watch state stateful and pinned by CoreContract against the real core; run the real repository in feature tests and delete the four hand-written fakes."
status: in-progress
priority: P2
effort: 10h
branch: refactor/android-one-watch-state-fake
tags: [android, testing, watch-state, core-seam, architecture]
created: 2026-09-28
---

# Android — one watch-state fake (architecture review candidate J)

Worktree `/home/andre/Workspace/mediagram-watch-state`, branch off `main` @ 220fa71e.
Scout (2026-09-28): the seam is UniFFI's `CoreInterface` behind `CoreProvider`
(`StateCoreClient` is gone since f98dec21). `FakeCore` (`:core:testing`) implements every
state method as an empty stub; `CoreContract` has 9 cases, none about watch state, and runs
on FakeCore (unit) and the real core (`RealCoreContractTest`, instrumented). Four fakes
re-implement the core's rules differently (player, TV, catalogue, `WatchStateRepositoryTest`'s
`StateCore`); two past bugs slipped through them (cc8bf95d re-stamp, 40a43589 two-call finish).

## Decisions (user, 2026-09-28 — "J", accepting the round's recommendations)

1. **One fake = a stateful `FakeCore`** following the core's rules, with a controllable
   clock; feature tests run the real `DefaultWatchStateRepository` over it; the four
   hand-written fakes are deleted.
2. **Rules pinned in `CoreContract`**, run on FakeCore (unit) and the real core (device):
   progress upsert/clamp/newest-first; set-watched clears the position and always re-stamps;
   un-watch tombstones and leaves the position; watchlist add twice is harmless; kids is
   global; tombstoned rows hidden.
3. **No "write returns its snapshot"** Rust change — two local SQLite calls are cheap.
4. **Who is watching is answered once, on the repository** — as `chosenProfile:
   StateFlow<Profile?>` rather than a kids boolean, because the profile-roles design on
   main (docs/superpowers/specs/2026-09-28-profile-roles-design.md) turns "kids" into a
   per-kid age limit; view models read `kids` (later its limit) from the profile.
5. **`clearProgress` leaves the Kotlin repository** (no callers; clears a position without
   the watched stamp — the resurrection hazard). The Rust function stays.
6. **Tests that wrote with no chosen profile move onto one.**
7. **Failure injection moves to `FakeCoreProvider`** (provider failures, not core ones);
   the mockk-relaxed TV-player and player-lifecycle fixtures migrate too.
8. **Order:** phase 01 now (touches only `:core:testing` and `:core:data` tests); phases
   02-03 after `feat/android-home-web-parity` and `feat/android-film-preload` merge to
   main, because they rewrite test files those branches also change.

## Phases

| # | Phase | Owns | When | Status |
|---|-------|------|------|--------|
| 01 | [Stateful FakeCore watch state + contract cases](phase-01-stateful-fake-core-and-contract.md) | core/testing, core/data tests | now | done |
| 02 | [Repository: `chosenProfile`, drop `clearProgress`](phase-02-chosen-profile-and-no-clear-progress.md) — the only guard against 40a43589's two-call shape once phase 03 deletes `ProgressRecorderTest`'s call-log fake, so this must land first — provider failure injection | core/data, feature view models | after merges | done (0.102.1) |
| 03 | [Migrate feature/TV/mobile tests to the real repository; delete the four fakes](phase-03-feature-tests-on-the-real-repository.md) | test sources across modules | after merges | done (0.105.2) |
| 04 | Device contract run (tablet), docs, version | core/rust androidTest, docs | after 03 | docs + version done 2026-10-04; device re-run waits for someone at the tablet (HyperOS asks before each USB install — 2026-10-04 21:07 INSTALL_FAILED_USER_RESTRICTED); `CoreContract` unchanged since its 34/34 tablet pass on 2026-10-03 |

Phases 03-04 get their own files when their turn comes (their inputs depend on what the
two open branches land).

## Coordination

The profile-roles design (another session) will change kids marks (age) and profiles
(`kidsAge`, `parentId`, `admin`, PIN). Whoever lands second adapts: the contract suite is
where the new kids-age rules should be pinned too.
