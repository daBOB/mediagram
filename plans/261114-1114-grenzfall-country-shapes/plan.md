# Grenzfall — guess the country by its outline

Status: done (Spieltisch 0.185.0, branch `feat/grenzfall`)

## Why
The user's first minigame list (Kopfnuss, country by its shape, Preisfrage, a bomb passed until one is left)
was half built: the country-shape game and the bomb game were never started. On 2026-11-14 the user asked for
both, the country-shape game first.

## User decisions (2026-11-14)
- Answer with **4 choices on the phone**, Buzzer-Quiz scoring (faster right answer, more points).
- **All countries, getting harder** over the game.

## Design
- Not a new engine: `GameKind::Grenzfall` builds a `Quiz` whose questions carry the outline as the TV picture
  (`Question.image`, an SVG data URL). Phones, TV card, reveal and podium are the quiz's.
- `src/grenzfall.rs`: 10 rounds by tier `[1,1,1,2,2,2,2,3,3,3]`; wrong answers by closeness (tier 1: other
  famous shapes; tier 2: same continent; tier 3: same subregion, then continent). Tonight's history key
  `land:<ISO>`, a tier starts over when used up.
- Data: `questions/laender.json` from `scripts/build_country_shapes.py` (Natural Earth 1:50m v5.1.2, public
  domain). UN members; disputed states left out; Somaliland/N. Cyprus/Åland merged; overseas parts dropped by
  a home-territory rule (biggest parts holding 80 % of land, plus land within 25 % of their size or 2°);
  28 blob/atoll states left out. Mercator per country, 1000-unit box, Douglas-Peucker at 1.2 units: 294 KB.
- Tiers hand-picked in the script: 30 famous, 68 middle, 68 hard.

## Steps
- [x] Outline builder and data, contact sheet checked by eye (US lost Hawaii only after one reference latitude)
- [x] Game kind, catalogue, factory, question builder, tests (`grenzfall_tests.rs`)
- [x] Shelf entry, TV outline size, phone keys hyphenate long names
- [x] Browser walk: 10 rounds, two phones, reveal and podium (`reports/`)
- [x] Shelf poster and backdrop (BudgetPixel gpt-image-2.6, medium)
- [x] Version 0.185.0, changelog

## Found on the way
- The latency spike merged in 0.184.0 drew "n=0" over every TV page and showed "Latenztest" in every
  controller app; fixed separately as 0.184.1.

## Next
- The bomb game (same list), in its own worktree.
- Not done: kids mode does not change Grenzfall (shapes are harmless; the hard tier may be too hard for kids).
