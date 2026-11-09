---
phase: 3
title: "Game state machine"
status: completed
priority: P1
effort: "5h"
dependencies: [2]
---

# Phase 3: Game state machine

## Overview
`DreifelderGame` implements `Game`: Question (intro then open) -> Reveal -> next round or Final.

## Tests first (`dreifelder_tests.rs`, deterministic via `cfg.seed`, time from `ctx.now`)
- Buttons not live before `opens_at`; taps up to 150 ms early accepted; tap clamp as in the quiz.
- A round ends when every *alive connected* player answered, or at `closes_at`; an out player who has not answered does not hold it up.
- Out players' answers are recorded for ghost scoring but add no points and never change `alive`.
- Reveal lists picks and who is out; after `reveal_ms` the next round starts or Final.
- Game ends with one alive; at 12 questions with several alive; with no alive connected.
- Alive player disconnects mid-round -> out at reveal; reconnecting does not bring them back.
- Final: `player.score` set to placement scores; `order` holds the finishing order.
- Question set is 12 questions, at most 3 of them films; none repeats within a game and not across games tonight (shared `used` history).

## Architecture
`dreifelder.rs` (state, `Game` impl, ~200 lines max) + `dreifelder_config.rs` (rounds 12, options 3, intro 3000, answer 12000, reveal 4000, points 1000->500 for the speed tie-break) + `dreifelder_views.rs` (phase 4). Questions: 9 general from `QuestionSupply::deck(9)` plus the 3 film questions the factory drew (`take(FILMS, 3)`), shuffled together, one `Round::shuffled(q, rng, 3)` each. With no film questions the deck is `deck(12)`.

## Related Code Files
- Create: `src/dreifelder.rs`, `src/dreifelder_config.rs`, `src/dreifelder_tests.rs`
- Modify: `src/factory.rs` (real arm replaces phase 1 placeholder), `src/kopfnuss_supply.rs` only if a mixed 9+3 draw needs a small helper (`deck()` is general-only; `take(FILMS, n)` gives films)

## Success Criteria
- [x] All tests seen failing first, then green; full `cargo test` green.
- [x] A scripted 3-bot game on the host reaches Final with correct placement.

<!-- Updated: Validation Session 1 - 9 general + 3 film -->
## Risks
- Timer/phone races as in Kopfnuss (opens_at keyed screens, 400 ms next-screen gap after a tap): reuse `next_gap_ms` idea, covered by a test that a double tap never answers the next question.

## Done (2026-11-09)
- One `Question` stage (shown at once, live from `opens_at`) instead of Intro/Open: the client tells them apart by time.
- Question mix in `dreifelder_config::dreifelder_questions` (films first, general for the rest, shuffled); the game takes a plain `Vec<Question>` like `Quiz`.
- Players not seated at the start are ignored and see a wait screen.
