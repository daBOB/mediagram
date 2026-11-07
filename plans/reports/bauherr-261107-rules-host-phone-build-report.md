# Bauherr: rules, host and phone — build report

Branch `feat/bauherr` (from `feat/spieltisch`), two commits, nothing pushed, no version bump, no changelog.

## Built

Rust (`crates/mediagram-game/src/`), one concern per file:

| file | what |
|---|---|
| `bauherr_board.rs` | the board as data: 36-field ring, 8 plots, price table |
| `bauherr_rules.rs` | `Table`: walk + bank pass, buy, stages and permit prices, entrances, guests, forced sale, bankruptcy, net worth, standings, round limit |
| `bauherr_asks.rs` | what a field offers as a list of options (`Opt`, `Ask`), absent auto-pick |
| `bauherr.rs` / `bauherr_turn.rs` | config, `Stage`, `impl Game`; turn flow and timers |
| `bauherr_views.rs` | phone sheet + `TvScene::Bauherr` |
| `protocol_bauherr.rs` | wire types (TS generated and committed) |

Also: `GameKind::Bauherr`, factory branch, lobby gate 2-4 (`room_settings.rs`, shared constants with Heimlauf), `PhoneScreen::Sheet` + `SheetLine`.

Client: `phone/PhoneSheet.tsx` (generic sheet), `tv/TvBauherr.tsx` + `tv/bauherr/bauherr-draw.ts` + `bauherr-status.ts` (plain 2D board, temporary), lobby entries (`ui/lobby.ts` `GAME_NAMES`), CSS in `styles.css`.

## Board

Closed ring of 36 fields = border of a 10x10 grid, clockwise from top left (field 0 = Bank). Tiles carry `id, kind, x, y, exits`; the pawn follows `exits[0]`, so a fork is a data change. Eight plots inside the ring (4 corner 2x2, 4 side 4x2 or 2x4, a free park in the middle). Each ring field borders at most one plot (tested), so an entrance field belongs to exactly one hotel.

Field kinds by id (B Bank, R Rathaus, P Bauplatz, S Baustelle, F Freier Eingang, . plain):
`BP.P.S..SF.PP.S..SR.PP.S..SF.PP.S..S` (Bank 0, Freier Eingang 9 and 27, Rathaus 18; every plot has one Bauplatz and one Baustelle field).

## Price table (Taler; start cash 12.000, bank pass 2.000)

| plot | hotel | land | entrance | stages (cost) | nightly rate after each stage |
|---|---|---|---|---|---|
| 0 | Kastanienhof | 1.000 | 300 | 1200, 900, 900 (Biergarten) | 150 300 450 |
| 1 | Seeblick | 2.000 | 500 | 2000, 1500, 1500, 1200 (Seeterrasse) | 200 400 650 850 |
| 2 | Waldhaus | 1.300 | 350 | 1400, 1000, 1000, 800 (Saunahaus) | 150 300 450 600 |
| 3 | Stadtpalais | 3.500 | 800 | 2800, 2200, 2200, 1800, 1600, 1400 (Dachgarten) | 300 600 850 1100 1300 1500 |
| 4 | Alpenrose | 1.800 | 450 | 1800, 1200, 1200, 1000 (Wellnessgarten) | 200 400 600 750 |
| 5 | Sternwarte | 3.000 | 700 | 2800, 2000, 2000, 1800, 1600, 1400 (Planetarium) | 250 550 850 1150 1350 1500 |
| 6 | Kurhaus | 2.300 | 550 | 2200, 1600, 1600, 1400, 1200 (Thermalbad) | 250 500 750 1000 1150 |
| 7 | Leuchtturm | 2.600 | 600 | 2400, 1800, 1800, 1600, 1400 (Aussichtsdeck) | 250 500 800 1050 1300 |

Fully built 3.000-12.000, full rate 450-1.500 (asserted in a test). Stage 1 is "Hauptgebäude", the last one the leisure facility, between them "Anbau" / "n. Anbau".

## Rules as implemented (decisions in italics where the plan left room)

- Turn: roll, walk (bank entered, even to stop, pays 2.000), guest payment first if the field has another player's entrance, then the field's action. No extra turns.
- Bauplatz: buy the bordering free plot if affordable. Baustelle: build on the bordering own hotel if the next stage is affordable. Rathaus: menu build (any own hotel) / buy entrance (any own built hotel). Freier Eingang: gratis entrance at an own built hotel.
- Permit die: 1-3 genehmigt, 4 abgelehnt, 5 gratis, 6 doppelt. Then "how far": 1..remaining stages that the player can pay after the permit price. *Gratis = the first stage of the chosen run is free, the rest normal. Doppelt = the whole run costs double.*
- Entrance: built hotel only, free bordering field, one per field; paid ones cost the hotel's entrance price (to the bank). *Entrances vanish with the last stage when sold back.*
- Guest: nights die x current rate to the owner, no entrance owner pays himself. Short: sell to the bank at half price, top stage of the dearest top stage first, then plots, dearest land first; still short: pay what is left, out. *Out players keep nothing, are skipped, the game ends at once when one is left.*
- End: `TURN_BUDGET` 96 / players rounds (48/32/24), a round = every seat took one turn; richest net worth wins; `Player.score` = net worth, synced after every action and tick.
- Absent player: rolls and answers after `auto_ms` 1,5 s: buys/builds while keeping 2.000, else cheapest entrance, else skips.
- *A tap whose client time is earlier than the question's `opens_at` (minus 150 ms) is dropped; the phone also locks buttons 700 ms after a tap. Both stop a double tap from answering the next question.*

## Wire types

`BauherrScene { board, estates, entrances, seats, round, rounds, turn }`; `turn` is `BauherrTurn` tagged by `stage`:
`roll`, `rolled{value,rolled_at,lands_at}`, `walking{from,to,started_at,hop_ms,bonus}`, `guest{owner,plot,nights,amount,rolled_at,lands_at,paid_at}`, `permit{plot,outcome,rolled_at,lands_at}`, `ask{prompt,plot,marks[{field,color}]}`, `note{text,started_at}`, `final{winner,ranking}`.
Seats show the state after the move (like Heimlauf): `field` is already the walk's end, cash changes at `paid_at` for guests.
Phone: `PhoneScreen::Sheet { lines[{label,value}], prompt, options[{label,color}], opens_at, picked }`, answered with `Action::Choose { index, at }`. Shown to every seated player all game; empty `options` = informing only. The end uses the existing `Final`.

## Verification

- `cargo clippy -p mediagram-game --all-targets -- -D warnings` clean; `cargo test -p mediagram-game` 165 tests green (rules, flow, gate).
- Client `bun run typecheck`, `bun test` (12), `bun run build` green.

## Scripted full games against `game-night-dev` (port 8092)

Scripted phones (random-ish: 75 % first option, 0,5-1,4 s think time) played both games to the end, no protocol errors, restart via `back_to_lobby` worked:

| game | turns | wall time | avg turn | median | p90 | buys / builds / entrances | final worth |
|---|---|---|---|---|---|---|---|
| 2 players (48 rounds) | 96 | 548 s | **5,7 s** | 4,9 s | 9,6 s | 7 / 9 / 3 | 18.800 / 17.400 |
| 4 players (24 rounds) | 96 | 507 s | **5,3 s** | 4,8 s | 7,4 s | 8 / 5 / 3 | 16.900 / 15.500 / 14.100 / 13.800 |

Turn-budget tuning: the 5-6 s is the mechanical floor (die 1,15 s + 0,9 s show + walk ~1 s + notes + bot think). Real players think longer: at ~12-18 s per turn the 96-turn budget is ~20-30 min, short of the 45-60 min target; a budget of ~160-200 turns (`TURN_BUDGET` in `bauherr_rules.rs`, 2p 80-100 rounds) would fit. Not changed, as the user fixed ~96; re-time with humans.
Economy observation (random bots, so a lower bound on skill): nobody went bankrupt in either game and the worth spread stayed within ~25 %. Bankruptcy and the ending-by-last-player path are unit-tested but untouched by the bots; if real games feel flat, raise nightly rates or lower start cash before touching anything else.

Screenshots (desktop browser, 412x915 phone and 1920x1080 TV): `plans/reports/bauherr-261107-screenshots/` (`phone-sheet-buy`, `phone-sheet-build`, `phone-sheet-watching`, `phone-lobby`, `tv-plain-board`).

## Notes for whoever continues

- Another session runs `game-night-dev` and kills it by name; my host died twice mid-run until I ran a copy of the binary under another name from the scratchpad. Scripts live in the scratchpad, not the repo.
- The plain TV board draws on every animation frame via React (`useServerNow`), fine on a desktop, not meant for the box.

## What the 3D board (phase 05) needs from this

- Everything is in `BauherrScene`: `board` (tile kinds, grid x/y, exits; plots with footprint, bordering fields, levels with names/costs/rates), `estates` (owner, levels built), `entrances` (field -> plot), `seats` (field, cash, worth, out), `round/rounds`, `turn`.
- Animation cues come from server times in `turn`: die flight `rolled_at..lands_at` (nights and permit dice use the same two fields), walk `started_at` + `hop_ms` per field from `from` to `to` (follow `exits[0]`), guest payment at `paid_at`. State is already final when an animation starts (like Heimlauf), so a renderer should derive "before" from the turn: a pawn at `from` until hops pass, cash and levels change at the stage's end.
- Missing on purpose, add when the look needs it: a build event (which stage just went up), a sale event for forced sales (the seat just loses stages; the TV can diff `estates` between scenes), coin/guest effects (derive from `guest` and `bonus`). `note.text` is German prose for the news ticker, not for logic.
- Field names for the phone are "Feld n" (id + 1); the TV must print the same number on candidate fields (the plain board does, small, top left of each tile) next to the coloured mark from `ask.marks`.
- The awards screen is the shared `TvFinal`, ranked by `Player.score` (net worth). It captions values as points; a Taler caption would need a `TvFinal` option (not done, the ceremony is approved and untouched).
- Lobby: the TV lobby shows the badge `Bauherr · 2-4 Spieler`; the leader's phone picks the game next to Heimlauf.

## Unresolved

- Turn budget vs the 45-60 min target (needs a timed play with people).
- Whether "Gratis" should also waive the first stage when several stages are chosen (done: yes, only the first); and whether permit "Doppelt" should double the cost of every chosen stage (done: yes).
- Names in `Opt` labels ("Lieber nicht", "Feld n") are first drafts.
