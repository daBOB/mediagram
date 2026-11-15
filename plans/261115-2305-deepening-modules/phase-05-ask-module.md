# Phase 05 — The Ask module (Spieltisch)

## Problem

Seven turn-based games each keep their own `since` and repeat
`ctx.house_at(actor, since)` (`maumau.rs:148`, `schwimmen.rs:166`,
`durak.rs:166`, `race.rs:128`, `zuendstoff.rs:153-154`, …). Flüchtig
(`fluechtig.rs:24-27`, `Ask { opens_at, step }`) and Grand Allee
(`grand_allee/live_asks.rs:61-66`, `Ask { prompt, hotel, opts }` inside
`Stage::Ask { ask, since, step }`) each have an unrelated `Ask` struct.
The phone's `opens_at`/`closes_at` are derived per game.

## Decided

- The Ask module covers only asks put to one player (glossary: a question to
  everyone is not an ask; group games keep their "every present player
  answered" close — user: keep the behaviour).
- It holds: the seat asked, when the ask opened, the clock if any, and when
  the house decides (the earlier of the clock and Grace).
- What is asked (options) stays with each game; Grand Allee keeps answering
  through `Bot::pick(&Table, seat, &Ask)` with its indexable options.
- Grand Allee's rule that the house answers the rest of a turn at once after one
  clock ran out (`idle`, `live_turn.rs:46-52,80`) stays in Grand Allee.
- The simulator (`grand_allee/play.rs:63-89`) reads `since`/`due()` through the
  Ask in the same commit (ADR 0001: no separate faster move order).
- The phone's `opens_at`/`closes_at` come from the Ask.
- The two game-local `Ask` structs are renamed for what they hold.

## Steps

- [ ] Ask module + its tests (opened, clock, Grace from the later of leaving
      and opening, house due, answered).
- [ ] Turn games, Zündstoff's holder, Race, Flüchtig, Grand Allee use it.
- [ ] Simulator reads through it; default simulator run still passes and takes
      about as long (~0.8 s for 4 000 games).
- [ ] Delete per-game timing tests now covered by the Ask's; rule tests stay.
- [ ] `cargo test -p mediagram-game`; check.sh.
- [ ] Spieltisch patch bump; commit; merge; check.sh.

## Done when

No game computes when the house decides; one module does, tested on its own.
