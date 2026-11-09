---
phase: 2
title: "Elimination rules (pure)"
status: completed
priority: P1
effort: "4h"
dependencies: [1]
---

# Phase 2: Elimination rules (pure)

## Overview
All rules as pure functions with no time and no I/O, so every edge case is a plain unit test.

## Tests first (`dreifelder_rules_tests.rs`)
- Wrong or missing pick eliminates; right pick survives.
- All alive wrong -> nobody eliminated.
- Ghost (already out) picks never change who is alive.
- One alive left -> winner; two alive both wrong -> both stay.
- Cap reached with several alive -> ranked by speed total, then player id.
- Placement: later drop = better place; same-round drops ranked by post-elimination right guesses, then speed, then id.
- No alive player connected -> game over, alive ranked by speed.
- Final scores: 100 per player beaten, strictly ordered like the finishing order.

## Architecture (`dreifelder_rules.rs`)
`Tally { alive, out_round: BTreeMap<PlayerId, usize>, ghost_right: BTreeMap<..>, speed: BTreeMap<..> }`
`fn settle_round(&mut self, round, correct, picks: &[(PlayerId, Option<(u8, i32)>)]) -> Settled { eliminated, winner: Option<PlayerId> }`
`fn finishing_order(&self) -> Vec<PlayerId>`; `fn placement_scores(order) -> Vec<(PlayerId, i32)>`.

## Shared-code changes (also tests first)
- `quiz_config.rs`: `Round::shuffled(q, rng, options)` keeps `shuffled` behaviour for 4 (existing callers pass 4 via config); with 3 it keeps the correct answer plus 2 wrong ones. Test: correct answer always present, index right, 3 options.
- `quiz.rs`: extract the tap clamp into `fn claim_time(now, opens_at, at, max_claim_lag)`; `Quiz::on_action` calls it; existing quiz tests must stay green unchanged.

## Related Code Files
- Create: `src/dreifelder_rules.rs`, `src/dreifelder_rules_tests.rs`
- Modify: `src/quiz_config.rs`, `src/quiz.rs`, `src/kopfnuss_climb.rs` (call sites)

## Success Criteria
- [x] Every rule above has a test seen failing first; Kopfnuss and quiz suites unchanged and green.
- [x] No file over 200 lines.

## Done (2026-11-09)
- `settle_round` takes the picks as a map (only who picked) and returns who went out; the winner follows from `is_over` + `finishing_order`, so no `Settled` struct.
- Tap clamp and speed points became `QuizConfig::claim_time` / `speed_points` in `quiz_config.rs` (both games hold a `QuizConfig`), not a free fn in `quiz.rs`.
- `Round::shuffled` drops the first wrong answers after the usual shuffle, so the RNG sequence for 4 options is unchanged (seeded quiz tests untouched and green).
