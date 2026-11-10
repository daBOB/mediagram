# The Grand Allee simulator plays the live game, not a fast copy of it

The rule simulator (`grand-allee-sim`) drives the live `GrandAlleeGame` through
the `Game` trait, with a bot in every seat and the clock jumping from one
deadline to the next, instead of keeping its own, faster move order. The copy
had drifted from the live game within days (no Doppelt choice, walks timed by
the die, entrances placed with no decision, missing delays), and it under-timed
a game by 5 minutes; every rule change had to be made twice. A default run of
4 000 games now takes about 0.8 s instead of 0.1 s, which is fine for tuning
(decided 2026-11-09, 0.170.0).

## Consequences

- Do not reintroduce a separate, faster simulator move order for speed. If runs
  get too slow, run the player counts in parallel threads first.
- When the house decides is separate from what playing bots choose: the house
  plays cautiously for absent players (lets an extra turn go, takes the
  cheapest entrance, auctions the hotel in question), and simulated bots play
  to win. Both answer through `Bot::pick`.
