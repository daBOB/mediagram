---
phase: 6
title: "Verification and release"
status: in-progress
priority: P1
effort: "2h"
dependencies: [2, 3, 4, 5]
---

# Phase 06: Verification and release

## Overview
Prove the whole game runs end to end on real devices, tune it, version it and
document it.

## Requirements
- Full game via the smoke test (`bun tools/smoke.mjs --game kopfnuss`) reaches Fact and ends.
- Live run: TV box + 3 phones, one complete game. Record the total time (target 10–12 min).
- Check: tricks stack and bounce visibly, Kleber + Schneckenpost together stay answerable, the climb is decided.
- The user reviews `questions/fakten.json`.
- Version: next minor in `Cargo.toml` (workspace), `web/package.json`, `android/app/build.gradle.kts` (check what the Grand Allee session already took).
- Changelog entry in `docs/project-changelog.md`; Kopfnuss row in spieltisch `design/game-brief.md`.

## Related Code Files
- Modify: `tools/smoke.mjs`, the 3 manifests, `docs/project-changelog.md`, `plans/261108-1631-kopfnuss-quiz-party-game/plan.md` (review section)

## Steps
1. `cargo test -p mediagram-game`, `cargo clippy`, `bun test`, `bun run typecheck`.
2. Smoke run, then the live run; screenshots go to `reports/evidence/`.
3. Tune config values only (timings, rounds) from the live run.
4. In the `feat/kopfnuss` worktree: rebase on the latest `spieltisch-games` (Grand Allee edits), rerun the tests, bump the version, write the changelog; then merge into `spieltisch-games` (only when the user says so) and remove the worktree. <!-- Updated: Validation Session 1 - own worktree -->

## Success Criteria
- [ ] All checks green; evidence on disk; the user has approved the facts
- [ ] Review section in `plan.md` with the measured game length and tuning changes

## Risk Assessment
- Merge conflicts with Grand Allee in shared files (`protocol.rs`, `protocol_settings.rs`, `factory.rs`, `room_settings.rs`, `TvApp.tsx`, `PhoneApp.tsx`, `PhoneLobby.tsx`, `ui/lobby.ts`) → resolve by keeping both arms; rerun all tests after the rebase.
