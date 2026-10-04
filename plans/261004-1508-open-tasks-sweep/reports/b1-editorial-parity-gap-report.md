# B1 gap report — `260926-1330` Android editorial parity, phases 4/5/6/8/7

2026-10-04, research subagent against `main` (after 0.99.12).

**Headline:** phases 4, 5, 6, 8 shipped in **0.66.0** (`280235b3`; TV fixes `cf142d28`,
`906fdc41`); only `plan.md` was never updated. Still owed: visual/wording gaps vs today's
web, the TV title/series/browse pages (`260929-0215` left them unchanged), phase 7.

## Status by requirement (abridged)

| Ph | Area | Status | What remains |
|---|---|---|---|
| 4 | Film spread | PARTIAL | facts line lacks first 3 genres (`film-page.js:36`); tablet draws the phone's stacked layout — web >900px is a full-bleed side spread (`title-page.css:52-116`) |
| 4 | Film tabs | PARTIAL | Details lacks "Audio languages" (`MediaSet.alang` exists); crew names plain text, web links them (`cast.js:58-65`) |
| 4 | Series page | PARTIAL | About lacks "Audio" (`series-summary.js:154-158`); no provider totals/air dates ("8 of 16") — core holds `first_air`/`total_*`, `TitleInfo` doesn't; "2 seasons" vs web "two seasons" |
| 4 | Tabs keep selection, tests | DONE | — |
| 5 | Movies dept | PARTIAL | genre row = 140dp posters, web 16:8 tiles with name over art; no "All N films →" pill |
| 5 | Series/Tutorials dept | PARTIAL | "All courses" is a grid; web a LIST |
| 5 | Collections / franchise | PARTIAL | franchises = row of small posters, lists = text rows; web: wrapping 4:3 cards, name over art, list cards use first title's art, "New list" pill; franchise overview below hero, web inside |
| 5 | Person page | DONE | wording "N titles in your library" → web "N in your library" |
| 5 | Grouped search | DONE | collection captions say "titles", web "films" for a franchise; collections as posters, web cards |
| 5 | Latest | PARTIAL | headings "Latest films/…" → web "Movies/Series/Tutorials"; courses grid → web LIST |
| 5 | Genres index | PARTIAL | portrait posters → web 16:9 tiles, name over art |
| 5 | Cross-cutting | PARTIAL | numerals → web spells counts ≤ 20 (`format.js:162-172`); page headings smaller than web's display heading + caps subline + rule; no Robolectric tests for Person/Franchise/Genres/Latest |
| 6 | TV magazine home, departments | DONE | 0.83.0, 0.84.0 |
| 6 | TV title + series pages | PARTIAL | no spread/backdrop, no My List pill, tabs above a poster header, Details one text line, About only 3 facts, Episodes a wall of season posters, Similar hidden when empty, Up lands on nearest tab not selected |
| 6 | TV person/franchise/collections/genres/search | PARTIAL | rect portrait, one mixed wall; franchise no hero/year span/"In release order"; poster cards; search heading "Films" vs chip "Movies" |
| 8 | Appearance | DONE | 0.66.0 + Artwork 0.69.1 |
| 7 | check.sh, device walk, review, docs | PARTIAL/OWED | phone/tablet walk film→cast→person→franchise→search→series resume→kids; phone half never reviewed |

## Still owed (ordered; groups = parallel worktrees, no shared files)

- **Group A, phone browse:** (2) Latest headings + courses as list (`LatestScreen.kt`); (3) one `ArtTile` (art, scrim, display-font name, meta, aspect) used by GenresIndex 16:9, Movies GenreRow 16:8, Collections/Lists 4:3 wrapping grid + list art + New list pill, Search collections "N films"; (4) Movies "All N films →" pill, "All courses" list, franchise overview in hero + "In release order"; (5) spelled counts, person "N in your library", larger page headings; (9) Robolectric tests for Person/Franchise/Genres/Latest.
- **Group B, title pages:** (6) film facts 3 genres, Details "Audio languages", series About "Audio", crew links, "two seasons"; (7) tablet wide title spread on EXPANDED (`title-page.css:52-116`, reuse `WideDeptHero`); (8) provider totals + air dates via core `TitleInfo` (UniFFI) into `SeriesSummary`.
- **Group C, TV title/series (L):** (11) spread like `TvDepartmentHero`, Play/My List/⋯ pills, Overview/Details fact sheets, About fact sheet, Similar always shown, Up enters selected tab; share fact-sheet row builders via `feature/catalog` (merge B's `SeriesSummary.kt` first).
- **Group D, TV browse (M):** (12) genre tiles, collection cards, franchise hero, round portrait + Films/Series sections on person, "Films" → "Movies", spelled counts.
- **Close:** (1) plan.md status; (10) phone/tablet walk + review; (13) changelog, TV differences recorded, box walk, `check.sh`.

## Deliberate differences already written down — do not "fix"

`TitleSpread.kt:22-30` (phone words below art; quote only wide); `CollectionScreen.kt:186-188`
(series art inset); DESIGN.md › Navigation (no blur, compact header hides, ⋮ menu, pushed
pages' own back bar, Search a page); home-parity report (Settings/System two-pane, per-device
achievement dot); `system-architecture.md` § Television differs (TV always dark, no list mode,
rail utilities, full-screen pushed frames, 2:3 department walls). Only in reports (treat as
owed): lists as rows / tutorials grid on phone; TV hiding empty Cast/Similar.

## Questions → rulings (lead, 2026-10-04, under CLAUDE.md "parity is the default")

1. Tablet title page → **port the web's wide side spread** (task 7).
2. TV title/series pages → **build the spread look** (task 11). TV Episodes → **the web's
   season picker + episode list**, unless the D-pad walk shows it fails the remote; any kept
   difference gets written under "Television differs".
3. Provider totals + air dates → **do it in B1** (task 8; core `TitleInfo` change).

Each ruling is reversible by the user; none reverses a user decision.
