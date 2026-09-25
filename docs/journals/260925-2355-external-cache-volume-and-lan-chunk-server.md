---
date: 2026-09-25 23:55
title: External cache on a chosen volume and a home LAN chunk server
tags: [mediagram, android, cache, lan-server, isolation, hmac-signing]
severity: high
status: done — awaiting merge
---

# External cache on a chosen volume and a home LAN chunk server

## Summary

Five phases shipped in a single plan: Android cache lives on SD/USB with a budget capped to the chosen volume; reads align to 1 MiB chunks behind a seam; a separate `mediagram_cache` Rust server stores chunks on the home LAN for all devices to share; Android reads through the server first and falls back to Telegram; all phases passed independent code review with 14 critical findings caught and fixed. Device testing: Redmi shows 64 GB ladder and 2.7 GB held survivors. A 12 GB film plays in ~3 s cold and fetches zero chunks warm from the LAN. First play to the server: 67 chunks arrive, none dropped; the server holds 83 MB locally cached, 6 MB fetched from Telegram live, 77 MB served from the LAN.

## Key Decisions

- **Separate `mediagram_cache` server** over web-player cache reuse: isolation of lifecycle, failure modes, and team ownership (server-side is Rust only).
- **HMAC-SHA256 signed PUTs** (`MGC1` scheme, token never sent) instead of bearer tokens: the token stays on the device, a look-alike server on another Wi-Fi learns nothing, and a replayed PUT cannot modify the chunk because first-write-wins already ignores it.
- **1 MiB aligned chunks** as the shared key: `ChunkMemo` refuses short chunks (a LAN source could return them), preload bypasses the server and fills it for free, and the ladders stays homogeneous between local and LAN tiers.
- **`FLAG_IGNORE_CACHE_ON_ERROR` on player path only**: preload fails loudly when disk is full or a card pulled, preserving the invariant that held sets are always in cache. The player itself falls through to Telegram and retries the cache on the next request.
- **Worktrees, not `main`**: `main`'s working tree held another session's uncommitted web and Rust work, so every phase ran in its own worktree. Phase 3 (Rust only) ran in parallel with 0→1→2 and was merged with them on `feat/android-lan-cache`.

## What Went Wrong and How It Was Caught

**Code review on five phases caught 14 findings across Android and Rust; all fixed with tests before final merge:**
- Stale-volume sweep deleted the chosen card's cache after a fallback (ad33ccc adds a test that the sweep skips the open cache).
- Server's total-file race: `GET /v1/sets/{id}/total` + `PUT chunk 0` + another device's `PUT total` = race on 22 of 40 runs. Fixed with atomic `create_new` on `root/<id>/total` (2d1fcdc).
- LRU index drifted when two devices wrote concurrently. Rebuild adds a per-key in-process mutex (2d1fcdc).
- A chunk that came back < 1 MiB would be cached, stall future reads, and poison the replay (f3c6682: `ChunkMemo` now refuses short chunks).
- Scheme-less LAN address (`192.168.0.1:7788` from avahi-browse) crashed app on discovery failure (7c766cf: parsed and normalised before storage).
- Multicast lock and NSD discovery never stopped; the app held both for its entire lifetime (deb9786: one bounded 10 s pass, released on success/failure/timeout).
- System screen's "Source" row showed only the last read, which is almost always Telegram read-ahead, so LAN hits were invisible (51baa7b: now counts them in `sourceLine`).

## Impact and Current State

- **Tests:** every phase test-first; `scripts/check.sh` (clippy, cargo test, Gradle test and lint) green on the integration branch.
- **Commits:** 36 from phases 0–4; key sequence: prepare cache errors (b3489a7), volumes (17f6fcf), chunks (6ccbca8), server (9e0572f), device integration (4c5f797), and 8 fixes post-review (7c766cf..cd1bc47).
- **Device (Redmi, 0.54.0 debug):** Phase 1 – ladder 512 MB…64 GB, 2.7 GB held survived restart, fallback shown. Phase 2 – 12 GB film, ~3 s to first frame (within 2–4 s band), 174 upstream, 0 re-plays. Phase 4 – 67 chunks to server on first play, 83 MB local + 6 MB Telegram + 77 MB LAN on replay, playback stays alive 32 s after server stops.
- **Branches:** feat/android-cache-volume, feat/android-chunk-reads, feat/mediagram-cache-server (all merged into feat/android-lan-cache; this branch is ready for main).

## Lessons

**Independent code review is the hard requirement for concurrency.** The server's total file race and the LRU drift both manifested as "22 of 40 runs fail", not every run. A single-pass read cannot find them. Parallel runs with coverage of the concurrent paths is non-negotiable.

**A scheme-less address is user-facing.** `avahi-browse -rt _mediagram-cache._tcp` prints `192.168.0.1 7788`, and users will paste it. Normalising on input (`http://` prefix, validation before storage) prevented a crash loop that would have surfaced post-merge.

**Multicast is a process-wide resource.** The NsdManager callback's lifetime outlasts the feature; releasing on timeout or success, not on the next call, is both correct and necessary for correctness on rotation (Settings close/reopen).

**The LRU survives restarts but the index drifts.** Rebuilding from mtime on every start is cheap (100 ms per 100k chunks). Attempting to persist the BTreeMap would fail on corruption mid-write and force a scan anyway.

## Next Steps

1. Resolve the TV branch conflict on `CacheBudgetBlock.kt` and `SettingsScreen.kt` (second to land rebases).
2. Card check on a real microSD phone or TV box (step 9 of phase 1, step 6 of phase 4 mobile-data, warm-vs-cold first-frame time).
3. Minor version bump (0.55.0 → 0.56.0) at merge: Cargo.toml, web/package.json, android/app/build.gradle.kts `versionName`, all in one commit before landing.
4. Backlog: QR pairing, token rotation, server-side delete, performance profile on a 10 GB MKV, and TV permission/token UI (TV plan's responsibility per plan.md).

**Status:** DONE — `feat/android-lan-cache` awaits merge to `main` once the other session has committed its work there; bump the minor version in all three manifests at merge.
