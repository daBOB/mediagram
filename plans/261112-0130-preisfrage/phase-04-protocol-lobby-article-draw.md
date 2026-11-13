---
phase: 4
title: "Protocol, lobby, article draw"
status: done
priority: P1
effort: "4h"
dependencies: [3]
---

# Phase 4: Protocol, lobby, article draw

## Overview
The game's wire shapes, its place in the catalogue and lobby, and the draw of 6 articles per game. Follows
Dreifelder's first phase (`plans/261109-1417-dreifelder-elimination-quiz/phase-01-*`).

## Tests first
- `catalogue_tests.rs`: Preisfrage listed as "Preisfrage", 1-8 players.
- `room_settings_tests.rs`: blocked with the kids lock ("Preisfrage ist nichts für Kinder", like Kopfnuss);
  blocked with an empty list ("Preisfrage braucht Artikel: am PC `bun run fetch` starten").
- `preisfrage_draw_tests.rs` (seeded RNG): 6 articles, one per band plus two from random bands; articles with
  fewer than 20 reviews first within a band; none twice in a night until the list runs out; a band with no
  articles is filled from the nearest band; fewer than 6 articles in the list → as many as there are, ≥ 3 or blocked.

## Architecture
- `protocol_settings.rs`: `GameKind::Preisfrage`.
- `protocol_preisfrage.rs`: `Article { asin, name, photo }` (photo = `/image/article/{asin}`), TV scene
  `PreisfrageScene { index, total, article, stage }` with stages `Intro`, `Guessing { closes_at, guessed }`,
  `Reveal { price_cents, checked, rows: Vec<GuessRow> }`; phone `PhoneScreen::PriceKeypad { article, closes_at,
  sent: Option<u32> }`; action `Action::GuessPrice { cents }`.
- `preisfrage_config.rs`: rounds 6, intro 3 s, guess 20 s, reveal 7 s, exact bonus 500 (data, not code).
- `preisfrage_draw.rs`: the draw above over the night's list; `used` kept on the host like `QuestionSupply`.
- `catalogue.rs`, `factory.rs`, `room_settings.rs::blocked`, `lib.rs`.
- Regenerate and commit the ts-rs types.

## Related Code Files
- Create: `src/protocol_preisfrage.rs`, `src/preisfrage_config.rs`, `src/preisfrage_draw.rs` (+ tests)
- Modify: `src/protocol_settings.rs`, `src/protocol.rs`, `src/catalogue.rs`, `src/factory.rs`,
  `src/room_settings.rs` (+ tests), `src/lib.rs`, `client/src/protocol/*` (generated), `client/src/ui/lobby.ts`

## Success Criteria
- [x] Tests seen failing first, then green; existing suites green; protocol diff committed.
- [x] Lobby on the dev host shows Preisfrage, blocked with the right reason with no list / with kids lock.

## As built
- Lobby texts: "Preisfrage gibt es nicht im Kindermodus." (the wording Kopfnuss and Dreifelder use) and
  "Preisfrage braucht Artikel: am PC „bun run fetch“ starten."; readiness is `Readiness.preisfrage`, at least
  three articles. Checked by `room_settings_tests`, not on the dev host's screen: the TV page comes in phase 6.
- One `Guessing { opens_at, closes_at, guessed }` stage covers the intro (before `opens_at`), as Dreifelder does;
  no separate `Intro`. The keypad carries `opens_at`, `closes_at`, `sent` and `max_cents`.
- `protocol.rs` was at 199 lines: the TV view types moved to `protocol_tv.rs`.
- Until phase 6 the TV renders nothing for a Preisfrage scene (`TvApp.tsx` routes the quiz explicitly).
