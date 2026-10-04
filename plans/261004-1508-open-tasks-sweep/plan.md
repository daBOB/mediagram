---
title: "Open tasks sweep: small fixes, decided wording, then three plans"
description: "Everything left open after the viewing-stats and home-parity work, in one tracked order."
status: in-progress
priority: P1
created: 2026-10-04
---

# Open tasks sweep

The user asked (2026-10-04) to "fix all open tasks". This file is the ledger: what is
in, in what order, what was decided, and where each item stands.

## Decisions (user, 2026-10-04 — do not reverse silently)

1. **Plans to take on, in order:** Editorial parity (Android, `260926-1330` phases 4–8)
   → Subtitles leftovers + Android watch-state test cleanup (`260930-0303`,
   `260928-0130` phases 02–04) → Profile roles & PINs (`260928-0047`, all 8 phases).
   **Quest 3 VR (`260930-1500`) is not in this sweep.**
2. **Stats zero time:** below one minute reads **"0 min"** (was "under a minute"), and
   while nothing is counted yet a line **"Counting since <date>"** sits under the
   figures — web, phone/tablet, TV; the shared contract §6 changes with it.
3. **Rail label:** the rail row reads **"Continue"** (was "Continue watching"), web and
   Android, rail width unchanged.
4. **Player list button:** **"My List" / "On My List"** (was "Watchlist" / "On the
   list") on the web, phone and TV players.

Outward actions still need their own word each time: publishing to the TV channel,
pushing to origin. Earlier rewatch-date default stands (watched keeps the latest finish).

## A — small fixes (this session)

| # | Item | Who | Status |
|---|------|-----|--------|
| A1 | Decisions 2–4 on web + Android, plus the web's stale department pill on rail pages | subagent, worktree | pending |
| A2 | `watched` finish-stamp overflow in the core (`state/rows.rs`, `state/watched_exchange.rs`), the stats-stamp bug's twin | subagent, worktree | pending |
| A3 | Episode label parity, Android `core/model/EpisodeLabel.kt` vs web `lib/format.js` (episode 0, ranges) | subagent, worktree | pending |
| A4 | TV box mDNS finds no cache server within the 10 s window | lead, device | pending |
| A5 | Tutorials "Continue your courses": tablet showed a Next-up card the web did not | lead, data | pending |
| A6 | 4K playback measurement on the box (HDR10 + DV, cold, "TV test" profile) | lead, device | pending |
| A7 | Reconcile stale plan status tables (`260916`, `260919`, `260920-*`, `260921`, `260923-*`, `260924-*`, `260925-1923`, `260929-0215`, `260928-0306`, `261002-0213`) | lead, docs | pending |

Version: subagents commit without bumping; the lead bumps by pattern when merging each
into `main` (memory: bump versions by pattern; another session commits to main).

## B — plans (after A, one at a time)

| # | Plan | Status |
|---|------|--------|
| B1 | `260926-1330-android-editorial-departments-parity` phases 4–8 | pending |
| B2 | `260930-0303-subtitles-for-films-and-series` leftovers + `260928-0130-android-one-watch-state-fake` 02–04 | pending |
| B3 | `260928-0047-profile-roles-pins-kids-age-limits` 01–08 | pending |

Each plan was written in late September; before building, re-check it against `main`
(the code moved a lot since) and rule on conflicts in that plan's own ledger.
