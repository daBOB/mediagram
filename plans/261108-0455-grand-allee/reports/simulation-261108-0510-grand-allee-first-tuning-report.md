# Grand Allee — first simulation and tuning (2026-11-08)

Engine: `crates/mediagram-game/src/grand_allee/` (11 rule tests pass).
Simulator: `cargo run --release -p mediagram-game --bin grand-allee-sim -- …`,
deterministic seeds, bots in three styles (cautious/balanced/bold, drawn per
seat). Time model: die 2.05 s, hop 0.28 s, note 1.4 s (live Bauherr timings);
tap 1.5 s, decision 6 s, auction 10 s (assumed until the test night).

## Fixes found by the first run

- "Bank stops paying with two left" was applied from the start of a 2-player
  game; the classic means two *left* after others went out. Fixed
  (`Table::bank_pays`, test added).
- Every roll tap cost a 6 s decision; now a 1.5 s tap.

## Runs (1 000 games per player count unless noted)

Columns: minutes p50 (p10–p90) · % games ending last-player-standing · % with
all 10 hotels owned at half time · mean hotels owned at half time ·
half-time leader wins · max wait p95 (s) · forced purchases per game · win
rate vs fair share cautious/balanced/bold.

**Design start values** (rent 100 %, round turns 120, rounds 14–40):

| P | Min | Last | All ½ | Owned ½ | Leader | Wait p95 | Forced | Styles |
|---|---|---|---|---|---|---|---|---|
| 2 | 11 (9–13) | 25 % | 0 % | 5.8 | 78 % | 50 | 4.1 | .90/1.02/1.09 |
| 4 | 17 (16–19) | 5 % | 2 % | 7.2 | 64 % | 81 | 11.6 | .89/1.01/1.10 |
| 6 | 17 (15–19) | 0 % | 1 % | 6.8 | 55 % | 102 | 15.2 | .88/1.00/1.12 |
| 8 | 16 (15–18) | 0 % | 0 % | 6.1 | 50 % | 122 | 17.2 | .92/1.01/1.06 |

**Rent 50 %, round turns 280, rounds 30–120** (best length):

| P | Min | Last | All ½ | Owned ½ | Leader | Wait p95 | Forced | Styles |
|---|---|---|---|---|---|---|---|---|
| 2 | 25 (15–36) | 87 % | 26 % | 7.2 | 68 % | 55 | 8.5 | .84/1.05/1.11 |
| 4 | 37 (30–42) | 42 % | 57 % | 9.5 | 80 % | 87 | 18.3 | .83/1.02/1.15 |
| 6 | 39 (36–42) | 1 % | 46 % | 9.3 | 69 % | 115 | 25.4 | .73/1.03/1.23 |
| 8 | 40 (38–43) | 0 % | 29 % | 9.0 | 67 % | 137 | 32.0 | .79/.96/1.26 |

Other levers on top of that (500 games):

| Lever | Effect |
|---|---|
| Rent 100 %, round turns 240–300 | Bankruptcy ends most games early (2p 100 %, 4p 80–95 % last standing, 16–27 min); leader wins 67–78 % |
| Rent 70 % | Between the two: 2p 20 min, 4–8p 32–38 min; leader 52–81 % |
| Lap bonus 3 000 | Length +1–2 min; leader 66–79 %; no real change |
| Start cash 16 000 | No real change |
| +4 Grundstück fields (7, 15, 27, 35), experiment only | All owned at ½ 48/87/72/56 %, but forced purchases 14–50 per game; leader unchanged |

## Read

1. **Length** is tunable: rent 50 % and a longer round limit put 4–8 players
   at 37–40 min. Two players end at ~25 min, mostly by bankruptcy.
2. **Runaway** is structural: the half-time leader wins 65–82 % with every
   money and board lever. Rent buys stages, stages raise rent. Only a rule
   change (a catch-up) would move it.
3. **Ownership:** 9–9.5 of 10 hotels are owned at half time from 4 players
   up, but "all ten" in only 29–57 % of games; more Grundstück fields fix that
   at the price of heavy forced-purchase churn.
4. **Forced purchase at half price** happens 18–32 times a game at 6–8
   players (bots force-buy whenever they can afford it).
5. **Waiting** at 8 players is 137 s at p95, over the 2 min target.
6. **Style:** bold play wins ×1.2–1.3 its fair share at 6–8 players.

## Open questions (to the user)

See the decision questions in the session; results go into the design's
Decisions section and `Rules::default()`.

## Decisions and tuned values (user, 2026-11-08)

- Rates halved in the hotel table itself (`board.rs`), not as a factor;
  `Rules::default()`: round turns 280, rounds 30–120. The default run
  reproduces the "rent 50 %" table above exactly.
- Runaway accepted (target ≤ 80 %), ownership target 9 of 10 at half time,
  forced purchase kept as the classic, 8 Grundstück fields kept.
- Open: 8-player waiting (137 s p95) until the test night measures real
  decision times.
