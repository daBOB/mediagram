# Simulator baseline: the old engine and the live game (2026-11-09)

`grand-allee-sim`, default run (1 000 games per player count, `Rules::default()`
unchanged, think times unchanged: tap 1.5 s, decision 6 s, bid 10 s).
Before: ecfe7d5f (0.168.0), the simulator's own move order. After: the simulator
drives `GrandAlleeGame` through the `Game` trait (branch
`refactor/simulator-plays-live-game`).

## Run time

| | `time grand-allee-sim` (4 000 games) |
|---|---|
| Before | 0.11 s |
| After | 0.82 s |

Under 5 s, so the player counts still run one after another.

## Before (ecfe7d5f; identical to the "rent 50 %" table of 2026-11-08)

| Players | Minutes p50 (p10–p90) | Rounds | Last standing | All owned at ½ | Hotels owned at ½ | ½-leader wins | Seat 1 wins | Max wait p50/p95 s | Debt auctions | Forced buys | Out | Win vs fair: cautious/balanced/bold |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2 | 25 (15–36) | 82.1 | 87 % | 26 % | 7.2 | 68 % | 51 % | 41/55 | 6.7 | 8.5 | 0.9 | 0.84/1.05/1.11 |
| 4 | 37 (30–42) | 65.5 | 42 % | 57 % | 9.5 | 80 % | 27 % | 67/87 | 9.9 | 18.3 | 2.2 | 0.83/1.02/1.15 |
| 6 | 39 (36–42) | 46.0 | 1 % | 46 % | 9.3 | 69 % | 18 % | 91/115 | 6.5 | 25.4 | 2.4 | 0.73/1.03/1.23 |
| 8 | 40 (38–43) | 35.0 | 0 % | 29 % | 9.0 | 67 % | 13 % | 112/137 | 3.7 | 32.0 | 1.8 | 0.79/0.96/1.26 |

## After (the live game)

| Players | Minutes p50 (p10–p90) | Rounds | Last standing | All owned at ½ | Hotels owned at ½ | ½-leader wins | Seat 1 wins | Max wait p50/p95 s | Debt auctions | Forced buys | Out | Win vs fair: cautious/balanced/bold |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2 | 29 (18–42) | 80.4 | 90 % | 21 % | 6.8 | 66 % | 50 % | 57/81 | 6.8 | 8.0 | 0.9 | 0.90/0.99/1.11 |
| 4 | 42 (34–45) | 64.7 | 46 % | 59 % | 9.5 | 80 % | 27 % | 85/111 | 10.7 | 17.5 | 2.3 | 0.80/1.08/1.13 |
| 6 | 43 (40–45) | 45.9 | 2 % | 46 % | 9.3 | 69 % | 15 % | 107/134 | 6.9 | 25.0 | 2.5 | 0.71/1.03/1.25 |
| 8 | 43 (41–45) | 35.0 | 0 % | 32 % | 9.0 | 66 % | 14 % | 126/155 | 3.9 | 31.1 | 1.9 | 0.80/0.91/1.30 |

## Targets

| Target | Before | After | Holds? |
|---|---|---|---|
| 37–40 min per game (4–8 players) | 37 / 39 / 40 | 42 / 43 / 43 | **No**: 2–5 min over (see below; the clock model changed, the rules did not) |
| Half-time leader wins < 80 % | 4p 79.7 %, others 67–69 % | 4p 79.6 %, others 66–69 % | Yes, just (4p at the limit, as before) |
| 9 of 10 hotels owned at half time (4+ players) | 9.49 / 9.33 / 8.96 | 9.50 / 9.33 / 9.01 | Yes (8p at the limit, as before) |

Unrounded values from a one-off run with more decimals.

## Why the minutes moved

The rules play out the same way (rounds, auctions, forced buys and outs within
noise). The clock did not: the old simulator timed its own move order, and that
order had drifted from the live game. Mean minutes per game at 4 players,
by what the time is spent on (instrumented one-off runs):

| Spent on | Old | Live | Drift in the old simulator |
|---|---|---|---|
| Field for an entrance | 0 | 3.0 | Placed entrances at random with no time; the live game asks for the field (Rathaus: hotel, then field; the free entrance of a main building; Freier Eingang) |
| Declining ("Lieber nicht", "Fertig") | 0 | 1.1 | Charged a decision only when the bot acted; live, every question asked costs one |
| Walking | 4.5 | 5.6 | Timed hops by the die, not the hops past occupied fields; no `lead_ms` (200 ms a walk, 0.9 min) |
| Gaps before chained questions | 0 | 0.4 | No `chain_ms` (400 ms) |
| Taps | 8.1 | 6.8 | Charged two taps for an extra turn after a 6, live has one ("Nochmal würfeln") |
| Notes | 1.9 | 1.6 | A note after a guest pays (none live); none after buys, takeovers, rejections, auctions |
| Other decisions, dice, bids | 22.1 | 22.3 | — |
| **Total** | **36.6** | **40.8** | |

At 8 players the walk grows most (5.3 → 7.2 min, more occupied fields to pass)
and entrance fields add 2.6 min; at 2 players declines add 1.9 min.

The longer waits (p95 87 → 111 s at 4p, 137 → 155 s at 8p) are the same items:
more questions per move and longer walks between a player's decisions.

Two behaviours changed on purpose or with the live game: a playing bot now
raises the money on a Doppelt its other properties cover (the old simulator
always auctioned the hotel in question; the house still does), and entrances
go on the first free frontage field (the live pick) instead of a random one.
They show only at 2 players: hotels owned at half time 7.2 → 6.8, all ten
26 → 21 %. Switching the raise off gives 7.0 / 23 %, scattering entrances as
before gives 7.1 / 24 %; each explains part, the rest is seed noise.

## Read

The 37–40 min target now fails by 2–5 min at 4–8 players, because every
question the live game asks is charged the assumed 6 s, including picking a
field and declining. Those are probably quicker than a real decision; the test
night's measured think times decide whether the rules need retuning. `Rules`
and think times are unchanged here, as decided.
