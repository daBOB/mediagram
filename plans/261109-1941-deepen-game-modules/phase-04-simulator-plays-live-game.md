# Phase 4 — The simulator plays the live game

Priority: high. Status: pending. Wave 2 (after phase 3).

## Problem

The simulator (`src/grand_allee/play.rs`, `actions.rs`, `debts.rs`) re-implements
the move order of `GrandAlleeGame`. Drift found: no Doppelt choice (actions.rs:59-63
vs live_choose.rs:233-240); walk timed by the die, not the hops (play.rs:115 vs
live_turn.rs:62); a note after a guest pays (play.rs:121, none live); random free
entrance (actions.rs:72-77, the builder picks live); no `lead_ms`/`chain_ms`.
`PACE` (play.rs:24) copies `die`, `hop`, `note` from `GrandAlleeConfig`; `tap`,
`decide`, `auction` are human think-time assumptions and stay with the simulator.

## Decided

1. `due(&self, ctx: &GameCtx) -> Option<ServerTime>` in live_turn.rs (Grand Allee's
   `house_at` takes `&GameCtx` since the shared grace landed in 0.168.0): the time the
   current stage acts (Ask → `house_at`; Rolled/Guest/Permit → `at + roll_ms + show_ms`;
   Walking → `ends_at`; Sold/Note → `at + note_ms`; Auction → its close; Final → None).
   `tick` fires only when `due <= now`, so `due` and `tick` cannot drift. Add an
   assertion to `whole_game` (live_game_tests.rs:4-21) that the stage never changes
   before `due`.
2. `auto_index` (live_asks.rs:163-188) moves to `Bot::pick(&self, t, seat, ask) -> usize`
   with a `house` flag: `HOUSE` keeps its exact choices (lets a 6 go, cheapest
   entrance, auctions the hotel in question). A playing bot rolls again on a 6 and on
   a Doppelt it cannot pay chooses Raise when its other properties at the bank's
   price cover the shortfall, otherwise auctions the hotel in question.
3. play.rs becomes a driver: on Ask the seat's bot answers with `Action::Choose` at
   `since + think`; on Auction every open bidder bids (`Bot::bid` or pass) at
   `since + think`; otherwise jump `now` to `due` rounded up to the 50 ms tick and call
   `on_tick`. `Pace` becomes `Think { tap, decide, bid }` with today's values
   (1.5 / 6 / 10 s). `play(seed, players, rules) -> Record` keeps its signature;
   every `Record` field is filled by watching the game. Delete actions.rs, debts.rs.
4. Measure before/after (`cargo build --release --bin grand-allee-sim`; today 0.15 s for
   4 000 games). Above 5 s only: run player counts with `std::thread::scope`.
5. New baseline report under `plans/261109-1941-deepen-game-modules/reports/`; re-check
   37–40 min, half-time leader < 80 %, 9 of 10 hotels owned at half time. `Rules`
   unchanged.

## Acceptance

All live tests pass unchanged; `a_simulated_game_is_deterministic_and_ends` passes;
the simulator has no move-order code of its own.
