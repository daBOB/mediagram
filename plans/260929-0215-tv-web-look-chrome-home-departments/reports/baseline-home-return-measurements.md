# Baseline — returning to Home on the TV box, before the web-look chrome

Box `192.168.0.35:5555` (Skyworth HPR302), **benchmark build 0.81.1** (`installBenchmark` +
`cmd package compile -m speed -f`), profile "TV test", 2026-09-29 ~03:00–04:00.
Per run: `logcat -c` + `dumpsys gfxinfo reset`, the repro, 4–5 s settle, then
`grep -c "Davey!"`, `grep -c "Choreographer.*Skipped"`, `gfxinfo` janky frames.

| Repro | Run 1 | Run 2 | Run 3 |
|---|---|---|---|
| R1 Home → OK on a Recently added poster → Back | Davey 0, skipped 0, janky 3 (12.5 %) | 0, 0, 2 (8.3 %) | 0, 0, 3 (12.5 %) |
| R2 cover Watch now → ~10 s playing → Back (one Back returned to Home each time) | 0, 0, 7 (2.4 %) | 0, 0, 5 (5.6 %) | 0, 0, 6 (6.9 %) |
| R3 Movies tab → Home tab | not measured — see below | | |

- No Davey frame and no skipped-frame warning in any valid run: on the optimised build the
  return to Home is not visibly slow on this box today, matching the decoder-freeze closeout.
  The janky-frame percentages are the numbers to compare against.
- R3 could not be driven on the old masthead: its focused tab exposes no text node, so the
  guarded script (which refuses to send a key unless it can confirm where focus is) stopped.
  The masthead is replaced by the pills in this plan; R3 is measured on the new chrome only.
- An earlier, unguarded attempt at R2/R3 sent one Back too many, left the app and reached
  the Google TV launcher (Prime Video opened; left with Home, nothing else pressed). Its numbers
  are discarded. Every run above checked that mediagram was in front before each key.
- Debug-build numbers were not taken (the closeout report already has them: ~5 s of Davey
  frames on any return to Home).
