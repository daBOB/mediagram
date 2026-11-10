# Phase 05 — Phone controls

**Goal:** the phone is the player's own table edge in Grand Allee: what they
hold, what they may decide, how long they have, and a real bid in a debt
auction. Today it is the generic money sheet: text lines, a prompt, coloured
buttons; a debt auction offers five fixed shares of the property's value.

## What exists to build on

- `PhoneScreen::Sheet` (`src/protocol.rs`) — lines, prompt, options,
  `opens_at`, `picked`; answered with `Action::Choose`. Since 0.165.0 only
  Grand Allee uses it. Drawn by `client/src/phone/PhoneSheet.tsx`, styled by
  `.sheet*` in `client/src/styles.css`.
- `src/grand_allee/live_views.rs` — the phone view: `lines()` (Taler, Runde,
  one line per hotel, Vermögen), `label()` per option, `status()` while
  nobody asks this phone anything, the auction's bid buttons.
- `src/grand_allee/live_debts.rs` — `BID_PCTS` (10/25/50/75/100 % of value),
  marked `shortcut:`; `bid()` takes an option index.
- `PhoneScreen::Choice` and `ChoiceScreen.tsx` — the quiz's clock
  (`closes_at`, `.choice-clock`, `.meter`), to reuse on the sheet.
- The TV already marks candidate fields in the answer colours, so field
  choices ("Feld 5") match by colour; the TV HUD shows both clocks.
- Clocks: `ask_ms` 20 s, `auction_ms` 15 s (`live.rs`); when one runs out the
  house bot decides (`auto_index`).

## Sub-phases

| # | Sub-phase | Delivers |
|---|---|---|
| 5a | Clock on the phone | `closes_at` on `Sheet` (the ask's 20 s, the auction's 15 s); the sheet shows seconds left and the meter, as the quiz does. |
| 5b | Bid control | A sealed bid in steps of 100 from 100 to the bidder's cash (design: no minimum), set with − / + keys and quick shares of the value, sent with one "Bieten" key; "Passen" beside it. New `Action::Bid { amount, at }`; `BID_PCTS` and its shortcut go. |
| 5c | Hotel cards | The sheet's hotel lines become cards: hotel name in its colour, stages built as pips (x of y), rate per night, entrances, value. Cash and round stay at the top. |
| 5d | Doppelt route | A Doppelt permit the mover cannot pay asks: auction the hotel in question (the permit costs nothing, today's only way) or auction other properties until it is paid, then build. The house keeps choosing the first, as the simulator does. |
| 5e | Check and review | Phone screenshots at phone width for every screen kind (turn, field choice, Rathaus, auction, waiting, out, final), tests green, version bump, commit. |

Each sub-phase ends with tests green; 5e ends with screenshots for the
user's review and one version bump and commit for the phase.

## Decisions to confirm (user)

1. Bid control: − / + keys in 100 and 1 000 steps plus quick shares of the
   value (25/50/75/100 %), then "Bieten". Alternatives: a slider, or a
   number pad.
2. Hotel cards: text cards with a colour band per hotel. Alternative: with a
   small picture of the hotel rendered from its baked model (ten more
   images to ship).
3. Doppelt route: offer the choice, as the design's "may put the plot in
   question up for auction instead" reads. Alternative: keep the automatic
   auction.

All three confirmed by the user on 2026-11-09 as recommended (− / + keys and
shares, text cards, offer the choice), from the rendered mockup
`reports/phone-controls/phone-controls-choices-mockup.png`.

## Out of scope

- A map of the board on the phone (the TV marks fields in button colours).
- Sounds or haptics beyond today's tap buzz.
- Onboarding (phase 06).

## Todo

- [x] 5a clock on the phone — `Sheet.closes_at`, the quiz's clock class and meter
- [x] 5b bid control — `Action::Bid`, `BidRange`, `SheetBid.tsx`, `sheet-bid-math.ts` (tested); starts at half the value; `BID_PCTS` gone
- [x] 5c hotel cards — `HotelCard`, colours in `live_views.rs` (`HOTEL_COLORS`); `Sheet.picked` dropped (never set since Bauherr)
- [x] 5d Doppelt route — `Opt::Raise`, `GrandAlleeGame.building`; others short → the hotel in question is auctioned after all; `live_permit_tests.rs`
- [x] 5e check and review (0.166.0) — 319 Rust tests, 102 client tests, clippy and tsc clean; the six sheet screens drawn by the real component in `reports/phone-controls/phone-sheet-six-screens.jpg`. Not yet seen on a real phone in a live game.
