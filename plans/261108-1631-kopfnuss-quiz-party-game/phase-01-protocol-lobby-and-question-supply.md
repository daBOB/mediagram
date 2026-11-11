---
phase: 1
title: "Protocol, lobby and question supply"
status: completed
priority: P1
effort: "3h"
dependencies: []
---

# Phase 01: Protocol, lobby and question supply

## Overview
Kopfnuss exists as a game kind, the lobby can start or block it, and a
question supply hands out 3 questions per category without repeats tonight. Facts
file and loader.

## Requirements
<!-- Updated: Validation Session 1 - Quiz has no minimum; Kopfnuss gets its own constant -->
- `GameKind::Kopfnuss`; lobby: new `KOPFNUSS_MIN = 2` (+ too-few message) in `room_settings.rs`, max = room max; blocked when `kids_only`.
- `Trick` enum: `Wechselbad`, `Schneckenpost`, `Spiegel`, `Kleber` (ts-rs export).
- `PhoneScreen::Choice { …, tricks: Vec<Trick> }`; all existing constructors pass `vec![]`.
- Supply: buckets = 12 general categories + "Eure Filme" (film questions, any film kind).
  `offer(rng, 3)` → 3 distinct categories with ≥3 unused; `take(category)` → 3 questions, marks used.
- Fewer than 3 offerable categories → offer what is there (≥1); 0 → game ends early to finale.
- `questions/fakten.json`: ~60 `{category, text}` German facts, `include_str!` like `general.rs:35`.

## Architecture
- `src/protocol_kopfnuss.rs`: `Trick`, `KopfnussScene` (stage enum filled in 02/03; here: skeleton).
- `src/kopfnuss_supply.rs`: `QuestionSupply { buckets: BTreeMap<String, Vec<Question>> }`, built in
  `factory.rs` from `general` + `film_questions(pool, eligible, rng, n, used)`; honors the night's `used` set.
- `src/kopfnuss_facts.rs`: `fact_pack() -> Vec<Fact>`, `pick_fact(rng, category_hint)`.

## Related Code Files
- Create: `src/protocol_kopfnuss.rs`, `src/kopfnuss_supply.rs`, `src/kopfnuss_supply_tests.rs`, `src/kopfnuss_facts.rs`, `questions/fakten.json`
- Modify: `src/protocol_settings.rs` (GameKind), `src/protocol.rs` (Choice.tricks, TvScene::Kopfnuss, re-exports),
  `src/quiz_views.rs`, `src/poster_views.rs`, `src/race_views.rs` (+ their tests: `tricks: vec![]`),
  `src/room_settings.rs` (+ `_tests.rs`), `src/factory.rs`, `src/lib.rs`, client lobby: `client/src/ui/lobby.ts` (name, seats) + `client/src/phone/PhoneLobby.tsx:80` (game buttons), generated `client/src/protocol/*.ts`
  <!-- Updated: Validation Session 1 - lobby buttons live in PhoneLobby.tsx -->
- Note: test files only pattern-match `PhoneScreen::Choice { .. }`; constructors needing `tricks` are the 4 in `quiz_views.rs:33`, `poster_views.rs:26`, `race_views.rs:70,82`.

## Tests first
1. `room_settings_tests`: Kopfnuss blocked with 1 player; blocked when kids_only; allowed with 2.
2. `kopfnuss_supply_tests`: offer gives 3 distinct categories each with ≥3 left; `take` returns 3 and never repeats tonight; a drained category is no longer offered; film questions land in "Eure Filme"; empty supply → `offer` empty.
3. Fact pack parses, ≥50 entries, no empty text.

## Implementation Steps
0. Create worktree: `git worktree add ../mediagram-kopfnuss -b feat/kopfnuss spieltisch-games` (from `mediagram-games/`); all work happens there. <!-- Updated: Validation Session 1 - own worktree -->
1. Write tests above (red).
2. Add `GameKind::Kopfnuss`, `Trick`, `Choice.tricks`; fix constructors; regenerate TS (`cargo test` exports ts-rs).
3. Lobby block rules + lobby list entry ("Kopfnuss").
4. `QuestionSupply` + factory arm building a placeholder game (real one in 02).
5. Write `fakten.json` (user reviews text before ship).

## Success Criteria
- [ ] Tests green; `bun run typecheck` green after TS regen
- [ ] Other games unchanged (their test suites green)

## Risk Assessment
- GameKind/protocol edits collide with Grand Allee session → one-line arms, rebase early.
- Film questions per night limited by library size → supply just offers fewer film rounds.
