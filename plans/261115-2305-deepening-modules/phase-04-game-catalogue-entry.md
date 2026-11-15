# Phase 04 — One catalogue entry per game (Spieltisch)

## Problem

A game's facts live in separate matches on `GameKind`: `catalogue.rs` `entry()`
(name, min, max `:50-77`), `timed()` (`:82-95`), `sized()` (`:100-113`);
`factory.rs:65-145` (construction); `room_settings.rs` `blocked()` (kids,
material, board, with a `_ => {}` wildcard, `:102-116`); `host.rs:86-90`
Readiness. Adding Zündstoff touched nine places (c98046b3); several are lists
the compiler does not check: the lobby order `CATALOGUE` (`catalogue.rs:30-46`),
`catalogue_tests.rs:6,10`, the TV's ternary chain (`TvApp.tsx:37-61`, falling
through to TvQuiz), `blocked()`'s wildcard.

## Decided

- Each game declares one catalogue entry beside its own code: name, seat range,
  timed, sized, kids rule, needs the board, readiness need, start.
- One exhaustive match maps `GameKind` → entry (compiler catches a missing game).
- `blocked()` reads kids/board/readiness from the entry; no wildcard.
- The lobby order stays an explicit list; a test asserts every game appears
  exactly once (replaces the hand list in `catalogue_tests.rs`).
- The TV picks its scene through an exhaustive map keyed by scene kind, so
  TypeScript catches a missing game; no fall-through to TvQuiz.
- Blurbs and groups stay in the TV client (`shelf-games.ts`): wording, ADR 0002.
- Lobby/phone protocol (`games: CATALOGUE`, one `blocked` message for the
  chosen game) does not change.

## Steps

- [ ] Entry type; per-game entries; exhaustive kind → entry.
- [ ] Factory, `blocked()`, Readiness read the entries.
- [ ] Lobby-order test; delete the hand-kept test list.
- [ ] `TvApp` exhaustive scene map.
- [ ] `cargo test -p mediagram-game`; client `bun test` + typecheck
      (as `scripts/check.sh` runs them).
- [ ] Spieltisch patch bump; commit; merge; check.sh.

## Done when

Adding a game means its own entry plus its TV scene; nothing else compiles
without them.
