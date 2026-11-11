---
title: "2026-10-04 user decisions for the \"fix all open tasks\" sweep"
author: Andre
date: 2026-11-08
id: l-f4949d49
scope: repo
status: active
touches:
  - plans/261004-1508-open-tasks-sweep/plan.md
  - plans/261004-1508-open-tasks-sweep/reports/b3-profile-roles-preflight-report.md
---

## Decided
On 2026-10-04 the user asked to "fix all open tasks" and decided:
- Plans in order: Android editorial parity (260926-1330 ph 4–8) → subtitles leftovers + watch-state test cleanup (260930-0303, 260928-0130) → profile roles & PINs (260928-0047). Quest 3 VR is NOT in scope.
- Stats: below one minute reads "0 min"; "Counting since <date>" while nothing is counted (web, phone, TV; contract §6).
- Rail row "Continue watching" → "Continue" (web + Android).
- Player list button "Watchlist"/"On the list" → "My List"/"On My List" (web, phone, TV).

**Why:** user picked these via AskUserQuestion; they reverse shipped wording, so a later audit must not undo them.

## Watch out
the live ledger is `plans/261004-1508-open-tasks-sweep/plan.md` — resume from its tables after a compaction. Publishing to the channel and pushing still need the user's word. Related: viewing-stats-device-round-pending, player-aesthetic-is-a-user-decision.

**Subtitles (2026-10-04, later):** count The Deuce S3 upload as the end-to-end proof; may flip the "test" profile's Subtitles to English and back; may remove the inline read path on/after 2026-10-08 if the follower copy shows 0 inline rows and the other machine is ≥ 0.91.0; may run `--mkv` + local folders after the MP4 backfill. The MP4 cha
…

_From Claude Code's memory of this repo, last updated Oct 4._
