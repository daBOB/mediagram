# Phase 06 — The card table (Spieltisch)

## Problem

Mau-Mau, Schwimmen and Durak each re-implement seats (`player_at`/`seat_of`:
`maumau.rs:101-107`, `schwimmen.rs:86-93`, `durak_seats.rs:13-19`), the event
ring (4 at `maumau_moves.rs:32` and `schwimmen.rs:111`, 6 at
`durak_seats.rs:60`), the banner (`say`; Mau-Mau alone queues its wish after a
"Mau" banner, `maumau_moves.rs:136-139`), `next_event`, RNG seeding, a
`button()` helper (`maumau_views.rs:19`, `durak_views.rs:11`), and test helpers.
Schwimmen borrows `sort_hand` from `maumau_rules`. Tests set up deals by
writing `pub(crate)` fields (`maumau_tests.rs:23-34`, `schwimmen_tests.rs:23-38`).

Facts: Durak's ring of 6 covers one `resolve` emitting a `Draw` per refilling
seat (`durak_flow.rs:112-119`, up to 6 seats); the TV dedupes events by id
(`client/src/tv/cards/cards-motion.ts:41-55`, `cards-sound.ts:75-81`). Turn
order is not shareable: Durak's actor comes from its stage.

## Decided

- Each card game holds a card table value (seats, event ring, banner) and
  implements `Game` itself; the table does not own turn order.
- The house check goes through the Ask module (phase 05).
- One ring size, 6, for all three — its own commit first; nothing visible
  changes (TV dedupes by id).
- Banners: the table offers replace and queue-after-current. Mau-Mau queues its
  wish; Schwimmen and Durak keep replacing. No behaviour change.
- Hand sorting moves to the table (or a shared card module), not Mau-Mau's rules.
- Tests: each card game gets a test-only constructor that deals fixed hands,
  deck, discard and turn; one shared helper drives any card game through the
  `Game` trait. No test writes game fields.
- End screens stay where they are until phase 08 moves them to the Room.

## Steps

- [x] Commit 1: ring of 6 for Mau-Mau and Schwimmen (patch bump).
- [x] Card table module; the three games hold it; duplicates deleted.
      `card_table.rs`: seats, the no-clock ask, the ring, `say` (replace) and
      `say_next` (queue behind the banner on show), `said`, `banner(now, hold)`;
      `sort_hand` and `button()` moved there. Mau-Mau's `cfg` field went (its
      only use was `banner_ms`, now the table's). RNG seeding stays in each
      game: the same one-liner is in ten games, not a card-table concern.
- [x] Dealt constructors + shared helper; rewrite setups; delete copied helpers.
      `dealt(..)` per game (seat 0 to move); `card_game_driver.rs` drives any
      card game. Schwimmen's two-round tests became two dealt games (a seat
      dealt no cards is already out); Durak's six-attack cap is played out.
      Every TV scene and phone screen of 228 seeded games (2–8 seats, leaving,
      returning, random taps, late joiner) recorded before and after: identical
      byte for byte (279 MB, same sha256); the probe was deleted.
- [x] `cargo test -p mediagram-game`; client tests unaffected (no protocol change).
- [x] Spieltisch patch bump; commit; merge; check.sh.

## Done when

A new card game is its rules, stages and house moves on top of the table.
