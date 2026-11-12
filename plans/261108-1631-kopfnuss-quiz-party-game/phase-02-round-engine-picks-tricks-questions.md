---
phase: 2
title: "Round engine: picks, tricks, 3 questions"
status: completed
priority: P1
effort: "5h"
dependencies: [1]
---

# Phase 02: Round engine — picks, tricks, 3 questions

## Overview
`KopfnussGame` runs 7 rounds: pick phase, resolve, a contained 3-question `Quiz`.
All host logic and pure views. No client work.

## Requirements
- **Pick (12 s, ends early once all connected players are done).** Each player taps through: category (1 of 3), then trick (1 of 2, randomly offered from 4, seeded), then target (any other **connected** player; with nobody else connected the trick step is skipped). <!-- Updated: Validation Session 1 - connected targets only --> Each step is a `Choice` whose `opens_at` = the previous tap's `ctx.now`, because the phone keys `ChoiceScreen` by `opens_at` (`PhoneApp.tsx:149`), so equal values would freeze the screen.
- **Resolve (3 s, shown on TV).**
  - Category: majority. On a tie, the vote of the last-placed player (lowest score; if several, the one whose vote is among the tied) decides; otherwise random among the tied. No votes at all → random among the offered.
  - Tricks: applied in pick-completion order. Target already holds 3, or already holds that trick → **bounce**. Record `Landed{from,to,trick}` / `Bounced{…}` for the TV.
- **Questions.** `Quiz::new(QuizConfig{rounds:3, seed, ..}, supply.take(cat), now)`; forward `on_action`/`on_tick`; on `is_finished()` clear all tricks, start the next round or the finale (phase 03).
- **Phone during questions.** The inner quiz's `PhoneScreen`, with this player's `tricks` injected when it is a `Choice`.
- **Config** (`KopfnussConfig`): rounds 7, pick_ms 12000, resolve_ms 3000, max_tricks 3, trick_offer 2, seed.

## Architecture
- `src/kopfnuss.rs`: `KopfnussGame { cfg, supply, rng, round, stage, quiz: Option<Quiz>, tricks: BTreeMap<PlayerId, Vec<Trick>> }`, `impl Game`.
- `src/kopfnuss_picks.rs`: `PickSheet` per player (offered tricks, vote, trick, target, done_at) + `resolve_category()` + `resolve_tricks()` as pure functions.
- `src/kopfnuss_views.rs`: phone and TV views per stage.
- `KopfnussScene.stage`: `Pick{categories, done:Vec<PlayerId>, closes_at}`, `Resolve{category, votes, events}`, `Question{quiz:QuizScene, tricks:Vec<(PlayerId,Vec<Trick>)>}`, plus `Climb`/`Fact` (phase 03).

## Related Code Files
- Create: `src/kopfnuss.rs`, `src/kopfnuss_config.rs`, `src/kopfnuss_picks.rs`, `src/kopfnuss_picks_tests.rs`, `src/kopfnuss_views.rs`, `src/kopfnuss_tests.rs`
- Modify: `src/protocol_kopfnuss.rs`, `src/factory.rs` (real game), `src/lib.rs`
- Read-only: `src/quiz.rs`, `src/quiz_tests.rs` (helper pattern `choose`/`tick`)

## Tests first
`kopfnuss_picks_tests`:
1. majority wins
2. tie → the last-placed player's vote
3. tie where last place voted outside the tie → random among the tied (seeded, deterministic)
4. no votes → one of the offered
5. 4th trick on one phone bounces
6. the same trick twice bounces
7. order = pick-completion time

`kopfnuss_tests` (tick-driven, seeded):
1. A full round reaches the questions after all players pick → ends early.
2. Timeout without picks still proceeds.
3. Tricks appear on the target's `Choice` only, for exactly 3 questions, and are gone in the next pick.
4. Scores accumulate across rounds via the inner Quiz.
5. After 7 rounds → finale stage.
6. A disconnected player does not block the pick phase and is not offered as a target.
7. Pick screens have strictly increasing `opens_at`.

## Implementation Steps
1. Tests (red) → `kopfnuss_picks.rs` pure functions (green).
2. Stage machine in `kopfnuss.rs` with the contained Quiz (green).
3. Views; keep every file under 200 lines (split views if needed).

## Success Criteria
- [ ] All tests above green; `cargo clippy` clean for new files
- [ ] `quiz.rs` unchanged

## Risk Assessment
- Contained Quiz's `Final` phone view could flash for one tick → Kopfnuss swaps it out the same tick it sees `is_finished()`.
- A player with all 3 tricks may not be able to answer in 12 s → tune in phase 06 playtest (config only).
