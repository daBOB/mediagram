---
phase: 7
title: "Verification and release"
status: pending
priority: P1
effort: "3h"
dependencies: [2, 6]
---

# Phase 7: Verification and release

## Steps
1. `scripts/check.sh` (clippy, cargo test, protocol committed, bindings committed, typecheck, build) green.
2. [x] A real fetch on the PC (phase 2) into the funstation's cache server; `curl` shows the list and a photo.
   Done 2026-11-12: cache server 0.177.0 installed on the funstation (the old binary kept as
   `/usr/local/bin/mediagram_cache.0.163.0`, chunks and artwork intact), 574 articles and their photos read back.
3. funstation: Spieltisch and the media app from this branch; a night with three phones on the LAN plays a full
   Preisfrage to the ceremony; screenshots of each stage kept in `reports/`.
4. Edge checks there: kids lock → blocked with its reason; cache server stopped → blocked with its reason, other
   games still start; a phone that leaves mid-round does not stall it.
5. Versions: Spieltisch minor and media minor at the next free numbers when merging (ferry holds Spieltisch
   0.176.0); `docs/project-changelog.md` entries for both lines; `CONTEXT.md` glossary: Preisfrage, article list.
6. Game brief in spieltisch (`design/game-brief.md`): Preisfrage added to the games table.
7. Merge into `spieltisch-games`, then `main` on the user's word. The TV box run waits for the open test night,
   like the other games.

## Success Criteria
- [ ] All of the above, with the evidence in `reports/`.
- [ ] Plan marked shipped with a Review section.
