---
title: "Preisfrage: guess the price of odd Amazon articles"
description: "1-8 players see an odd amazon.de article on the TV, type a price on the phone; closest either way scores by accuracy. Articles come from a fetch run by hand on the PC, kept on the home cache server."
status: pending
priority: P2
mode: tdd
created: 2026-11-12
branch: feat/preisfrage
base: spieltisch-games 2647f381 (main c331dd23, Spieltisch 0.175.0, media 0.176.2)
blockedBy: []
blocks: []
---

# Preisfrage

Original genre-equivalent of the TV show's price guess: no show name, art or look.
Brainstorm: `plans/reports/brainstorm-261109-1417-dreifelder-and-price-guess-report.md`.
Prototypes (branch `prototype/preisfrage`, never merged): rules `be678b54`, bestseller fetch `c0884d63`,
search fetch `e310c8ff`.

## Rules (decided with the rules prototype, user 2026-11-12)
- 1-8 players. 6 articles per game (the show's number). TV: photo + short name; phone: price keypad.
- One guess per article, locked once sent. 3 s intro, 20 s to guess; the round closes early when all have guessed.
- **Closest either way wins.** Points per guess: `round(1000 × max(0, 1 − |guess − price| ÷ price))`, +500 for the
  exact cent. Equal guesses share. No guess = 0. Highest total wins; then the shared ceremony.

## Articles (decided with the fetch spikes, user 2026-11-12)
- **Odd and unknown, not bestsellers** (user): amazon.de search results for curiosity terms, a random results page,
  40 % sorted by newest, sponsored skipped; review count kept, few reviews preferred.
- A price band per search (5-20, 20-50, 50-150, 150-500 €) so a game gets cheap and dear articles.
- Skipped: books/Kindle/films, 0 € prices, titles on an adult-word blocklist.
- **Fetched by hand on the user's PC** (user): `bun run fetch` in `tools/preisfrage-articles`, Playwright + the
  installed Chromium (plain HTTP gets Amazon's 503 "automated access"). It writes the list and photos to the home
  cache server; the game reads them from there.
- Amazon's terms forbid robots; private game nights only, never published (user's choice over eBay/Keepa).

## Decisions (my calls unless marked)
1. **Kids lock: Preisfrage is blocked** like Kopfnuss and Dreifelder (user).
2. Cache server gets a third blob kind, `game/{game}/{name}`: GET open on the LAN like the others, PUT signed.
3. Spieltisch never holds the cache token: the media app's `/night` row names the cache server's address;
   Spieltisch's Kotlin side GETs `articles.json` at night start and hands it to the host as JSON (as it does the
   titles); photos go through the existing `ImageLookup` hook with a new `ImageKind::Article`.
4. No list (no cache server, nothing fetched yet, unreadable JSON) → Preisfrage blocked in the lobby with the reason.
5. Per game: 6 articles, one from each band plus two random bands; reviews < 20 first; no article twice in a night.
6. The reveal shows the price check date ("Preis vom 12.11."), since prices are up to a week old or more.
7. TV look decided from rendered options before the TV page is built (memory: user wants renders).

## Phases
| # | Phase | Line | Status |
|---|-------|------|--------|
| 1 | [Cache server: game blobs](phase-01-cache-server-game-blobs.md) | media | done |
| 2 | [Article fetch tool](phase-02-article-fetch-tool.md) | tools | done |
| 3 | [Spieltisch gets the articles](phase-03-spieltisch-gets-the-articles.md) | both | done |
| 4 | [Protocol, lobby, article draw](phase-04-protocol-lobby-article-draw.md) | Spieltisch | pending |
| 5 | [Rules and game state](phase-05-rules-and-game-state.md) | Spieltisch | pending |
| 6 | [Look, TV and phone pages](phase-06-look-tv-and-phone-pages.md) | Spieltisch | pending |
| 7 | [Verification and release](phase-07-verification-and-release.md) | both | pending |

Order: 1 → 2 (needs the blob routes) · 3 needs 1 · 4 → 5 → 6 → 7. Phase 2 can run beside 4-5.
Each code phase: tests first, watch them fail, then implement.

## Key dependencies
`mediagram-cache` blob store and routes; the `token::sign` vector in `docs/running-the-player.md`;
`GameNightProvider.kt` (`/night`), `NightSession.kt`, `spieltisch-core::start_night`; `ImageLookup`;
`room_settings::blocked`; `catalogue.rs`; `factory.rs`; `TvFinal` ranked; `tools/smoke.mjs`.

## Open questions
- Decisions 2-7 were not asked about; challenge any that is wrong.
- Poster and backdrop for the shelf: prompts to add to `plans/261107-1509-games-poster-overview/image-prompts.md`.
- Version numbers are taken at merge time: `feat/fluechtig-ferry` already holds Spieltisch 0.176.0.
