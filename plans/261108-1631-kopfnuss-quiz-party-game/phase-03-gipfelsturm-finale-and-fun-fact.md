---
phase: 3
title: "Gipfelsturm finale and fun fact"
status: completed
priority: P1
effort: "3h"
dependencies: [1]
---

# Phase 03: Gipfelsturm finale and fun fact

## Overview
A rapid-fire race up 10 steps. Each phone runs its own stream of the same question
sequence. The winner gets a fun fact. Pure host logic in its own module; phase 02
calls `Climb::new` once the last round ends.

## Requirements
- **Head start by rank** (competition ranking via `quiz_views::rank_of`): 1st → 3, 2nd → 2, 3rd → 1, the rest 0. Tied players share a rank.
- **One shared deck** of up to 30 questions from the supply (any category), so everyone sees the same questions in the same order. Each player has their own index into it.
- **Phone:** `Choice { prompt: question text, options, opens_at, closes_at: None, tricks: [] }`.
  - Correct → +1 step, next question with `opens_at = now`.
  - Wrong → next question with `opens_at = now + 2000` (the buttons show "Gleich … 2": the lock).
- **Win:** the first player to reach step 10 by host arrival order. If nobody does after 90 s, the highest step wins; a tie goes to whoever reached that step first. A drained deck counts as the cap.
- **Then the Fact stage (12 s):** `pick_fact`; the winner's phone shows an `Outcome` with the fact, the TV shows the fact; then `is_finished()`.
- **Config:** steps 10, head_start [3,2,1], lock_ms 2000, climb_ms 90000, fact_ms 12000.

## Architecture
- `src/kopfnuss_climb.rs`: `Climb { deck, pos: BTreeMap<PlayerId,(step, idx, opens_at, reached_at)>, ends_at, winner }` with `on_choose`, `on_tick`, `phone`, `scene`.
- `KopfnussScene.stage`: `Climb{steps, seats:Vec<{player, step}>, ends_at}`, `Fact{winner, text}`.

## Related Code Files
- Create: `src/kopfnuss_climb.rs`, `src/kopfnuss_climb_tests.rs`
- Modify: `src/kopfnuss.rs` (hand-off, Fact stage), `src/protocol_kopfnuss.rs`, `src/kopfnuss_views.rs`

## Tests first
1. Head start: scores 900/900/500/0 → steps 3/3/1/0.
2. Correct answer moves up one step and serves the next question at once.
3. Wrong answer: a tap before `opens_at` (lock) is ignored; after it, accepted.
4. Everyone sees the same question at the same index.
5. First to step 10 wins, and the game moves to Fact.
6. At the 90 s cap the highest step wins; a tie goes to the earlier `reached_at`.
7. A drained deck behaves like the cap.
8. The Fact stage ends the game after `fact_ms`.

## Implementation Steps
1. Tests (red), then `Climb` (green), then hand-off and Fact in `kopfnuss.rs`.

## Success Criteria
- [ ] Tests green; deterministic with a seed

## Risk Assessment
- The phone keys `ChoiceScreen` by `opens_at`, so two questions with the same `opens_at` would freeze it. Correct answers use the tap's `ctx.now`, which strictly increases per player; test 2 covers this.
- An early tap within tolerance on a locked question → the host ignores it (no buzz), so the player taps again.
