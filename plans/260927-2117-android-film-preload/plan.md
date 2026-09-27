---
title: "Android film preload: a Preload button beside Play, with progress"
description: "Preload a whole film into the device cache (filling the home cache server as a side effect), show progress on the film page, add a per-film status route to the cache server."
status: pending
priority: P2
effort: 14h
branch: feat/android-film-preload (worktree /home/andre/Workspace/mediagram-preload)
tags: [android, playback, cache, cache-server, preload, tv]
created: 2026-09-27
---

# Android — film preload

Android-only by the user's decision (2026-09-27, "only an android"): the web player has
no film preload and gets none — a deliberate difference, recorded in the code beside the
button and in DESIGN.md. Base: `feat/android-settings-redesign` once 0.69.3 is committed.

Scout facts (verified 2026-09-27, main @ ca3137b4): the device path already writes a
whole set into the player's cache (`CacheDataSourceWriter` over the strict factory,
which also PUTs missed chunks to the home cache server); the home server has no Telegram
session and can only report store-wide totals (`/v1/status`); the core needs no change
(`read` + `totalSize` suffice). `SeriesPreloader` cannot be reused as is: `want()`
replaces its queue, `fits` caps at 0.75 × budget (default budget 2 GiB), no cancel, no
progress (`ProgressListener` is `null`), dies with the process.

## Decisions (user, 2026-09-27 — do not reverse silently)

1. **Lands on the device cache** the player reads; the home cache server fills as a side
   effect through the existing LAN write path. No Telegram session on the server.
2. **Indicator:** button "Preload · 5.8 GB" → thin bar "2.1 of 5.8 GB · 36%" (tap to
   cancel) → "Preloaded ✓" (Remove). Chunks already held from playback count. A quiet
   line "Home server: x of y GB" when the server holds any of it — needs a new route
   `GET /v1/sets/{id}` → `{total, chunks_held, bytes_held}`.
3. **Survives leaving the app:** a `dataSync` foreground service while anything is
   queued; POST_NOTIFICATIONS stays undeclared (the service runs; its notification is
   simply not shown). The page's indicator is the progress display.
4. **Space:** a manual preload ignores the 0.75 rule but must fit the budget (minus what
   is playing); otherwise "Needs 5.8 GB — raise the cache budget" linking to Storage.
   No pinning (add it only if evictions show up).
5. **Pauses while anything plays**, resumes after; one at a time, others "Queued".
6. **Phone/tablet and TV film pages, films only** (series already preload two episodes).
7. **Parallel** with the home-parity work, in its own worktree.

## Phases

| # | Phase | Owns | Status |
|---|-------|------|--------|
| 01 | [Cache server: per-film status route](phase-01-cache-server-per-film-status-route.md) | crates/mediagram-cache, `LanChunkClient` | done |
| 02 | [Preload engine: queue, progress, pause, service](phase-02-preload-engine-queue-progress-service.md) | core/playback, feature/player, app manifest | pending |
| 03 | [Film pages: button, bar, server line](phase-03-film-page-preload-button-progress.md) | ui-mobile `TitlePills`/`TitleDetailScreen`, ui-tv `TvTitlePage`, a small ViewModel | pending |
| 04 | [Verify on tablet + TV box, docs, version](phase-04-verify-docs-version.md) | tests, docs, manifests | pending |

01 and 02 are independent; 03 needs both. One agent at a time in the worktree.

## Versioning

Minor bump per feature phase by regex in the three manifests. This branch and
`feat/android-home-web-parity` both bump: whichever merges second renumbers (memory:
bump versions by pattern — renumber unpushed).

## Deploy (not an agent step)

The cache server runs on a separate home machine (never install it on the dev box). After
phase 01: build for that box and copy the binary + unit there with the user. Android must
keep working against an older server: a 404 on the new route hides the server line.

## Rollback

`git revert` per phase. The only persisted state is the preload queue (if persisted) and
the cached bytes themselves, which the existing LRU already owns.
