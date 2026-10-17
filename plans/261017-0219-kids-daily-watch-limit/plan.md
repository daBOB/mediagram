---
title: Kids daily watch limit
status: in progress
created: 2026-10-17
branch: feat/kids-daily-limit (worktree ../mediagram-kids-limit)
version: 0.125.0 (minor: new feature)
---

# Kids daily watch limit

A grown-up gives a kid profile a daily watching limit. Time watched on every
device counts against it; at five minutes left the kid is told; at zero what is
playing finishes, and nothing new opens until tomorrow.

## Decisions (user, 2026-10-17 — do not reverse without asking)

| Question | Answer |
|---|---|
| Shape | One daily limit per kid, same every day, resets at local midnight |
| At zero | Warning first; soft limit — the playing film or episode finishes, nothing new opens |
| Devices | Shared across devices, through the existing per-day stats sync (≤ ~5 min lag accepted) |
| Firmness | Household rule like the age filter, not a lock; switching profiles unchanged |

Not chosen, so not built: a PIN "add time for today" (a parent raises the limit
in Manage profiles instead), weekday/weekend limits, bedtime windows.

## Rules (`web/test/fixtures/watch-state/daily-limit.json` — rule, words, choices; held by the web and Kotlin tests. Sync keys in `profile-roles-*.json`, held by web, Rust and Kotlin)

- `dailyLimit`: minutes or `null` (no limit). Offered: 30, 45, 60, 90, 120, 180 min.
- Watched today = `SUM(stats_days.seconds)` for the profile and today's local date, over every device's rows.
- State from (limit, watched): `free` (no limit) · `ok` · `warn` (0 < left ≤ 5 min) · `out` (left ≤ 0).
- `out` refuses **opening** any title: browse, resume, Next up, the next episode, joining watch together.
- A title already open plays to its end. Autoplaying the next episode is refused like any open.
- The warning shows once per open title, on entering `warn` (or at open when already there).
- Who sets it: the same rule as the age limit (the kid's parent or the admin, with their PIN).

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | [Limit data, sync and rule](phase-01-limit-data-sync-and-rule.md): web server + Rust core + fixtures | done |
| 2 | [Web player](phase-02-web-player.md): Manage, open gate, warning, next-episode stop | done |
| 3 | [Android](phase-03-android.md): FFI, Manage (phone + TV), player gate, warning | done |
| 4 | [Docs, version, devices](phase-04-docs-version-devices.md) | done except the device walk (needs a kid profile; asked 2026-10-17) |

Phase 1 blocks 2 and 3; 2 and 3 are independent of each other.

## Key dependencies / risks

- Cross-device totals need web sync on: the live player must run with `MEDIAGRAM_SYNC_STATE` set, or web minutes never reach Android (check in phase 4).
- Another session commits to `main`; work in the worktree, bump versions by pattern.
- Device checks use test profiles only (tablet avatar "T", box "TV test"); never change a real kid's limit.
