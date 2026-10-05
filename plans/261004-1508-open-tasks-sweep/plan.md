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

7. **Box and 4K (2026-10-04, evening):** fix 4K HDR/DV stutter with **parallel downloads**
   for high-bitrate files (cold Telegram ~15–17 Mbit/s vs 27–60 Mbit/s); the mDNS test may
   clear the box's manual cache address for about a minute and restore
   `http://192.168.0.240:7788`; **publish to the channel after the Android profile work
   lands** and is walked (the box runs 0.106.1 + the DTS fix by adb meanwhile).

Outward actions still need their own word each time: publishing to the TV channel,
pushing to origin. Earlier rewatch-date default stands (watched keeps the latest finish).

## A — small fixes (this session)

| # | Item | Who | Status |
|---|------|-----|--------|
| A1 | Decisions 2–4 on web + Android, plus the web's stale department pill | subagent, worktree | done 0.100.0 — pill was stale focus |
| A2 | `watched` finish-stamp overflow, core + web | subagent, worktree | done 0.99.12 — other row types share the hole (follow-up) |
| A3 | Episode label parity, Android `core/model/EpisodeLabel.kt` vs web `lib/format.js` (episode 0, ranges) | subagent, worktree | done 0.99.10 — web printed ranges as JSON |
| A4 | TV box mDNS finds no cache server within the 10 s window | lead, device | investigated 2026-10-04, not an app bug: the server answers QU/QM/legacy queries in < 50 ms from a wired host; the box (on Wi-Fi) never gets a ServiceFound in its window, with or without a second pass (the double "Registering listener" is NsdService-internal). Likely the router/AP dropping multicast between wired and Wi-Fi. Manual address (port 7788 by default since 0.99.8) is the fix in use; restored on the box |
| A5 | Tutorials "Continue your courses": tablet showed a Next-up card the web did not | lead, data | done 0.99.11 — both narrowed after limiting |
| A6 | 4K playback measurement on the box (HDR10 + DV, cold, "TV test" profile) | lead, device | done 2026-10-04 — HDR10/AAC plays; DV decodes natively (dvhe.st) but cold Telegram ~15–17 Mbit/s < 27 Mbit/s → rebuffers; DTS/TrueHD never started → A9 |
| A7 | Reconcile stale plan status tables | subagent | done `18b57905` (+ 260925-2046) — 4 left unverified, see reports/a7 |
| A8 | The same far-future stamp hole in every other synced row type, core + web | subagent, worktree | done 0.100.1 |
| A9 | DTS and TrueHD sat at 0:00 on the box (passthrough stalls) | lead | done 0.106.2 — FFmpeg decodes them to PCM; verified Magnolia + Rocketman on the box |
| A10 | 4K stutter: parallel chunk downloads | subagent + lead (box) | done 0.109.0 — box cold ~30 Mbit/s (was 15–17): ≤ 28 Mbit/s titles play; 86 titles > 32 Mbit/s still rebuffer — next step is core (separate connections / file-DC routing) |

Version: subagents commit without bumping; the lead bumps by pattern when merging each
into `main` (memory: bump versions by pattern; another session commits to main).

## B — plans (after A, one at a time)

| # | Plan | Status |
|---|------|--------|
| B1 | `260926-1330` — complete: A–H merged, check.sh green, final review fixed, TV box walk fixed and re-walked on 0.109.1 (Left from Search, franchise hero, Back to franchise card, Back to Editor's choice card) | done |
| B2 | subtitles: 03–06 + 08 (tablet↔web) verified 2026-10-04; MP4 backfill running since 20:00 → then `--mkv` + folders; 09 on/after 2026-10-08; 08 TV leg needs adb · watch-state: 02 merged 0.102.1; 03 merged 0.105.2; 04 docs done — tablet contract re-run needs someone to accept the install on the tablet | in progress |
| B3 | `260928-0047`: phases 01–07 merged in 0.110.0. Security review fixes merged in 0.111.0 (`f573c0b2`): Manage closes and drops its PIN on leave; the wrong-PIN wait persists; a first profile waits for a sync round on core, Android and web, per library; a first PIN never overrides (schema web v13, core v9). The re-review found M1, M2 and L1, and all three are fixed. `scripts/check.sh` is green (Android only after `--rerun-tasks`: Kotlin incremental compiling skips `by`-delegating fakes when an interface gains a method). On the tablet, RealCoreContractTest passes 44/44, plus CoreLoadsTest 1/1, and the picker walk was look-only. Still owed: the TV box install and walk (box unreachable at 02:35), phase 08 docs, and publishing to the channel once the user agrees. | in progress |

Each plan was written in late September; before building, re-check it against `main`
(the code moved a lot since) and rule on conflicts in that plan's own ledger.
