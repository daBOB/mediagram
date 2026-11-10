# Phase 5 — Typed move stages

Priority: medium. Status: pending. Wave 3 (after phase 4).

## Problem

`GrandAlleeGame` (live.rs:117-143) holds the move in progress in loose fields:
`step` (live_turn.rs:59,73,79,89), `roll` (:59,135), `rathaus` (:59), `served`
(live_turn.rs:60, live_choose.rs:52), `debt` (live_debts.rs:15,32), `building`
(live_choose.rs:62, live_debts.rs:33), `idle` (live.rs:178, live_turn.rs:42,135).
`proceed` is entered from live_turn.rs:46, live_choose.rs:67,:76,
live_debts.rs:25,:42, and via `settle`. `step` is redundant (each caller knows the
next step). `building` without the matching bank `debt` can be written. Test helper
`place` (live_tests.rs:60-65) sets `turn` without resetting `roll`/`idle`;
`g.rounds = 1` (live_debt_tests.rs:134) writes a field.

## Decided (design C, no `Decide` seam)

1. `begin_move(seat, now)` pulled out of `next_turn` (live_turn.rs:135-137) and `new`
   (live.rs:154-155); `place` calls it; tests get one round via `Rules` in the config.
2. `enum Debt { Rent { owner, amount }, Permit { hotel, count, price } }` replaces
   `debt` + `building`.
3. `rathaus` moves into `Stage::Walking`; `roll` becomes `six: bool`.
4. The step travels in the stage (`Stage::Ask { .., step }`, `owing: Option<Debt>` on
   Auction and Sold); `proceed` becomes named continuations (after the walk, to the
   guest, to the field). Keep the exact `now`/`later` (`chain_ms`) timing —
   live_tap_tests.rs:24 depends on it.
5. Test: after a clock runs out, the house answers a 6's extra roll at once (idle
   carries through the move).

## Since this spec was written (0.167–0.170)

- `Stage::Note` carries `broke`; the went-broke note is set in live_debts.rs.
- `house_at(&self, ctx: &GameCtx, since)`; `auto_index` became `Bot::pick` (bots.rs).
- `due(&self, ctx)` in live_turn.rs is the single source of when a stage acts;
  `tick` returns early unless `due <= now`. Keep it that way: every new stage shape
  gets its arm in `due`, never a guard in `tick`.
- The simulator (play.rs) drives the live game and matches `Stage::Ask` and
  `Stage::Auction`; keep it compiling.
- Line numbers above are from 0.166.0; trust the code.

## Acceptance

No behaviour change; impossible combinations of step and debt no longer compile;
all tests green; live.rs and every touched file under 200 lines. The simulator's
default run (`cargo run --release -p mediagram-game --bin grand-allee-sim`) prints
exactly the same tables before and after: it plays 4 000 seeded games through the
live game, so any difference is a behaviour change.
