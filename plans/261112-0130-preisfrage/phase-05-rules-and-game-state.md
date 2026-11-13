---
phase: 5
title: "Rules and game state"
status: done
priority: P1
effort: "5h"
dependencies: [4]
---

# Phase 5: Rules and game state

## Overview
The rules as pure functions, lifted from the rules prototype's module (`prototype/preisfrage`, `be678b54`,
`roundResult` and `ranking`) with the user's choice fixed: closest either way, scored by accuracy. Then the game's
state machine in the room, like `dreifelder.rs`.

## Tests first (`preisfrage_rules_tests.rs`)
- Points: `round(1000 × max(0, 1 − |g − p| ÷ p))`; exact cent +500; a guess at twice the price or more = 0;
  a guess of 1 cent on a 500 € article ≈ 0, never negative.
- Winner label: smallest distance either side; equal distances (one above, one below) share it.
- No guess → 0 points, no winner label; nobody guessed → no winner, the round still ends.
- Ranking over 6 articles: highest total first, equal totals share a place; the prototype's "steady against lucky"
  walkthrough gives Ana first (4508 vs 2916 vs 2914).

## Tests first (`preisfrage_tests.rs`, host clock)
- Intro 3 s → guessing 20 s → reveal 7 s → next article; after the 6th, the final ranking and ceremony.
- A guess is taken once: a second `GuessPrice` is refused; after the clock, refused; 0 or over 100 000 € refused.
- The round closes as soon as every connected player has guessed; a player who left does not hold it open.
- A player who joins mid-game sits out the current article and plays from the next.
- TV views never show a guess before the reveal (only who has guessed); phone views show the player their own.

## Architecture
- `preisfrage_rules.rs`: `fn points(guess, price) -> u32`, `fn round_result(price, guesses) -> Vec<GuessRow>`,
  `fn ranking(totals) -> Vec<(PlayerId, u32, place)>`.
- `preisfrage.rs`: `PreisfrageGame` implements `Game` (stage, clock, `used`), as `DreifelderGame` does.
- `preisfrage_views.rs`: TV scene and phone screen from the state; final via `TvFinal` ranked.

## Related Code Files
- Create: `src/preisfrage_rules.rs`, `src/preisfrage.rs`, `src/preisfrage_views.rs` (+ `_tests.rs` each)
- Modify: `src/factory.rs`, `src/lib.rs`, `tools/smoke.mjs` (a 3-phone Preisfrage run)

## Success Criteria
- [x] Every rule above seen failing first, then green; all suites green.
- [x] Smoke run: three scripted phones play 6 articles to the ranking on the dev host with `--articles`.
- [x] No file over 200 lines.

## As built
- Smoke run on 2026-11-12 against the dev host over the 434-article dry run: six articles to the final and back
  to the lobby; Ana (to the cent) 9000, Ben (10 % over) 5400, Cem (skips the first, then half) 2500, equal to
  the prototype's JavaScript formula. `bun crates/mediagram-game/tools/smoke.mjs ws://127.0.0.1:PORT/ws
  --game preisfrage --articles DIR`.
- Points are worked out in whole numbers, rounding half up as `Math.round` did.
