# Phase 3 — Fields

Status: in progress (texts drafted, `concept/fields.md`) · After phase 2 (effects and amounts are fixed by the balance)

## Context
- Now: `src/lebensweg/spaces.rs` holds the original's 142 spaces with one-line placeholder texts. `stretch.rs`
  doubles 44 of them, so the 1.3 board has 192 fields, and a copy shows the same text twice.
- `board.rs` (`PARTS`): the stretches, their ages, the three forks and the STOP fields.
- Places on the Lebensberg (look prototype): school at Start, uni (Studium), workshop (Ausbildung), office district
  (Beruf), chapel (Hochzeit), house (Hauskauf), family (Kinder), house / tent (Familienweg / Abenteuerweg),
  mine (Riskanter Weg), waterfall, snow toward the top, Villa am See / Landhaus at the end.
- Fixed by the balance: each field's effect, amount and position. This phase decides what a field says and how it
  looks, not what it does.

## Deliver: `concept/fields.md`, one row per field (192)
- [x] Stretch, age, effect and amount, who gets the money (bank or the player with the perk).
- [x] Title (up to 3 words, readable on the TV) and one sentence of story. Copies get texts of their own.
- [ ] Tile on the board: colour and icon per kind (payday, life token, baby, money in, money out, career fee,
      insurance case, special). The text shows when a car lands.
- [ ] A small prop beside the field where the place suggests one.
- [x] Own staging for the STOPs and forks: Berufswahl, Hochzeit, Hauskauf, the three forks, Ruhestand.
- [ ] One rendered sheet of the tile looks, opened; user picks.

## Then
`spaces.rs` gets the approved texts with a text per copy; tests and the sim rerun (results must not move).

## Open
- Tone (user, 2026-11-14): warm, set on the mountain.
- Copies sit next to their original (concept § Open points 1).
