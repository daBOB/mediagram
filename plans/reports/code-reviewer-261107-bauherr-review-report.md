# Code review: Bauherr (rules, turn flow, wire, phone sheet, plain TV board)

Scope: `git diff f8d37d4c..HEAD -- crates/mediagram-game`, limited to `src/bauherr*.rs`, `src/protocol_bauherr.rs`, the Sheet/SheetLine additions, Bauherr's parts of `room_settings.rs`/`factory.rs`, `client/src/phone/PhoneSheet.tsx`, `client/src/tv/TvBauherr.tsx` (+ `tv/bauherr/*`, checked for crashes only). Card games not reviewed.

Verification: `cargo test -p mediagram-game` 264 green. Throwaway probes ran in a scratch copy of HEAD (repo untouched):
- a fuzz of 300 seeded games: 2–4 players, low start cash, hotels and entrances already built, random disconnects and reconnects, random, out-of-range and foreign taps with `at` skewed ±500 ms;
- three targeted probes, quoted in the findings below.

## Overall

The money core is solid. The fuzz checked these after every tick and action, and none failed:
- cash never went negative;
- bankrupt seats owned nothing;
- no entrance sat on an unbuilt or unowned plot;
- `Player.score == worth` held;
- no seat that was out ever had the turn.

All 300 games reached `Final`; 254 of them had a bankruptcy. Views were built every 7 ticks without a panic. Nothing critical or high. Two medium findings: double-tap protection, and a rule question at the Rathaus.

## Findings

### M1 — A double tap answers the next question when questions follow each other directly (medium, verified)
- Where: `client/src/phone/PhoneSheet.tsx:20`, `src/bauherr.rs:148`, `src/bauherr_turn.rs:80-91`.
- What happens: `BuildMenu`, `Entrances` and `Hotel` open the next question at the same server instant as the tap (`since: now`). The phone resets its 700 ms lock as soon as the prompt changes (line 20). On a LAN the new sheet arrives in a few ms, so the second tap of a double tap (about 100–300 ms later) lands on the new question. The server also accepts it, because `at` is after `since`.
- Example: at the Rathaus, a double tap on "Eingang kaufen" places a paid entrance on the first offered field. A double tap on "Hotel ausbauen" rolls the permit die for the first hotel.
- Probe: tap index 1 (Entrances) at t=1000, then tap at t=1200 → `Ok`, cash 12000 → 11700, "Ana baut einen Eingang für Kastanienhof."
- The builder's report claims both guards stop exactly this. They do not for these chained questions.
- Fix (server, preferred): open chained questions a little later. In `choose`, the `BuildMenu`/`Entrances`/`Hotel` arms call `self.ask(now + 400.0, …)` (or a `cfg.chain_ms`). The phone then shows the buttons disabled until `opens_at`, and the server check drops the stray tap. Client-only alternative: delete line 20, so the lock carries into the next question.

### M2 — The Rathaus allows one errand per visit, the spec says "and/or" (medium, rule question, verified)
- Where: `src/bauherr_turn.rs:92-101`, `src/bauherr_asks.rs:62-71`.
- Spec: "build on any own hotel (permit roll) and/or buy entrances". As built, building or placing one entrance ends the turn.
- Consequence: the natural combination cannot be done in one visit — build the main building, then put an entrance on it — and only one entrance can be bought per visit.
- Probe: build at the Rathaus → `Stage::Note`, turn over.
- The build report does not list this among its decisions.
- Fix if "and" is meant: after `Levels`/`Place`, if the player still stands on the Rathaus, offer the Rathaus menu again without the errand already done, until "Lieber nicht". Otherwise record it as a deliberate decision. **User call.**

### L1 — An absent player's autoplay skips free things and never buys an entrance at the Rathaus (low, verified)
- Where: `src/bauherr_asks.rs:112`, `:117`, `:124`.
- The `keeps` reserve check also applies at cost 0. Below 2,000 Taler an absent player therefore skips a free entrance on Freier Eingang, and a free stage after a "Gratis" permit. Probe: cash 1,500 on field 9 with a built hotel → no entrance, next turn.
- At the Rathaus, `BuildMenu` costs 0 and has priority 4, so it always beats `Entrances`. If no building then keeps the reserve, the player skips and never buys the affordable entrance.
- Fix: `let keeps = |cost: i32| cost == 0 || cash - cost >= RESERVE;` and price `BuildMenu`/`Entrances` at their cheapest reachable option instead of 0.

### L2 — The awards screen ignores Bauherr's ranking (low, verified by reading; ranking, not looks)
- Where: `client/src/tv/TvBauherr.tsx:38`.
- `TvFinal` gets every room player, sorted by score, and `final.ranking` is ignored. Consequences:
  - a phone that joined mid-game (unseated, score 0, `room_seats.rs` seats it) appears in the ceremony;
  - bankrupt players and that joiner share places;
  - the TV's places can differ from the phones' "Platz n", which come from `standings()` with a seat-index tie-break.
- Fix: copy Heimlauf's handling (`TvRace.tsx:86-89`): filter and order by `final.ranking` and pass `ranked`. Keep this when the 3D board (phase 05) replaces this file.
- Related (`src/bauherr_rules.rs:211`): bankrupt players rank by seat index, not by who lasted longer. Fix: record the turn number when a seat goes out and sort out-seats by it, latest first.

### L3 — "Doppelt" with nothing affordable ends the turn without saying why (low, verified by reading)
- Where: `src/bauherr_turn.rs:137-144` with `levels_ask` returning `None` → `ask(None)` → `end_turn(now, None)`.
- The phone last showed "Doppelt! Alles kostet das Doppelte." and then the next player is on, with no note.
- Fix: when `levels_ask` is `None` after a permit, `end_turn` with a note such as "{who} kann sich das Doppelte nicht leisten."

### L4 — After a phone wakes, its clock estimate can stay stale; a tap is dropped and the buttons stay dead for up to ~30–60 s (low, UNVERIFIED on a device; the clock code was already there)
- Where: `client/src/net.ts:93-102` together with `PhoneSheet.tsx:22` (`now >= opens_at`) and the server's `at` check at `src/bauherr.rs:144,148`.
- Assumption: Android Chrome's `performance.now()` does not advance during deep suspend (CLOCK_MONOTONIC). If so, the offset is stale by the length of the sleep. The lowest-round-trip sample wins, and old samples are not cleared on reconnect, so the stale offset can persist until they rotate out.
- Meanwhile the turn player's buttons stay disabled and any tap that does get through is dropped as `Ignored`. That player counts as connected, so autoplay does not take over. The player is not stuck for good, but may wait up to a minute.
- Fix: clear `samples` in `ws.onopen` and on `visibilitychange` → visible, so the six-ping burst re-syncs.

### Info (already there before Bauherr, shared with Heimlauf) — a phone that goes silent without closing its socket stalls the turn
- Where: `room_seats.rs:29` (`connected()` only checks `conn`); no liveness timeout in `room_loop.rs`/`server.rs`.
- A phone that goes dark without a WebSocket close stays "connected". Bauherr then waits indefinitely at its Roll or Ask. Players turning off their screen lock works around this today.
- Fix if wanted: track the last ping per connection and treat a player as absent after about 15 s of silence in `absent_since`.

## Checked and clean
- **Money:** every cash change goes through `Table` (`walk`, `buy`, `build`, `place_entrance`, `pay`, `sell_one`).
  - Every debit is gated by a question built on state that cannot change before the answer: only the turn player acts, and no tick changes state between a question and its answer.
  - `pay` conserves money. The selling loop runs until no plot is left, so a bankrupt player owns nothing and has no entrances, and a guest can never pay an owner who is out.
  - Entrances disappear with the last stage. Net worth = cash + land + stage costs.
- **Stalls:**
  - every stage except Roll and Ask runs on a timer, and both are autoplayed for absent players;
  - `ask()` always adds Skip, so `auto_index`'s fallback and `ask.opts[pick]` are valid;
  - an empty question ends the turn;
  - `next_turn` always terminates and checks the round limit only between turns;
  - a bankruptcy mid-turn shows a note and moves on to the next turn or `Final`;
  - when only one player is left, the game goes to `Final`.
- **Trust boundary:**
  - a wrong player, a wrong stage or a tap during `Final` → `Ignored`;
  - an out-of-range Ask index → `NotAllowed` via `.get()`; Roll with an index other than 0 → `Ignored`; other action kinds → `Ignored`;
  - all plot and field ids come from options the server built, so no unwrap or index is reachable from input.
  - `at` comes from the client, so the early-tap check is a convenience guard, not a security control. That is fine.
- **Rules:** walking and the bank bonus (passing or landing, once per lap), Bauplatz/Baustelle/Freier Eingang, the permit die 3/1/1/1, stages in order, "how far" limited to what the player can afford, entrances (built hotel only, bordering fields, one per field), guest = nights × current rate, forced sale order, rounds 48/32/24, ranking by worth — all match the spec, apart from M2.
- **Lobby gate:** `StartGame` keeps only connected players, so the seats match the 2–4 the gate counted. Quiz, poster and race behave as before the `blocked()` refactor.
- **TV board:** no crash paths (all indices come from the server; `TvFinal` handles an empty list). The generated TS is in sync.

## Plan follow-ups (no plan edits made)
The phase-03 todo items appear complete per the build report. Still open: tuning the turn budget, the Rathaus "and/or" question (M2), and the builder's Gratis/Doppelt interpretations.

## Unresolved questions
1. Rathaus: one errand per visit, or build and entrances in the same visit (M2)?
2. Should an absent player keep the 2,000 reserve even for free options (L1)? The spec says "buys/builds when it keeps ≥ 2 000", which reads as applying to purchases only.
