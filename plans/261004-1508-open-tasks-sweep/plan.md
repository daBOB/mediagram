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

5. **Subtitles (2026-10-04):** count The Deuce S3 upload as phase 06's end-to-end proof; may
   flip the "test" profile's Subtitles to English and back for the cross-device check; may
   remove the inline read path on/after 2026-10-08 when the follower copy shows 0 inline rows
   and the other machine reads ≥ 0.91.0; after the MP4 backfill, run `--mkv` and the local
   folders, paced. **Backfill: resume now — the user confirmed the other machine is idle.**
   Started 2026-10-04 20:00 (1,828 sets, log `~/.local/share/mediagram/backfill-channel-261004-2000.log`).

6. **Profile roles (2026-10-04):** start the web + core half (01–05a) now, in parallel with the
   Android work; a new kids profile starts at **FSK 6**; the household-admin PIN stays
   **separate** from the web's Settings admin token. Pre-flight rulings in
   `reports/b3-profile-roles-preflight-report.md` override the phase files where they differ
   (state schema web v12 / core v8, file splits, stamp bounds, keep Back = "Stay as I am").

Outward actions still need their own word each time: publishing to the TV channel,
pushing to origin. Earlier rewatch-date default stands (watched keeps the latest finish).

## A — small fixes (this session)

| # | Item | Who | Status |
|---|------|-----|--------|
| A1 | Decisions 2–4 on web + Android, plus the web's stale department pill | subagent, worktree | done 0.100.0 — pill was stale focus |
| A2 | `watched` finish-stamp overflow, core + web | subagent, worktree | done 0.99.12 — other row types share the hole (follow-up) |
| A3 | Episode label parity, Android `core/model/EpisodeLabel.kt` vs web `lib/format.js` (episode 0, ranges) | subagent, worktree | done 0.99.10 — web printed ranges as JSON |
| A4 | TV box mDNS finds no cache server within the 10 s window | lead, device | pending |
| A5 | Tutorials "Continue your courses": tablet showed a Next-up card the web did not | lead, data | done 0.99.11 — both narrowed after limiting |
| A6 | 4K playback measurement on the box (HDR10 + DV, cold, "TV test" profile) | lead, device | pending |
| A7 | Reconcile stale plan status tables | subagent | done `18b57905` (+ 260925-2046) — 4 left unverified, see reports/a7 |
| A8 | The same far-future stamp hole in every other synced row type, core + web | subagent, worktree | done 0.100.1 |

Version: subagents commit without bumping; the lead bumps by pattern when merging each
into `main` (memory: bump versions by pattern; another session commits to main).

## B — plans (after A, one at a time)

| # | Plan | Status |
|---|------|--------|
| B1 | `260926-1330` — groups A (0.102.0) and B (0.101.0) merged and walked on the tablet 2026-10-04 (wide title spread, genres in facts, Audio languages, crew link → person, series air dates + spelled seasons, genre tiles, Latest "Movies", Collections cards); groups C (TV title/series) + D (TV browse) building; then phase 7 close (TV box walk needs adb) | in progress |
| B2 | subtitles: 03–06 + 08 (tablet↔web) verified 2026-10-04; MP4 backfill running since 20:00 → then `--mkv` + folders; 09 on/after 2026-10-08; 08 TV leg needs adb · watch-state: 02 merged 0.102.1; 03 after B1 C/D merge; 04 after 03 | in progress |
| B3 | `260928-0047-profile-roles-pins-kids-age-limits` 01–08 | pending |

Each plan was written in late September; before building, re-check it against `main`
(the code moved a lot since) and rule on conflicts in that plan's own ledger.
