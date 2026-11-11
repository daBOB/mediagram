---
title: Second desloppify cycle merged as 0.118.0 (2026-10-07)
author: Andre
date: 2026-11-08
id: l-c0980724
scope: repo
status: active
touches:
  - scripts/release-android.sh
---

## Decided
The code-health cycle that started 2026-10-06 was merged into local `main` as 0.118.0 (merge `9b9d3494`) on 2026-10-07. It is not pushed and not published to the channel. Strict scores: Rust 88.3, web 87.3, Android 82.1.

The ledger is `plans/261006-1318-desloppify-code-health/`. Its `plan.md` Log holds the timeline. `open-questions-for-user.md` holds:
- the user decisions, including the UniFFI surface (question 8), which blocks 3 Android catalog issues;
- the device walks owed on the tablet and the TV box.

The health worktree `../mediagram-desloppify` is kept for the next cycle. All three `.desloppify/` state folders are synced into the main checkout, with backups in `~/Workspace/desloppify-backups/`.

**Why:** the cycle ran across two sessions and three usage-limit stops. A session-scratchpad wipe lost its first triage notes, and they had to be rebuilt from transcripts.

**2026-10-08:** native core rebuilt on main; 0.118.1 (`92cebc44`: Back on a non-Home tab returns Home) installed on the tablet; tablet read-only and playback walks passed (plan Log). Still owed: TV box walk (box was off the network), `EncryptedSettingsTest` (needs HyperOS Install via USB), channel release via `scripts/release-android.sh` (ask first), the user decisions. The user pushes main themselves (ssh needs their passphrase).

## Watch out
- Before publishing 0.118.0, rebuild the native core in the main checkout; its gitignored `.so` predates the merge.
- Do the device walks in the open-questions file, then ask before running `scripts/release-android.sh`.
- The next cycle starts with triage of about 100 web, 108 Rust (+277 clippy) and about 130 Android review issues.

Related: desloppify-needs-patched-build, rebuild-native-core-after-rust-changes, android-tv-self-update.
…

_From Claude Code's memory of this repo, last updated Oct 7._
