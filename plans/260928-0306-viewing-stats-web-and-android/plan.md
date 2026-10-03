---
title: "Viewing stats: minutes watched, what was watched, started, finished — web and Android"
description: "Per-profile viewing stats recorded by both state engines, synced as bounded rows through the existing #mlib-state documents, shown on a stats page on both surfaces."
status: completed
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

9. **The stats page is a rail item "Stats"** between Genres and Settings — web rail,
   phone rail, TV rail alike (user, 2026-10-03). The new-achievement dot sits on it.
10. **"Watched again" is a history entry** (user, 2026-10-03): restarting a finished title
    shows as its own line ("Watched again · Der Pate · Sat 21:14"), its minutes counted on
    top.
11. **Goes before profile roles** (user asked for stats, 2026-10-03; roles still pending).
    Kids are already known — `profiles.kids` (schema v7, core `kids_profile_tests.rs`) — so
    the kids achievement subset needs nothing from roles. "Parents see their kids' stats"
    stays with the roles work.

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

**Contract (names, wire keys, rules — read first):** [shared-contract.md](shared-contract.md)

| # | Phase | Owns | Bump | Status |
|---|-------|------|------|--------|
| 01 | [Web: pure stats rules and shared fixtures](phase-01-web-stats-rules-and-shared-fixtures.md) | `web/src/state/stats-*.ts` (pure), `web/test/fixtures/watch-state/stats-*.json` | patch | completed (0.95.3 on this branch) |
| 02 | [Web: record, sync, stats route, Stats page](phase-02-web-record-sync-route-and-stats-page.md) | `web/src/state/**`, `web/public/**` | minor | completed (0.96.0) |
| 03 | [Core: record, sync, summary, uniffi](phase-03-core-record-sync-summary-uniffi.md) | `crates/mediagram-core/**` | patch | completed (0.96.1) |
| 04 | [Android: Stats rail item and page, phone and TV](phase-04-android-stats-rail-and-page.md) | `android/**` | minor | completed (0.97.0) |
| 05 | [Cross-device verification, docs](phase-05-cross-device-verification-and-docs.md) | `docs/**` | none | completed 2026-10-03 — tablet, TV box and web verified ([results](reports/cross-device-verification-results.md)); 0.99.6 published to the channel |
| 06 | [Achievements: fixtures, web derivation, section, dot](phase-06-achievements-web.md) | web | minor | completed (0.98.0) |
| 07 | [Achievements: core and Android](phase-07-achievements-core-and-android.md) | core, android | minor | completed (0.99.0, 0.99.1 fixes) |

**Order:** 01 → 02 (web, the reference) → 03 → 04 → 05; 06 after 02; 07 after 04 and 06.
Branch `feat/viewing-stats`, worktree off `main`. Profile roles (pending) rebases on this.

## Open questions (resolved 2026-10-03)

- Stats page: rail item (decision 9). "Watched again": yes (decision 10).

## Planning state 2026-10-03
Phase files written against `shared-contract.md` (amended with every cross-phase decision).
Web phases 01–02 and the core phase 03 were applied task by task in scratch copies and pass
(web suite, typecheck, lint; core 478 lib tests, clippy, line limits, the web's fixtures). The
achievement rules (06/07) pass all 19 fixture cases on web and Rust. Not yet compiled: the
Kotlin of phases 04 and 07 (they build on phase 03's generated bindings).
One question for the user: a rewatch moves a `films-N` date later (contract §9).
