---
phase: 1
title: "Protocol, settings, lobby"
status: completed
priority: P1
effort: "3h"
dependencies: []
---

# Phase 1: Protocol, settings, lobby

## Overview
Dreifelder exists as a lobby choice that starts, with wire types exported to TS. No rules yet.

## Tests first
- `room_settings_tests.rs`: Dreifelder with 1 connected player is blocked with `DREIFELDER_TOO_FEW`; with 2 it can start; in kids mode blocked with `DREIFELDER_NOT_FOR_KIDS` (mirror the Kopfnuss tests at lines ~191-199).
- Factory test: `GameKind::Dreifelder` builds a game (not the quiz arm).
- `cargo test` regenerates ts-rs types: `DreifelderScene`, `DreifelderStage` appear under `client/src/protocol/`.

## Wire types (`protocol_dreifelder.rs`)
`DreifelderScene { round, rounds, alive: Vec<PlayerId>, out: Vec<OutPlayer{player, round}>, stage }`
`DreifelderStage`: `Question { category, text, options: Vec<String> (3), image, opens_at, closes_at, answered: Vec<PlayerId> }`, `Reveal { question fields, correct: u8, picks: Vec<Pick{player, choice: Option<u8>}>, eliminated: Vec<PlayerId>, ends_at }`, `Final { order: Vec<PlayerId> }`.

## Related Code Files
- Create: `src/protocol_dreifelder.rs`
- Modify: `src/protocol.rs` (TvScene::Dreifelder + re-exports), `src/protocol_settings.rs` (GameKind::Dreifelder), `src/room_settings.rs` (min 2, messages), `src/factory.rs` (arm; shared `used` history like Kopfnuss; draws 3 film questions with `film_questions(..)`, as the Kopfnuss arm draws 9), `src/lib.rs`, `client/src/ui/lobby.ts`, `client/src/phone/PhoneLobby.tsx`, `tools/smoke.mjs` (usage line)
- Note: `ui/lobby.ts` and `PhoneLobby.tsx` changed on spieltisch-games 0.165.0 (Bauherr removed): merge main first.

## Success Criteria
- [x] New tests failed before, pass after; `cargo test` and `bun test` green; `bun run build` clean.
- [x] Lobby shows Dreifelder; start builds a placeholder game without panicking.

<!-- Updated: Validation Session 1 - factory draws 3 film questions -->
## Risks
Merge conflicts in lobby files with 0.165.0: merge main before starting.

## Done (2026-11-09)
- Merged spieltisch-games 0.165.0 first (fast-forward, user's choice), so no Bauherr arm to code around.
- No placeholder game: the factory arm went in with the real game in phase 3, and its test (`the_factory_builds_dreifelder`) lives in `dreifelder_tests.rs`. Less throwaway code.
- `Pick` is `FieldPick` on the wire: a `Pick.ts` would shadow TypeScript's built-in `Pick<T, K>`.
