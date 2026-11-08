# Phase 02 — rules engine and simulation

**Goal:** the Grand Allee rules as a pure, tested Rust engine, and a simulator
that plays thousands of bot games to check and tune the numbers before any
art is made.

## Scope

- `crates/mediagram-game/src/grand_allee/`: board data, rules (pure state and
  operations, seeded dice), bots (three personalities: cautious, balanced,
  bold), a game driver with a time model taken from the live Bauherr timings
  (die 1.15 s + 0.9 s shown, 0.28 s a hop, 1.4 s a note) plus 6 s per human
  decision and 10 s per auction.
- `src/bin/grand-allee-sim.rs`: N games per player count (2, 4, 6, 8),
  deterministic seeds, prints the acceptance metrics.
- Not in scope: protocol, room, TV, phone. The engine is not wired into the
  live game yet.

## Acceptance (revised with the user after the first runs)

1. Median length 35–45 min at 4, 6, 8 players; ~25 min at 2 (bankruptcy). Met.
2. ≥ 9 of 10 hotels owned at half time on average, from 4 players up. Met.
3. Half-time leader wins ≤ 80 % (runaway accepted as in the classic). Met.
4. No player waits > 2 min between own decisions (p95 of the per-game
   maximum). Not met at 8 players (137 s); open until the test night.

## Todo

- [x] board data + tests (40 fields, counts, every hotel touches a Grundstück and a Baustelle)
- [x] rules + tests (walk, skip occupied, bank bonus and two-left rule, Rathaus, buy, forced buy, permit prices, first entrance, leisure wing, rent, debt auction, bank fallback, elimination, end)
- [x] bots and driver
- [x] simulator binary and metrics
- [x] tune and record results in a report under `plans/reports/`
