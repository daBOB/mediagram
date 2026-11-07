# Phase 01 — Card table + Mau-Mau

**Priority:** first card work; Schwimmen and Durak build on it. **Status:** done, merged 2026-11-07 02:46.
Context: `../261106-1808-card-games/plan.md` (architecture section is the base),
Heimlauf as the pattern: `../261106-0907-race-game/plan.md`, `src/race*.rs`,
`client/src/tv/TvRace.tsx`, `client/src/tv/race/*`.

## What it is

One shared card table — a deck, a hand only its owner sees (phone), cards on the
TV table, turns, a winner — and Mau-Mau on it. The table must be generic enough
that Schwimmen (swap with three open cards in the middle) and Durak (attack and
defend pairs, trump under the deck) are only rules + views on top.

## Shape (guidance; finalize in the code)

Host (`crates/mediagram-game/src/`):
- `cards.rs` — `Suit` (Kreuz, Pik, Herz, Karo), `Rank` (2–10, Bube, Dame, König,
  Ass), `Card`, `Deck::from_rank(lowest)` (7 → 32, 6 → 36, 2 → 52), seeded
  shuffle. Pure. `cards_tests.rs`.
- `maumau_rules.rs` (pure: legal plays, 7 stacking, 8 skip, jack wish, Mau
  check), `maumau.rs` (turn flow, `impl Game`, absent players play themselves
  after `auto_ms` like `race.rs` `absent_since`), `maumau_views.rs`.
- `protocol_cards.rs` — generic wire types, e.g.
  - phone: `PhoneScreen::Cards { desk: CardDesk }` — own hand, which hand cards
    are selectable, optional selectable table cards, buttons (label + what they
    need: nothing / one or more hand cards / hand + table card), prompt, info
    line. Phones stay thin: they know nothing about Mau-Mau.
  - action: `Action::Cards { button, hand: Vec<u8>, table: Option<u8> }`.
  - TV: `TvScene::Cards { scene: CardsScene }` — game label, seats (player,
    card count, optional face-up cards, optional lives/marker), centre stacks
    (face-up, a stack can hold an attack+defence pair), face-down deck count,
    optional trump card, note (wished suit, "Zieh 4", "Mau vergessen!"), whose
    turn, the last event with server times so the TV can animate it, final ranking.
- `GameKind::MauMau` (one kind per card game), factory branch, seat gate 2–8 in
  `room_settings.rs::blocked()`, lobby entry + badge (`client/src/ui/lobby.ts`,
  `PhoneLobby.tsx`, `TvLobby.tsx`).

Mau-Mau rules (user-confirmed): 32 cards (7–A) for 2–5 players, 52 for 6–8; deal 5;
turn up a start card (its 7/8 effect hits the first player; a start Jack allows any
suit). Match suit or rank. 7: next draws 2 unless they play a 7 (stacks +2). 8: next
is skipped. Bube: on anything, wishes a suit (phone shows the four suits). Can't or
won't play: draw one; if it fits you may play it, else the turn passes. Mau: the
phone offers "Mau!" with the second-to-last card; playing it without → the host
deals one penalty card and the TV says so. Empty draw pile → reshuffle the
discards under the top card. First empty hand wins; the rest rank by cards left,
then by hand points. Absent player: first legal card (non-special first), wishes
their most-held suit, always says Mau.

TV (`client/src/tv/cards/`, `TvCards.tsx`): three.js, Heimlauf's lacquered cherry
table (`client/public/race/table-lacquered-cherry.jpg`), card faces drawn once on a
canvas atlas at load (own design, French suits, German indices B/D/K/A), card
backs, seats round the table with name/avatar head and a fanned back-count,
discard pile + draw pile, wished-suit marker, turn highlight, animated card
moves (seat → pile, deck → seat). `renderer.compile` at load. Sound: reuse
Heimlauf's music beds and the TV sound settings; add card cues only if CC0 ones
are at hand (Kenney Casino Audio), else note it as deferred.

Phone (`client/src/phone/`): one new desk screen — hand fanned at the bottom,
tap to select, buttons above; selectable table cards when the desk has them.
Must work one-handed on a phone in portrait.

## Todo

- [ ] cards.rs + tests
- [ ] protocol_cards.rs + PhoneScreen/TvScene/Action variants; regenerate ts-rs
      types (`cargo test` writes `client/src/protocol/`) and commit them
- [ ] Mau-Mau rules + flow + views + tests (7 stacking, 8 skip, jack wish, Mau
      penalty, reshuffle, absent auto-play, ranking)
- [ ] GameKind, factory, seat gate, lobby entry
- [ ] phone desk screen
- [ ] TV card table
- [ ] Verify (below)

## Success criteria

- clippy clean, `cargo test -p mediagram-game`, client `bun run typecheck && bun test`
  and build all pass.
- Three scripted phones against `game-night-dev` play a full Mau-Mau to the end
  (pattern: `race-driver.mjs`/`race-phone.mjs` in
  `/tmp/claude-1000/-home-andre-Workspace-mediagram-spieltisch/521b522b-2224-4a59-953f-6743ddaf2420/scratchpad/`);
  TV and phone screenshots from a desktop browser at 960×540 / phone size.
- Box measurement is phase 06's job, not this one's.
