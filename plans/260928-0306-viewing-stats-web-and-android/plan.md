---
title: "Viewing stats: minutes watched, what was watched, started, finished — web and Android"
description: "Per-profile viewing stats recorded by both state engines, synced as bounded rows through the existing #mlib-state documents, shown on a stats page on both surfaces."
status: pending
priority: P2
effort: 24h
branch: feat/viewing-stats (off main once home, preload and decoder branches have merged)
tags: [stats, watch-state, sync, web, android, rust-core]
created: 2026-09-28
---

# Viewing stats

## Decisions (user, 2026-09-28 — do not reverse silently)

1. **Stats wanted:** watch time in minutes, what was watched, what was started, what was
   finished — per profile.
2. **Both surfaces** (Surface Parity; the web is the reference): the web's state store and
   the Rust core (Android) both record, pinned by shared fixtures.
3. **Stats page per profile:** minutes this week / this month / all time, a bar chart of the
   last 30 days, and a history list newest first ("Started · Der Pate · Sat 21:14",
   "Finished · Crime 101 · Fri") with minutes per title.
4. **Each profile sees only its own stats** now; parents/admin see their kids' once profile
   roles land (docs/superpowers/specs/2026-09-28-profile-roles-design.md, another session).
5. **Kept forever** — no retention cap on daily minutes or per-title totals (user changed the
   proposed 2-year cap: "all the time").
6. **Sync** in the existing per-device `#mlib-state` documents under NEW keys, which older
   readers drop (the `unwatched` precedent — docs/system-architecture.md § sync).
7. **Achievements, derived — never stored or synced** (user, 2026-09-28, "Accept"):
   computed on both surfaces from the stats rows, `watched.finished_at` and the library,
   pinned by shared fixtures; the earned date is when the threshold was crossed. Starter set:
   first/10/50/100 films finished; a whole series or course finished; 10/100/500 hours;
   7- and 30-day streaks; finished titles in 5/10 genres; 10 documentaries; 5+ episodes in
   one day. Shown as a section of the stats page (earned with dates, the next few with
   progress "7 of 10 films"). A dot on the stats entry when something new was earned —
   "seen" kept per device, unsynced; no pop-ups during playback. **Kids profiles get only
   finishing/exploring achievements** — none for hours, streaks or binges.
8. **Built after** `feat/android-home-web-parity`, `feat/android-film-preload` and
   `fix/android-decoder-stall-recovery` merge — it changes the state schema and sync format
   the profile-roles work also changes; start from a settled main and coordinate with it.

## Data (bounded — why it is safe to sync)

- **Per (profile, set, device):** `startedAt` (first play), `lastWatchedAt`, `seconds`
  watched, `finishes` (count). Bounded by titles actually watched.
- **Per (profile, local day, device):** `seconds`. ~365 rows/year/profile (~45 KB/year for
  four profiles).
- "Finished" dates come from the existing `watched.finished_at`; no raw per-session log is
  kept anywhere (it would grow without bound and every sync round downloads every device's
  document).
- Each device owns its rows; merge keeps the newest `updatedAt` per (key, device); totals
  are sums across devices — a merge can never double-count.

## Counting rule (to be pinned in fixtures)

Watch time = wall-clock time between consecutive position writes of the same title while it
plays, counted only when the position advanced, each step capped (≈ 1.5 × the 10 s write
tick) so a seek, a pause or a sleep never counts. Days are the watching device's local date.

## Phases

| # | Phase | Owns |
|---|-------|------|
| 01 | Shared fixtures + sync record format + merge rules | web/test/fixtures/watch-state/stats-*.json, docs |
| 02 | Web: record, store, export/merge, stats API, stats page | web/src/state, routes, web/public stats page |
| 03 | Rust core: schema migration, record in `set_progress`, export/merge, UniFFI `stats(profile)`; Android stats page (phone/tablet + TV) | crates/mediagram-core, android |
| 04 | Cross-device verification (web + tablet + TV box), docs, versions | — |
| 05 | Achievements: shared fixtures for each rule, derivation on web and in Kotlin/Rust, stats-page section, new-badge dot, kids subset | both surfaces |

Phase files are written when the branch is cut (their inputs depend on what merges first).

## Open for the phase-01 author

- Where the stats page lives on each surface (web rail item vs Settings; Android rail /
  menu / profile menu) — follow the web once it exists.
- Whether the history list also shows "Watched again" when a finished title is restarted.
