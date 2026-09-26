---
title: "System page nerd stats"
description: "Telegram link, live playback sessions, transcoder and host figures on the web player's System page"
status: pending
priority: P2
effort: 14h
branch: feat/system-page-stats
tags: [web, status, telemetry, telegram, transcode]
created: 2026-09-22
---

# System page nerd stats

Adds four groups to `/api/status` and the System page, all four locked by the user:
Telegram link, playback sessions, transcoder, process/host.

The data travels the existing route: server producers go into `LiveFacts` (`web/src/status/snapshot.ts:13`), then `buildSnapshot`, then `/api/status` (`status/routes.ts:98`), then `pollStatus` every 2s, then `renderStatus` (`public/lib/status-view.js:84`).
Playback stats are the one group that starts in the browser. Each open player POSTs a small report every 5s. The server keeps the reports in memory for 15s, and they come back out through the same snapshot.

## Phases

| # | Phase | Effort | Status |
|---|---|---|---|
| 1 | [Plumbing and process/host](phase-01-snapshot-plumbing-and-host.md) | 2h | done |
| 2 | [Telegram link counters](phase-02-telegram-link-counters.md) | 3h | done |
| 3 | [Transcoder progress](phase-03-transcoder-progress.md) | 2.5h | pending |
| 4 | [Playback session reports](phase-04-playback-session-reports.md) | 3h | pending |
| 5 | [System page rendering](phase-05-system-page-rendering.md) | 2.5h | pending |
| 6 | [Docs, version, parity record](phase-06-docs-version-parity.md) | 1h | pending |

## Dependencies

- The phases run **one after another**, not in parallel. Phases 1–4 all extend `status/snapshot.ts` and `status/live-facts.ts`, so they would collide. Each phase lists the files it owns.
- Phase 1 comes first because it creates `live-facts.ts` and makes `live()` async.
- Phase 5 needs the snapshot shape from phases 1–4. It is one phase so the page's look gets decided once.
- Phase 6 comes last.
- Each server phase ships JSON only, so a phase can be reverted without touching the UI.

## Cross-cutting rules

- **Hot byte path.** Only counters and a ring-buffer write, once per `upload.GetFile` request (up to 1 MiB each). There is no per-chunk work. Percentiles, sorting and `/proc` reads run when the page polls, or on ffmpeg's 2s progress tick.
- **Keep the look.** Rows of label and figure in the page's own faces (`status-view.js:9-12`). No monospace grid.
- **Verification.** Unit tests plus the `stub-offline.ts` harness. **Never start the real player**: it holds the MTProto auth key and would break uploads in flight.
- Files stay under 200 lines. Two files that are already over are not grown beyond what the phases name: `player.js` (1195 lines) and `registry.ts` (292).
- Code comments must not reference the plan. Bump the version in all three manifests (CLAUDE.md § Versioning), as a minor bump.

## Rollback

Each phase is its own commit, and `git revert` of one phase stands alone. Nothing is persisted: no schema or on-disk format changes. The only protocol change is the new `POST /api/status/playback`. An old page never calls it, and a new page stops after a 404.

## Surface parity (summary; details in phase 6)

Android has a System screen (`android/ui-mobile/src/main/kotlin/SystemScreen.kt`) and a stats overlay on the player (`PlaybackStatsOverlay.kt`). Android does not transcode and has no server, so:
- Transcoder group: a deliberate difference.
- Playback group: already covered by the overlay on the device itself.
- Link group and host group: **parity is owed**, through a follow-up Android/Rust-core plan.

## Answers (user, 2026-09-26)

Defaults accepted: Android link/host parity is recorded as owed and scheduled later, not built
here; reconnects count the main connection only; playback rows show the viewer's address;
latency window stays the last 256 requests per DC.

## Unresolved questions (answered above)

1. Should the Android parity work (per-DC link stats in the grammers core, memory and disk-free rows) be its own plan now, or be scheduled later? This plan records it but does not build it.
2. "Reconnects" covers only the main MTProto connection. Download-DC senders are pooled and rebuilt without notice. Their failures show up as per-DC request errors instead. Is that acceptable, or should phase 2 also count the per-DC `Connection to dc N failed` log lines?
3. Should the playback row show the viewer's address (e.g. `192.168.1.23`, a tailnet IP) to tell devices apart? The default in this plan is yes, because the page is already limited to the household's own devices.
4. Latency window: the last 256 requests per DC. Is a time window, such as the last 60s, preferred?
