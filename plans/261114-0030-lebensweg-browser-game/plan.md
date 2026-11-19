# Lebensweg — the playable game, first in the browser

Branch `feat/lebensweg` (worktree `mediagram-lebensweg-game`, from `spieltisch-games` 0.182.0).
Look: prototype on `prototype/lebensweg` (`plans/261112-0055-lebensweg/`, commit `f157740e`).

## Decision (user, 2026-11-14)

"can we start it as browser game ?" → a **Spieltisch game** like Grand Allee: the Rust host keeps the
rules, the TV page (`/tv`) shows the Lebensberg, every player spins and picks on their phone, the lobby
avatars drive the cars. The TV page is played in a desktop browser first (live light runs on a PC
graphics card); the TV box follows after the bake, with no rewrite. Not chosen: a one-browser hot-seat
game (would be a second build later).

## What already exists and is reused

- Host, lobby, avatars, seats, absent players' turns (`house_at`), game log: `crates/mediagram-game/src/`.
- Phone: `PhoneScreen::Sheet` (money lines + options + clock), as Grand Allee uses it. No new phone screen.
- TV: the prototype's board code (terrain, road, waterfall, district, cars with avatars, camera, HUD).
- Template for a money board game: `src/grand_allee/` (rules, bots, live loop, views) and `bin/grand-allee-sim.rs`.

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | [Rules and numbers](phase-01-rules-and-numbers.md): the original's rules, money × 1.3, Rust rules module with tests | redo for 2013 |
| 2 | [Balance with bots](phase-02-balance-with-bots.md): `lebensweg-sim`, original board vs ours | redo for 2013 |
| 3 | [Fields](phase-03-fields.md): a title, story and tile look for every field; staging of the STOPs and forks | waits for the 2013 board |
| 4 | [Cards](phase-04-cards.md): careers, salaries, houses, stocks, life tokens: texts, looks, where they show | pending |
| 5 | [Event animations](phase-05-event-animations.md): a storyboard per event, key frames rendered, game length re-measured | pending |
| 6 | [Screen mockups](phase-06-screen-mockups.md): TV and phone rendered, user picks before views are written | pending |
| 7 | [TV board and wiring](phase-07-tv-board-and-wiring.md): prototype ported into modules, game registered | pending |
| 8 | [Play test in the browser](phase-08-play-test-in-browser.md): full game with phones, screenshots, QA | pending |

Concept first (user, 2026-11-14): "We are still a long way from finishing the concept phase. We still need to
define the fields and cards, as well as the event animations." Phases 3–5 are that concept; no views before it.

The original is the 2013 German edition (user, 2026-11-14: "we need to get closer to Spiel des Lebens", with its
rules sheet). Phases 1–2 were done on the 2000 US edition: done there, to redo on the 2013 rules and board.
Rules and what changes: `research/spiel-des-lebens-2013-rules.md`. Board: 151 spaces read from two board photos
(`research/spiel-des-lebens-2013-board-from-photos.md`, ~30 rows partial). Cards: only the life tiles found
(`research/spiel-des-lebens-2013-cards-from-photos.md`, 2015 printing); career, house and investment values missing.

## Key dependencies

- Phase 1 needs the research (`research/original-rules-and-numbers.md`).
- Phase 7 waits for the user's picks in phases 3–6 (standing rule: renders before views).
- Version: Spieltisch line, next free minor at merge time (above 0.191.0: main and the funstation run it), all five manifests; check main's version right before committing.

## Out of scope (later)

- TV box: baked light, KTX2 atlases, merged meshes (open item of the look prototype).
- A shared board-graph engine: Lebensweg's graph stays in its own module until a second graph board needs it.
- Ageing looks on the drivers (user: "no forget the ageing").

## Open questions

- Amounts: per-field amounts as the original, one-off amounts × 1.3 (bots: no further tuning needed).
- Game length: bots at an assumed pace: 4 players 29 min (original 25), 6 players 47 (39).
- Spinner gap: 5 % (IT-Beratung wins 1.34× its share) or 2 % (1.06×)?
