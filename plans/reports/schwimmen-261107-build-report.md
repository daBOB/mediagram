# Schwimmen on the card table — build report

Branch `feat/schwimmen` (worktree `../mediagram-schwimmen`), from `feat/spieltisch`. No version bump, no changelog.

## What was built

Host (`crates/mediagram-game/src/`)
- `schwimmen_rules.rs` — pure: `Value` (half points, so 30½ = 61 and 31 = 62), `value`, `Swap`, `apply`, `best_swap`.
- `schwimmen.rs` — state, deal, `impl Game`, absent-player auto play (`auto_ms` after the later of leaving / turn start): knock at ≥ 27, else the best improving swap, else pass.
- `schwimmen_turn.rs` — swap one / swap all / pass / knock and what follows each (31 ends the round, knocker's turn coming round again is the reveal, all-pass refreshes the middle).
- `schwimmen_round.rs` — reveal, lives, out, ranking, final scores (players beaten, like Mau-Mau).
- `schwimmen_views.rs` — phone desk and TV scene.
- `GameKind::Schwimmen`, factory branch, lobby gate 2–8 (`SCHWIMMEN_TOO_FEW/MANY`).
- Tests: `schwimmen_rules_tests.rs`, `schwimmen_tests.rs` (deal, swap one/all and their validation, pass count, all-pass refresh, knock → last round → reveal, no refresh after a knock, 31 mid-round, 31 in the deal, 30½, lowest loses, ties, all-tied spared, next round + starter rotation, schwimmt then out, last player wins + ranking + scores, desk buttons, TV scene, absent auto play) and a seat-gate test in `room_settings_tests.rs`. 182 pass (156 before), clippy clean.

Client (`crates/mediagram-game/client/src/`)
- Lobby entry + badge (`PhoneLobby.tsx`, `ui/lobby.ts`), `GameKind.ts` regenerated.
- `tv/cards/cards-texts.ts`: Schwimmen banner / note / marker texts pre-drawn.
- `tv/cards/cards-layout.ts` `pileAt`, `cards-motion.ts`, `cards-world.ts`: several piles lie in a row across the middle (a lone pile sits where the discards always did, so Mau-Mau is unchanged); one shadow patch per pile; a played card flies to its own pile; every card still in flight is hidden from its pile until it lands.
- Tests: two added to `tests/cards-layout.test.ts` (17 bun tests pass, typecheck and build clean).

## Wire changes

None. Everything is expressed with the existing generic types: `table` cards selectable (`enabled`), `hand_and_table` button ("Tauschen"), seat `open` for the reveal, seat `marker` for lives / knock / value, one pile per middle card, `Play { card, pile }` events with the slot as `pile`.

## Shared code touched (flag for the Durak merge)

- `maumau_views.rs`: `button` and `name` made `pub(crate)` (no behaviour change).
- `cards-hud.ts` `layout()`: a restored "no note" left the last pre-drawn note on screen (the restore set `noteKey = null` and then `setNote(null)` returned early). It now restores with `""`. Mau-Mau had the same latent bug (a stale "Zieh 8").
- `cards-layout.ts` / `cards-motion.ts` / `cards-world.ts`: the multi-pile generalisation above. Durak's table will want `pileAt` too; its piles may need its own positions.
- `lib.rs`, `protocol_settings.rs`, `factory.rs`, `room_settings.rs`: one-line additions each; expect trivial merge conflicts with Durak.

## Decisions

- Phone buttons: "Tauschen" (one hand + one middle card), "Alle drei tauschen", "Schieben", "Klopfen" (hidden once someone knocked). Info line: own hand value and coins. Out players get a wait screen ("Du schwimmst nicht mehr mit").
- Lives: 3, 2, 1, then 0 = "schwimmt" (still playing), next loss = out. TV tag marker: `●●○`, `schwimmt`, `raus`; with ` · klopft` after a knock; at the reveal `coins · value` plus ` · −1` for whoever lost.
- Reveal: all hands lie face up beside their seat for 8 s (`reveal_ms`), banner names who lost ("verliert ein Leben" / "schwimmt jetzt" / "scheidet aus" / "und alle mit gleichem Blatt verlieren"), note "Feuer – 31!" or "Aufgedeckt". Then the next round is dealt automatically; the starting player rotates to the next one still in.
- 31 or three of a kind dealt in the first three cards: 31 ends the round at once (reveal, no turn).
- Everyone tied for lowest: nobody loses a life (the spec's "ties: all lowest lose" would otherwise knock everyone out at once). Unconfirmed call, easy to flip in `reveal_round`.
- All-pass refresh: three new cards from the deck, the old middle goes under the deck, so the deck never runs dry (max 8 players leaves 5 cards).
- Hand cards are kept in suit/rank order (the order the desk sends); the middle keeps slot order.
- Score for the final screens = players beaten, as in Mau-Mau; ranking = winner, then reverse order of going out.

## Verification

- `cargo clippy -p mediagram-game --all-targets -- -D warnings` clean; `cargo test -p mediagram-game` 182 pass.
- Client `bun run typecheck`, `bun test` (17), `bun run build` pass.
- Scripted phones vs `game-night-dev` (GAME_PORT=8093), 3 players, two full games to the end (swap one/all, knock, 31, swimming, tie, out, refresh, winner, ceremony); plus an 8-player run (reveal, knock, swimming, out). A real headless phone UI (412×915) played a game to the end by tapping its own desk. Drivers live in the session scratchpad (`schwimmen-driver.mjs`, `schwimmen-phone.mjs`), not the repo.
- Screenshots (TV 960×540, phone 412×915): `plans/reports/schwimmen-261107-screens/`.

## Known gaps

- 8-player reveal is crowded: the top seat's open cards sit under the "Aufgedeckt" note and the side fans cross the middle row. Values stay readable in the tags. Not touched: the table layout is shared.
- The seat card-count badge reads 0 during the reveal (hands are shown as open cards instead).
- No deal / swap-into-hand animation: a played card flies to its middle slot, the card taken from the middle just appears in the hand (not shown on the TV, it is face down). The refresh shows no flight either.
- Box timing not measured (swiftshader only); the Schwimmen table uses fewer cards than the 8-player Mau-Mau worst case.

## Unresolved questions

- All-tied rounds: spare everyone (current) or something else?
- Should the loser start the next round (some house rules) rather than rotating?
