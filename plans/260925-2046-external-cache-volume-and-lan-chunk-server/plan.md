---
title: "External cache — a chosen volume, and a LAN chunk server"
status: done — awaiting merge
mode: hard + tdd
created: 2026-09-25
supersedes: [260921-1751-android-external-cache]
blockedBy: []
blocks: []
---

# External cache — a chosen volume, and a LAN chunk server

**Goal:** Android's cache can live on an SD/USB volume with a budget sized
to that volume. On the home network, every Android device also reads and
writes a shared `mediagram_cache` server, so a chunk fetched from Telegram
once is not fetched again by the next device.

**Design:** [`reports/brainstorm-260925-2030-external-cache-volume-and-lan-report.md`](reports/brainstorm-260925-2030-external-cache-volume-and-lan-report.md)
**Research:** [Android LAN / NSD / cleartext](reports/researcher-260925-2046-android-lan-access-nsd-cleartext-report.md),
[Rust server stack](reports/researcher-260925-2046-rust-lan-chunk-server-stack-report.md)

## Phases

| # | Phase | Status | Effort |
|---|---|---|---|
| 0 | [Survive cache errors, reconcile docs](phase-00-survive-cache-errors-and-reconcile-docs.md) | done (b3489a7, da44260) | 3h |
| 1 | [Cache on a chosen volume](phase-01-cache-on-a-chosen-volume.md) | done (17f6fcf..8eddb69); card check owed on microSD/TV hardware | 1.5d |
| 2 | [Chunk-aligned reads behind a seam](phase-02-chunk-aligned-reads-behind-a-seam.md) | done on `feat/android-chunk-reads` (6ccbca8, f3c6682), rebased on phase 1 | 0.5d |
| 3 | [`mediagram_cache` server](phase-03-mediagram-cache-server.md) | done on `feat/mediagram-cache-server` (9e0572f..08834f0) | 1.5d |
| 4 | [Android reads through the LAN server](phase-04-android-reads-through-the-lan-server.md) | done on `feat/android-lan-cache` | 2d |

Order is the user's: the volume first, then the LAN. **Phase 3 runs in
parallel** with 0→1→2, in its own worktree (Rust only, no shared files).
Phase 4 starts once 1, 2 and 3 are merged.

## Global constraints

- **TDD in every phase.** Tests that lock in current behaviour come first,
  then the refactor, then tests for the new behaviour. The regression gate
  is `scripts/check.sh` with `ANDROID_HOME` exported; without it the
  Android half is skipped and proves nothing.
- Every created or grown file is **strictly under 200 lines**.
  `SettingsScreen.kt` is at 200 and `MlibDataSource.kt` at 185 today; both
  shrink rather than grow.
- Module direction: `ui → feature → core:data → core:rust`, and
  `:core:playback → :core:data`. `:core:data` never learns chunk or volume types.
- Every media3 file carries the androidx `@file:OptIn(UnstableApi)`, for the
  reason `CacheProvider.kt` gives.
- **Phases 0–2 add no new permission.** Phase 4 adds `ACCESS_LOCAL_NETWORK`
  and `CHANGE_WIFI_MULTICAST_STATE`, and nothing else.
- Code comments, test names and commits carry no plan or phase references.
  Commits are conventional with no AI attribution.
- **Version:** all three manifests move in step. Compute the bump at merge
  against `main` as it then stands, a minor (0.55.0 → 0.56.0 today); do not
  bump on the branch. `versionCode` +1.
- **Device tests** follow memory: use a test profile, clear
  `cache/mlib` with `run-as`, and never "start over" on the Redmi. Do not
  start the real web player; `mediagram_cache` is a separate process and
  safe to run.

## Cross-plan

- Supersedes `260921-1751-android-external-cache`. Its phase-01 Task 1
  (`CacheVolumes.kt` plus tests) is reused verbatim; its budget presets and
  "budget applies at restart" were overtaken by `b91e9e8`.
- **`feat/android-tv-ui` (unmerged, 54 commits)** edits `CacheBudgetBlock.kt`
  and `SettingsScreen.kt`. Whichever lands second rebases. Phases 1 and 4
  own the Settings files; resolve on rebase, never by reverting the TV
  work.

## Deliberate surface differences (Surface Parity)

- The web player needs no LAN tier: it is itself a server with its own
  cache, kept as is by user decision.
- The TV surface gets the mechanism from `:core:playback` for free. Its UI
  for the permission prompt and the pairing token is **owed by the TV
  plan** after `feat/android-tv-ui` merges (user decision, validation
  session 1). Record it there when that plan is next touched.

## Validation Log

### Session 1 — 2026-09-25
**Red team** (report in `reports/`): 14 findings; the 4 High and the factual Mediums applied. Finding 5 (internal cache wiped under storage pressure) was put to the user: keep "cap follows volume", accept wipes.

**Verification results:** 18 claims checked. 17 verified, 1 failed, 0 unverified. Tier: Full.
- Failed: "preload marks sets held after a silent cache failure" (`SeriesPreloader.kt:168-169`, which does not exist; the file is 135 lines). Held is computed from `Cache.isCached` (`HeldSets.kt:36-40`). The reason in phase 0 was corrected; the decision is unchanged.

**Decisions**
1. Error flag on the player path only; preload stays strict → phase 0.
2. PUT auth: HMAC-SHA256 signature (`MGC1`); the token never leaves the device, and network binding was dropped → phases 3 and 4.
3. TV permission/token UI → follow-up owed by the TV plan → plan.md.
4. Execution: phase 3 in a parallel worktree → plan.md.

### Whole-Plan Consistency Sweep
Searched every file for: bearer, subtle, EncryptedPreferences, slider, "network it was paired", `READ_AHEAD` (renamed `CHUNK_BYTES`), "marks held", and phase dependencies.
Result: **0 unresolved contradictions.** The remaining hits describe today's code or the correction deliberately. Dependencies agree with the execution note (4 ← 1, 2, 3; 3 independent).

## Review (2026-09-25)

All five phases implemented TDD, each reviewed by an independent code-review pass, with its findings fixed and re-verified.

- Branches:
  - `feat/android-cache-volume`: phases 0 and 1.
  - `feat/android-chunk-reads`: phase 2, stacked on it.
  - `feat/mediagram-cache-server`: phase 3.
  - `feat/android-lan-cache`: all of the above merged, plus phase 4. **This is the branch to merge.** `scripts/check.sh` is green on it.
- Review findings that mattered, all fixed with tests:
  - Stale-volume sweep deleted the chosen card after a fallback.
  - The server's `total` write raced: 22 of 40 runs failed.
  - The LRU index drifted.
  - A short chunk would be cached and stall playback.
  - A scheme-less LAN address crash-looped the app.
  - Discovery held the multicast lock forever.
- Device numbers are in each phase's Review section.
- Still owed:
  - The card check (phase 1, step 9) on the microSD phone or TV box.
  - Phase 4's mobile-data step.
  - First-frame time with the LAN warm vs cold.
  - The TV permission and token UI (TV plan).
- Version: not bumped on any branch. Minor bump at merge.
