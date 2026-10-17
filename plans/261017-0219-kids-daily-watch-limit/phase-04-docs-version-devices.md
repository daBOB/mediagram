# Phase 4 — Docs, version, devices

## Overview
Priority medium, status pending (after 2 and 3).

## Requirements
- Docs: `docs/system-architecture.md` §8 Kids profiles (the limit, the rule, the lag, "household rule, not a lock"; also fix the stale fixed "FSK 12"), Watch state (the `dailyLimit` key); `docs/superpowers/specs/2026-09-28-profile-roles-design.md` permission table row; `docs/project-changelog.md`.
- Version 0.125.0 in `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` (by pattern; renumber if `main` moved).
- `scripts/check.sh` green.
- Devices, test profiles only: tablet (`ANDROID_SERIAL=caad49da`) and the box (`adb -s 192.168.0.56:5555`) — set a 30 min limit on a test kid, watch to the warning, cross zero mid-title, confirm finish + no autoplay + refused open; confirm the web's minutes appear on Android after a sync round.
- Check the live player's `MEDIAGRAM_SYNC_STATE`; report if off.

## Todo
- [ ] docs + changelog
- [ ] version
- [ ] check.sh
- [ ] tablet + box walk
- [ ] merge to main (no push/release without the user)

## Success criteria
Checks green, both devices behave per the rules, nothing pushed or released without the user's say.
