---
phase: 5
title: "Verification and release"
status: in-progress
priority: P1
effort: "4h"
dependencies: [4]
---

# Phase 5: Verification and release

## Overview
Prove it works in a real run, review, document, and hand over for release.

## Steps
1. Merge main first (spieltisch-games is at 0.165.0); resolve lobby-file conflicts keeping both games.
2. Simulated run: `tools/smoke.mjs` extended for Dreifelder with 4 bots (one stops answering, one picks wrong early, one disconnects) through to Final; run on the host and, with the user's go-ahead, on the TV box and the tablet (navigation only on the box; never change its settings).
3. Screenshots of each stage into `evidence/`.
4. `code-reviewer` pass; fix findings test-first.
5. Docs: one `docs/project-changelog.md` entry; bump `Cargo.toml`, `web/package.json` and `android/app/build.gradle.kts` together to the next free minor (decide at release, after merging main).
6. Commit only when the user asks (conventional commits, no AI references). Release is the release session's call: message `mediagram-spieltisch-16` with the commit and version.

## Success Criteria
- [x] Bot run reaches Final in all three edge scenarios (leaver, all-wrong round, cap).
- [x] `cargo test`, `bun test`, `bun run build` green after the merge.
- [ ] Plan marked shipped with a Review section.

## Out of scope
Sound effects (follow-up like Kopfnuss), shelf poster art, the price-guess game.

## Done (2026-11-09)
- Host run: `bun crates/mediagram-game/tools/smoke.mjs ws://localhost:8093/ws --game dreifelder` → PASS (Cem out round 1 with no pick, round 3 all wrong and nobody out, Dana leaves round 4 and is out at its reveal, Ana and Ben reach the cap, Ana first on speed: 300/200/100/0). A second run with a browser phone as 5th player ended 400/300/200/100/0, Cem above Eve on his ghost guesses.
- Version 0.166.0 in all three manifests; changelog entry written.
- Not done: TV box / tablet run (needs the user's go-ahead), commit (user's call), release message to the release session.
