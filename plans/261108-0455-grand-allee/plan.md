# Grand Allee — rebuilding the hotel game from proof of concept

The design (rules, board, hotels, prices, acceptance criteria) is approved and
lives in the spieltisch project: `spieltisch/design/grand-allee-design.md`
(2026-11-08). It replaces Bauherr's rules: 2–8 players, a 40-field oval
boulevard with ten hotels on both sides, forced purchase of unbuilt plots,
debt auctions, Rathaus entrances on passing, a 6 rolls again, round limit with
net worth, Bank stops paying with two players left.

## Phases

| # | Phase | Status |
|---|---|---|
| 01 | Rules and board design | done (spieltisch `design/grand-allee-design.md`) |
| 02 | [Rules engine and simulation](phase-02-rules-engine-and-simulation.md) | done: engine + 11 tests, simulator, tuned (rates halved, 280 shared turns); 8-player wait open for the test night |
| 03 | [3D models](phase-03-3d-models.md) — hotels with stages, dice, in Blender | done: ten themed hotels, baked GLBs in `client/public/grand-allee/` |
| 04 | [TV board and live game](phase-04-tv-board-and-live-game.md) | done: 0.160.0–0.165.0, Bauherr retired |
| 05 | [Phone controls](phase-05-phone-controls.md) — clock, bid control, hotel cards, Doppelt route | done: 0.166.0 |
| 06 | [Onboarding intro, test night, re-timing](phase-06-onboarding-test-night-retiming.md) | 6a done (0.170.2–0.172.0); test night open |

The live Bauherr game stays as it is until phase 04/05 wire the new engine in.
