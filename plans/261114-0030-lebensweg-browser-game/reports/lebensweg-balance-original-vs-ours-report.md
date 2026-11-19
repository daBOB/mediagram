# Lebensweg balance: the original board against ours (bots)

Date 2026-11-14. Tool: `cargo run --release -p mediagram-game --bin lebensweg-sim -- --games 20000`
(`crates/mediagram-game/src/bin/lebensweg-sim.rs`, bots `src/lebensweg/bots.rs`, recorder `src/lebensweg/play.rs`).
20,000 games per row; a fair share is 1.00 (wins × players ÷ seats); ±0.02 is noise for routes, ±0.04 for careers.

**Original** = the original's 147 spaces, 2000-rulebook money (board × 1.0). **Ours** = every stretch × 1.3,
one-off amounts × 1.3, 32 life tokens, 5 set aside for the Villa. Bots: coin toss at every fork (so each route is
compared fairly), coin toss per seat for insurance and stock, best salary, Villa only when richest (rulebook advice).
Minutes: assumed pace (tap 1.5 s, wheel 2.5 s, 0.4 s per field, 3 s per field's news, 6 s per decision).

## Result

| Board | Players | Minutes p50 (p10–p90) | Spins / player | Studium / Ausbildung | Familie / Abenteuer | Riskant / Sicher | Villa / Landhaus | Insured / not | Stock / not | Winner worth p50 | Ends in debt | Pile empty at |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| original | 2 | 12 (11–13) | 25 | 1.02 / 0.98 | 1.02 / 0.98 | 1.01 / 0.99 | 1.64 / 0.03 | 1.00 / 1.00 | 0.98 / 1.02 | 1555k | 0.2 % | 96 % (1 % of games) |
| original | 4 | 25 (23–27) | 25 | 0.95 / 1.05 | 1.01 / 0.99 | 1.02 / 0.98 | 2.73 / 0.04 | 1.00 / 1.00 | 1.03 / 0.97 | 1645k | 0.1 % | 76 % (94 % of games) |
| original | 6 | 39 (37–42) | 25 | 0.86 / 1.14 | 0.98 / 1.02 | 1.01 / 0.99 | 3.92 / 0.02 | 1.01 / 0.99 | 1.14 / 0.86 | 1710k | 0.1 % | 39 % (100 % of games) |
| ours | 2 | 14 (13–15) | 32 | 1.02 / 0.98 | 1.01 / 0.99 | 1.01 / 0.99 | 1.69 / 0.03 | 1.00 / 1.00 | 0.96 / 1.04 | 2023k | 0.1 % | 97 % (0 % of games) |
| ours | 4 | 29 (27–32) | 32 | 0.98 / 1.02 | 0.98 / 1.02 | 1.02 / 0.98 | 2.88 / 0.03 | 1.00 / 1.00 | 1.03 / 0.97 | 2138k | 0.0 % | 83 % (90 % of games) |
| ours | 6 | 47 (44–50) | 32 | 0.90 / 1.10 | 0.97 / 1.03 | 1.00 / 1.00 | 4.17 / 0.02 | 1.01 / 0.99 | 1.13 / 0.87 | 2206k | 0.0 % | 43 % (100 % of games) |

Careers retired from, 4 players (win vs fair): Buchhaltung / Kunst / Profisport / IT-Beratung / Medizin / Bühne / Polizei / Vertrieb / Lehramt
- original: 0.77 / 1.20 / 1.33 / 1.41 / 0.73 / 0.95 / 0.64 / 0.67 / 1.08
- ours: 0.77 / 1.18 / 1.40 / 1.34 / 0.72 / 1.00 / 0.64 / 0.69 / 1.06

| Measure | Original 4 pl. | Ours 4 pl. | Gap (points) |
|---|---|---|---|
| Studium / Ausbildung | 0.95 / 1.05 | 0.98 / 1.02 | +3 (2 pl.: 0, 6 pl.: +4) |
| Familienweg / Abenteuerweg | 1.01 / 0.99 | 0.98 / 1.02 | −3 (2 pl.: −1, 6 pl.: −1) |
| Riskanter / Sicherer Weg | 1.02 / 0.98 | 1.02 / 0.98 | 0 |
| Insured / not, stock / not | 1.00, 1.03 | 1.00, 1.03 | 0 |
| Careers | Profisport 1.33, IT 1.41 … Polizei 0.64 | 1.40, 1.34 … 0.64 | every career within ±7 |
| Game length p50 | 25 min (2 pl. 12, 6 pl. 39) | 29 min (14, 47) | × 1.16–1.2 |
| Ends in debt | 0.1 % | 0.0 % | |
| Token pile runs out at | 76 % of the game | 83 % | |

The Villa/Landhaus column is no balance test: bots go to the Villa only when richest, so Villa seats win by
construction; Landhaus wins 2–4 % in both, as the rulebook's rule makes it a safe place for those behind.

## What the bots changed

1. **Copies spread by position** (first stretch): Familienweg got a second payday, Abenteuerweg none → Familienweg
   +7–9 points; Buchhaltung −12, Profisport +17. Fixed: `stretch.rs` copies every kind in proportion.
2. **Copies picked per stretch by count**: Abenteuerweg got the extra payday → Abenteuerweg +11; Kunst +15, Medizin +17
   (their 125,000 / 100,000 fields came twice). Fixed: copies picked one at a time so each kind's running total —
   money fields by amount, each career apart — stays at 1.3 × the original's; a fork branch never doubles its payday.
3. **A look-ahead to the board's end** (so Medizin's fields also grow): Profisport +12, IT −10. Reverted; Medizin's
   four fields (5,000 / 5,000 / 25,000 / 100,000) stay × 1.0 and its share still matches (0.72 vs 0.73).
4. **33 tokens**: the pile ran out at 86 % of the game (original 76 %); 32 gives 83 % with the same shares.

## The spinner gap decides IT-Beratung

IT-Beratung earns 50,000 whenever the wheel stops between two numbers. Win share at 4 players, ours:

| Gap per spin | 0 % | 2 % | 5 % (now) | 16.4 % (clone's wheel) |
|---|---|---|---|---|
| IT-Beratung | 0.90 | 1.06 | 1.34 | 2.52 |

The physical original's rate is unknown; both boards above use 5 %, so the comparison holds. 2 % puts IT-Beratung
at a fair share without moving the others more than ±0.05.

## Unresolved

- Spinner gap rate: 5 % (now) or 2 % (fair IT-Beratung)? Users' call; the original's rate cannot be measured from
  the rulebook.
- Pace is assumed; a test night measures it (the length ratio, × 1.2, is what the bots know).
- Bots play simple habits; humans may value salary swaps and stocks differently. Shares hold for these bots only.
