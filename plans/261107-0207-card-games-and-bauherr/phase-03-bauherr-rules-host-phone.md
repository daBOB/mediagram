# Phase 03 — Bauherr: rules, host and phone

**Status:** done, merged 10:18; review fixes 10:36. The TV board is phase 05.
Context: `../261106-1830-board-games/plan.md` (item 3, the graph-board decision,
"things to avoid"), Heimlauf as the pattern (`src/race*.rs`, `protocol_race.rs`).

## The game (own rules in Hotel's spirit; numbers are mine, tune by playing)

2–4 players, lobby gate like Heimlauf. Currency **Taler**, start 12 000.

**Board** — a closed path of ~36 fields winding through a town, 8 hotel plots
beside it. Each plot borders 2–4 path fields. Board is **data** (tiles with id,
kind, position, exits; plots with their bordering fields), not geometry, per the
board-games plan; the scene carries it so the TV has one source of truth.

Field kinds:
- **Bank** (start): passing or landing pays 2 000.
- **Rathaus** (×1): build on any own hotel (permit roll) and/or buy entrances.
- **Bauplatz**: buy a bordering free plot at its land price.
- **Baustelle**: build on a bordering own hotel (permit roll).
- **Freier Eingang** (×2): place one entrance for free at an own built hotel.
- Plain fields otherwise.

**Hotels** — 8 own invented names (e.g. Seeblick, Alpenrose, Stadtpalais,
Kurhaus, Leuchtturm, Waldhaus, Sternwarte, Kastanienhof; never Hotel's names).
Each has a land price (1 000–3 500), stages built in order — main building, 1–4
annexes, then a leisure facility (pool, garden, …) — each with a cost, an entrance
price, and a nightly rate per completed stage. A fully built hotel costs 3 000–
12 000 and charges 300–1 500 a night. Cheap hotels pay back fast, dear ones big.

**A turn** — roll the die (1–6), walk, then the field's action on the phone:
buy / build / entrance / nothing. Building: roll the **permit die** — 3× genehmigt,
1× abgelehnt, 1× gratis (next stage free), 1× doppelt (pay double) — then pick
how far to build (next stage … full, the options you can afford). Entrances sit on
path fields bordering the hotel, one per field, only at a hotel with ≥ 1 stage; the
TV marks candidate fields in the answer colours (like Heimlauf's move marks).

**Guests** — landing on a field with another player's entrance: roll the die for
nights; pay nights × that hotel's current rate to its owner. Can't pay → the host
sells your property back to the bank automatically at half price (top stages
first, then plots) until paid; still short → you pay what you have and are out.

**End** — a round limit from a turn budget of ~96 turns (2p 48 rounds, 3p 32, 4p
24; constant, tune after a timed play), or earlier when one player is left. Richest
by net worth wins: cash + land prices + stage costs. `Player.score` = net worth.
TV shows "Runde n / N".

Absent player plays itself after `auto_ms` (Heimlauf's `absent_since`): buys/builds
when it keeps ≥ 2 000 in reserve, takes the cheapest entrance field, else passes.

## Wire and phone

- `protocol_bauherr.rs`: `TvScene::Bauherr { scene }` — board data, owners,
  stages, entrances, pawns, cash, stage of the turn with server times (roll in
  the air, walking with `hop_ms`, permit die, guest payment), round n/N, ranking.
- Phone: a **generic sheet screen** that later money games can reuse — header
  lines (Taler, my hotels with stage), prompt, options, `opens_at`, `picked`;
  options answer with `Action::Choose { index }`. Shown also when it is not my
  turn (who is on, my status).
- `GameKind::Bauherr`, factory branch, gate 2–4 in `room_settings.rs`, lobby
  entry + badge.
- The TV keeps working: until phase 05, `TvScene::Bauherr` may render a plain
  2D top-down canvas board in `TvBauherr.tsx` good enough to follow a game in a
  desktop browser. Phase 05 replaces it with the chosen 3D look.

## Todo

- [ ] board data + rules (pure) + tests: walk + bank pass, buy, permit die
      outcomes, stage order, entrance placement limits, guest payment, forced
      sale, bankruptcy, round limit, net worth
- [ ] turn flow (`impl Game`), absent auto-play, views + tests
- [ ] protocol + generated TS committed; GameKind/factory/gate/lobby
- [ ] generic phone sheet screen
- [ ] plain TV board (temporary until phase 05)
- [ ] scripted phones play a 2- and a 4-player game to the end against `game-night-dev`;
      log the average turn length so the turn budget can be tuned

## Success criteria

clippy, cargo test, client typecheck/test/build green; a full game played by
scripted phones; desktop screenshots of the phone sheet; average turn length reported.
