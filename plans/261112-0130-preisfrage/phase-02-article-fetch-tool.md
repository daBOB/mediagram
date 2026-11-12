---
phase: 2
title: "Article fetch tool"
status: pending
priority: P1
effort: "6h"
dependencies: [1]
---

# Phase 2: Article fetch tool

## Overview
A bun tool the user runs by hand on the PC (user, 2026-11-12): `cd tools/preisfrage-articles && bun run fetch`.
It reads odd amazon.de articles from search results with Playwright and writes the list and photos to the home
cache server. Built from the search spike (`prototype/preisfrage`, `e310c8ff`), which read 16 pages with no robot
check. Not part of any app; no version line.

## What it does
1. Reads `cache_server` and `cache_server_token` from `~/.config/mediagram/config.toml` (as `mediagram posters`
   does; `scripts/setup-funstation.sh` writes them). Missing → exits with a plain message.
2. 16 searches: terms drawn from `terms.json` (curiosities, gag gifts, odd inventions, kitchen/garden/car/office/
   pet/bathroom/camping gadgets), page 1-4 at random, 40 % sorted by newest, price band per search
   round-robin over 5-20 / 20-50 / 50-150 / 150-500 € (`rh=p_36:<cents>-<cents>`).
3. Per result: skips sponsored, books/Kindle/films (format words), 0 € prices, titles on the adult blocklist
   (`blocklist.json`), duplicates. Keeps ASIN, full title, short name, price in cents, review count, band, term.
4. Short name: the title up to its first ` | `, ` - `, `,` or `(`, at most 6 words / 48 characters.
5. Photo: the result's image URL with the size suffix replaced by `_AC_SL800_` (800 px JPEG from Amazon),
   downloaded and PUT to `/v1/game/preisfrage/photos-{asin}.jpg`.
6. Writes `articles.json` last (`{ version: 1, fetched, articles: [...] }`), only when at least 60 articles were
   kept, so a run cut short leaves last week's list in place.
7. Polite: one page every 4-8 s, cookies declined, stops at the first robot check (screenshot kept, exit 2).
   `--dry-run DIR` writes list and photos to a folder instead (for the dev host and for looking at them).

## Tests first (`bun test`)
- `parse.test.ts`: saved search pages in `fixtures/` (one plain, one newest-sorted, one with sponsored, one book
  result, one robot-check page) → expected articles, skips, and robot-check detection.
- `filters.test.ts`: media words, 0 €, adult blocklist, dedupe; `names.test.ts`: short names from real titles.
- `sign.test.ts`: the PUT signature reproduces the vector in `docs/running-the-player.md` exactly.
- `bands.test.ts`: 16 searches cover each band four times.

## Related Code Files
- Create: `tools/preisfrage-articles/{package.json,fetch.ts,search.ts,parse.ts,filters.ts,names.ts,upload.ts,
  sign.ts,config.ts,terms.json,blocklist.json}`, tests and `fixtures/`
- Docs: `docs/running-the-player.md` § "Preisfrage articles" (install `playwright-core`, Chromium, the command,
  what a robot check means, how often to run it)

## Success Criteria
- [ ] Tests seen failing first, then green.
- [ ] One real run on the PC: ≥ 200 articles, all four bands present, list and photos readable with `curl`
      from the cache server; result page screenshot shown to the user.
- [ ] No file over 200 lines.

## Risks
- Amazon changes its result markup → parse tests on saved pages make the break obvious; fixtures are refreshed
  from a real run when it happens.
- A robot check at home → the run stops, last week's list stays; trying again later or from another network.
- Amazon's terms forbid robots (user accepted, private use only).
