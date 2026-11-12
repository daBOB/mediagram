---
phase: 6
title: "Look, TV and phone pages"
status: done
priority: P1
effort: "6h"
dependencies: [5]
---

# Phase 6: Look, TV and phone pages

## Overview
First the look, chosen by the user from renders (memory: visual choices only with rendered, opened images); then
the TV page and the phone keypad in the client. The TV keeps the game-night family look (green field, cream card,
avatars, as Kopfnuss and Dreifelder) unless the user picks otherwise.

## 6a: Look (gate: nothing below is built before the user picks)
Three throwaway TV renders at 1920×1080 with a real fetched article (from the phase 2 dry run), each showing
intro, guessing and reveal:
- A: photo large on the left, name and "who has guessed" avatars on the right; reveal as a number line from 0 to
  twice the price, guesses as avatar pins, the price as a flag.
- B: photo centre stage like a showroom item on a pedestal; reveal as a list sorted by distance with points.
- C: a price-tag card theme (the article on a tag), reveal as avatars sliding onto a ruler.
Plus the phone keypad in two variants (calculator grid; big digits with euro and cent fields). Rendered with the
client's own fonts and tokens, opened for the user; the choice is recorded in this file.

## Choice (user, 2026-11-12)
From `visuals/preisfrage-look-choice.html`: **TV look A, shop window** (photo large on the left, name and
who-has-guessed avatars on the right; the reveal is a number line from 0 to twice the price with avatar pins and
a price flag, points under it) and **keypad K1, calculator** (one field, comma key, delete, send).

## 6b: TV page and phone keypad
- `client/src/tv/TvPreisfrage.tsx` + `tv/preisfrage/` (the picked look), `TvApp.tsx` routing, the price check
  date at the reveal ("Preis vom 12.11."), `TvFinal` ranked.
- `client/src/phone/PhonePriceKeypad.tsx`: digits, comma, delete, send; shows euros as typed (`29,99 €`); locked
  after send with "Dein Tipp: 29,99 €"; the clock; a small photo and the short name above the keys.
- Pure helpers with Vitest first: keypad input → cents (`"29,9"` → 2990, max two decimals, no leading zeros,
  cap 100 000 €); number-line placement (`preisfrage-line.test.ts`).
- Sounds: reuse Kopfnuss's cues where they fit (`tv/kopfnuss/kopfnuss-sound*.ts`); no new audio in this plan.

## Shelf art
Add Preisfrage's poster and backdrop prompts to `plans/261107-1509-games-poster-overview/image-prompts.md`
(paste-ready, title exactly "Preisfrage", no "Amazon" or show name anywhere); the user makes them; then
`ShelfGames.kt` and the two webp files as for the other games.

## Success Criteria
- [x] Look picked by the user from opened renders, recorded here.
- [x] Helper tests (`bun test`, `tests/preisfrage-price.test.ts`) seen failing first, then green; `bun run build` and typecheck clean.
- [x] Screenshots of intro, guessing, reveal, final and the phone keypad kept in `reports/` and shown to the user.

## As built
- `client/src/tv/TvPreisfrage.tsx` + `tv/preisfrage/preisfrage.css` (look A, fixed to the whole screen in vh/vw),
  `client/src/phone/PhonePriceKeypad.tsx` + `price-keypad.css` (K1), helpers in `client/src/ui/price.ts`
  (`euro`, `typeKey`, `toCents`, `linePlace`, `stackPins`: close guesses stand on longer stems so labels never
  overlap). The final is the shared ceremony (`TvFinal`), ranked by the running totals.
- Screenshots in `reports/` from a full game on the dev host over the 434-article dry run: a real phone page
  (Dana) and two scripted players; TV lobby, article alone, guessing, reveal, final; phone typed, sent,
  reveal, final.
- Not done: sound. Kopfnuss's player is bound to Kopfnuss's scene; reusing it needs it generalised first.
- Shelf art prompts: `shelf-art-prompts.md` in this folder (main's prompts file has moved on; move the section
  there at merge). The poster and backdrop are still to be made by the user.
