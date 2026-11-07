# Card table + Mau-Mau — build report

Branch `feat/card-games` (worktree `../mediagram-cards`), from `feat/spieltisch`. No version bump, no changelog.

## What was built

Host (`crates/mediagram-game/src/`)
- `cards.rs` — `Suit`, `Rank`, `Card` (ts-rs exported), `deck_from(lowest)` (7 → 32, 6 → 36, 2 → 52), `shuffled`.
- `protocol_cards.rs` — the generic wire types (below).
- `maumau_rules.rs` — pure: `playable`, `Top`, `lowest_rank`, `points`, `auto_pick`, `most_held_suit`, `ranking`.
- `maumau.rs` — turn flow, `impl Game`, absent-player auto play (`auto_ms` after the later of leaving / turn start, like Heimlauf).
- `maumau_views.rs` — phone desk and TV scene.
- `GameKind::MauMau`, factory branch, lobby seat gate 2–8 (`CARDS_TOO_FEW`/`CARDS_TOO_MANY`).
- Tests: `cards_tests.rs`, `maumau_rules_tests.rs`, `maumau_tests.rs` (deal sizes, start 7/8/jack, play legality, 7 stack, 8 skip incl. 2-player, jack wish, Mau forgotten/said, Mau button only at 2 cards, drawn card play/keep/pass, reshuffle, empty pile, win + ranking + scores, absent auto play) and a seat-gate test in `room_settings_tests.rs`. 156 pass, clippy clean.

Client (`crates/mediagram-game/client/src/`)
- Phone: `phone/CardsDesk.tsx` + `cards-desk.css` (generic desk), `PhoneApp.tsx` case `cards`, lobby entry in `PhoneLobby.tsx`/`ui/lobby.ts`, shared card writing in `ui/card-face.ts`.
- TV: `tv/TvCards.tsx` (loading, OK → settings, 4 s cheer then `TvFinal` ranked, like `TvRace`) and `tv/cards/`: `cards-print.ts` (face atlas + count-number atlas), `cards-layout.ts`, `cards-world.ts`, `cards-motion.ts`, `cards-hud.ts`, `cards-texts.ts`, `cards-sound.ts`, `cards-stage.ts`. Test: `tests/cards-layout.test.ts`. 15 bun tests pass, typecheck and build clean.

## Wire types (all ts-rs, committed under `client/src/protocol/`)

- `PhoneScreen::Cards { desk: CardDesk }` — `CardDesk { prompt, info, table: DeskCard[], hand: DeskCard[], buttons: DeskButton[] }`; `DeskCard { card, enabled }`; `DeskButton { id, label, needs: ButtonNeeds, primary, suit }`; `ButtonNeeds = nothing | one_hand | some_hand | hand_and_table`.
- `Action::Cards { button, hand: u8[], table: u8 | null }` — indices into the desk's hand/table. Hands are kept in suit/rank order, which is the order sent.
- `TvScene::Cards { scene: CardsScene }` — `game`, `seats[{player, cards, open, marker}]`, `piles[{cards (last 3), count}]`, `deck`, `trump`, `turn`, `wish`, `note`, `banner {player, text}`, `events[{id, at, player, kind}]` (last 4; kind `play {card, pile}` / `draw {count}` / `reshuffle`), `ranking`.
- Phones stay thin: Mau-Mau knowledge lives in the host. Phase 02 can use `table` (selectable) and `hand_and_table` buttons, `open`/`marker` on seats, `trump`, several piles without a wire change.

## Decisions I made

- "Mau!" is a play variant, not a separate tap: with exactly 2 cards the desk offers "Mau! und spielen" (highlighted) and plain "Spielen". Plain play leaving 1 card → 1 penalty card now, banner "vergisst Mau – Strafkarte!". Applies after a draw too.
- Draw: with sevens owed the draw takes them all and ends the turn. Otherwise draw 1: if it fits the desk offers "Spielen"/"Behalten" (only the drawn card is enabled), else the turn passes at once.
- Start card: 7 → first player owes 2; 8 → first player skipped; jack → any suit. An 8 in a 2-player game gives the same player another turn (natural result of skipping).
- While sevens are owed only a seven may be played (a jack does not save from them).
- Last card may be a jack/7/8 and wins on the spot (no wish).
- Score for the final screens = players beaten (`n − 1 − place`), since `TvFinal`/phone Final print points. Hand points for the tie-break: A 11, 10, 9, 8, 7 face value, K 4, D 3, B 2, rest 0.
- Banner on the TV shows the last happening for 2.6 s (server clock), else "X ist dran". A penalty banner can be replaced by a wish banner if a jack is played at 2 cards without Mau (the penalty draw still animates).
- Seats: first seat at the bottom, clockwise. Tables of 6+ sit closer in with shorter fans (checked with 8 players).
- TV pre-draw: tags (active/idle × marker), banner texts per seat (17 each), notes "Zieh 2..8", suit badges, one atlas for 52 faces + back (1280×1568), one atlas of count numbers; `renderer.compile` at load with every pool card and badge visible. Cards are opaque rounded shapes with the art in the atlas (no alpha test), unlit, one blob per seat/pile for shadows, no shadow maps. A text not in `cards-texts.ts` still shows but is drawn at first use (a texture upload mid-game).
- Sound: Heimlauf's music beds, TV sound settings, winner jingle; card landings reuse the wooden `pawn-knock` takes at 0.4 gain.

## Verification

- `cargo clippy -p mediagram-game --all-targets -- -D warnings` clean; `cargo test -p mediagram-game` 156 pass.
- Client `bun run typecheck`, `bun test` (15), `bun run build` pass.
- Scripted phones vs `game-night-dev` (GAME_PORT=8091), full games to the end: 3 players (7 stack to "Zieh 4", draw of owed cards, 8 skip, jack wish with badge, Mau called, Mau forgotten, win, ceremony) and 8 players (52 cards, reshuffle seen). A real headless phone UI (412×915) also played a game to the end by tapping its own desk. Drivers live in the session scratchpad (`cards-driver.mjs`, `cards-phone.mjs`), not the repo.
- Screenshots (TV 960×540, phone 412×915): `plans/reports/card-table-261107-screens/` — `tv-3p-*` (jack wish with badge, seven stack with "Zieh 4", Mau forgotten, eight skip, ranking), `tv-8p-*`, `phone-*` (lobby, picked card, wish picker, waiting with wish info, final).

## Deferred / not done

- Dedicated card sounds (deal, flip, shuffle): no CC0 set (e.g. Kenney Casino Audio) at hand; knocks stand in.
- Reshuffle has no animation (the deck count jumps), only an event.
- Box measurement (swiftshader only here): worst case is 8 players, ~150 pooled card meshes, 136 banner textures; phase 06 should time it on the box.
- Headless screenshots lack the avatar `.glb` files in this fresh worktree (the ceremony logs 404s); unrelated to the table.
- Phone hand has no drag/fan gesture; tap selects, buttons confirm. A hand wider than the screen scrolls sideways.

## Unresolved questions

- Should a jack as the last card be forbidden (some house rules)? Currently allowed.
- Keep "score = players beaten" for the final strip, or show cards left instead?
