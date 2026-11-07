# Durak — build report

Branch `feat/durak` (worktree `../mediagram-durak`), from `feat/spieltisch`. No version bump, no changelog.

## What was built

Host (`crates/mediagram-game/src/`)
- `durak_rules.rs` — pure: `beats`, `can_throw`, `attack_limit`, `auto_attack`, `auto_defence`, `first_attacker`.
- `durak.rs` — state, stages (`Attack`, `Defend`, `Throw`, `Settle`, `Final`), actions, absent-player auto play.
- `durak_flow.rs` — lead / beat / throw in / take / pass, then `resolve` (move cards, refill, safe players, next attacker, end).
- `durak_views.rs` — phone desk and TV scene.
- `GameKind::Durak`, factory branch, lobby seat gate 2–6 (`DURAK_TOO_FEW` / `DURAK_TOO_MANY`).
- Tests: `durak_rules_tests.rs`, `durak_tests.rs`, plus a seat-gate test in `room_settings_tests.rs`. 192 pass, clippy clean.

Client (`crates/mediagram-game/client/src/`)
- Lobby entry + badge (`PhoneLobby.tsx`, `ui/lobby.ts`), TV texts (`tv/cards/cards-texts.ts`).
- `tv/cards/cards-durak.ts` (new): pair grid + trump position; `cards-motion.ts` uses them when the scene's game is "Durak", draws `scene.trump` under the deck, and hides the discard shadow. Mau-Mau paths are unchanged (its `trump` is `None`, its pile placement is the old call).
- Tests: two added to `tests/cards-layout.test.ts` (17 bun tests pass; typecheck and build clean).

## Throw-in order (my choice)

No timers, no free-for-all. After the attack is beaten the **attacker** is asked first, then the other players in seat order after the attacker (the defender is skipped):
- The asked player gets "Dazulegen" (only cards whose rank is on the table, attack or defence, while below the limit) and "Fertig".
- A played card must be defended before anyone continues; afterwards the **same** thrower is asked again, so one player can add several cards. "Fertig" ends their turn for the whole bout, they are not asked again.
- Players with nothing that fits (or when the limit is reached) are skipped without a prompt, so nobody waits for a pass that cannot matter.
- When the defender takes ("Aufnehmen"), the same round runs once more with nothing to defend: others add up to the limit, then the defender picks everything up.
- End: when the round has nobody left who can add (all beaten → Bito; taking → taken).
- Trade-off: a player who said "Fertig" cannot add a rank that only appears later in the bout. Chosen for pace over the full classic rule; the table never stalls and each phone is asked at most a few times.
- Limit: `min(6, defender's hand when the bout began)`.

## Other decisions

- Bout end is not instant: the table stays 2.4 s with a banner ("... hat alles geschlagen – Bito!" / "... nimmt die Karten auf") before the cards move on, refill and the next attacker. This is what makes Bito vs taken readable on the TV.
- First attacker: the holder of the lowest trump (seat 0 if nobody holds one, e.g. 6 players use all 36 cards and the trump card is in a hand).
- Refill: attacker first, then seat order, defender last; the trump card (bottom of the deck) is drawn last. Safe players are marked once the deck is empty and the hand is empty, in the same order (attacker first, defender last), which is the ranking order for simultaneous exits.
- Next attacker: Bito → the defender (or the next player still in if they are out); taken → the player after the defender.
- End: ≤1 player left with cards. The last is the Durak (ranking last). If everyone empties together, nobody is Durak ("keiner ist Durak!"). Score for the final screens = players beaten, as in Mau-Mau.
- Absent player (after `auto_ms`, like Mau-Mau): leads the cheapest card (plain before trump), beats the first open attack with the cheapest beating card or takes, throws in nothing.
- Phone: the desk's table row lists each attack followed by the card beating it (the generic row has no pairing mark); for "Schlagen" the defender picks the attack on the table, then the hand card. Info line: trump, deck count, role.
- TV: trump card lies sideways under the deck while it exists; the note pill always says "Trumpf ♦ Karo" (so the suit stays visible once the last card is drawn); seat tags say Angreifer / Verteidiger / Raus / Durak.
- No animation for "taken" and Bito discards: the pairs stay for the 2.4 s, then vanish while counts change (no discard-pile or table→hand flight kind exists).

## Wire changes

None. Existing types suffice: `piles` hold one attack/defence pair each (event `Play.pile` = pair index), `trump`, `note`, `marker`, `table` + `HandAndTable`. Only `GameKind` gained `durak` (regenerated `GameKind.ts`).

## Verification

- `cargo clippy -p mediagram-game --all-targets -- -D warnings` clean; `cargo test -p mediagram-game` 192 pass.
- Client `bun run typecheck`, `bun test` (17), `bun run build` pass.
- Scripted phones vs `game-night-dev` (GAME_PORT=8094): 3-player game to the end (attack, beat, throw-in, taking, Bito, taken, empty deck, safe, Durak, ranking; 0 server errors), and a headless 412×915 phone played a full game by tapping its own desk.
- Screenshots in `plans/reports/durak-261107-screens/`: `tv-3p-*` (960×540), `phone-*` (412×915). Drivers are in the session scratchpad, not the repo.

## Not done / open

- No box measurement (swiftshader only). Durak adds at most 12 pooled card meshes plus the trump card.
- No 4–6 player screenshot (layout of seats is the shared one; pair grid max right edge x≈5.4 world units against seats at ±7.3 for 6 players).
- Pairing on the phone desk is positional only (attack, then its defence); a pairing mark would need a desk change.
