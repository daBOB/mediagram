# Phase 2 — Balance with bots

Status: done (2026-11-14) · Depends on phase 1

## Context
Template: `crates/mediagram-game/src/bin/grand-allee-sim.rs`, `src/grand_allee/bots.rs`. Standing rule: clones keep
the original's balance, measured with bots, not assumed.

## Steps
- [x] `src/lebensweg/bots.rs`: simple bots (fork picks random / always safe / always risky; college vs career).
- [x] `bin/lebensweg-sim.rs`: N games on the original board (`MORE` 1.0, original money) and on ours (1.3).
- [x] Measure per board: turns per game and minutes at the TV's pace, win share per start path and per fork,
      money spread at the end, how often a player ends in debt.
- [x] Tune the money on our board until its shares match the original's within a few points; record the table.
- [x] Report: `reports/lebensweg-balance-original-vs-ours-report.md`.

## Outcome
No money tuning was needed: the gaps came from which spaces the stretch doubled (`stretch.rs`), fixed by
keeping every kind at 1.3 × along the board. All routes within ±4 points, careers within ±7, ×1.2 game length;
see the report. Open: spinner gap 5 % or 2 % (IT-Beratung 1.34 vs 1.06).

## Success
Our board's win shares per choice match the original's (± 3 points over ≥ 2,000 games); game length known.
