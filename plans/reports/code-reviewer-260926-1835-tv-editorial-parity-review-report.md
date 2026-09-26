# TV editorial parity — review + device walk (2026-09-26)

Scope: `14f12be8..1e851e77 -- android/ui-tv` (+ shared pieces). Reviewer: code-reviewer agent (read-only),
device walk on TV box 192.168.0.35 (benchmark build, 32-bit armeabi-v7a).

## Found on the device
- **Launch crash (fixed 906fdc41):** `TvCatalogExtras` was a `@HiltViewModel` in `ui-tv`, which applies no Hilt
  plugin → no factory → "Cannot create an instance" on every build. Replaced by `BrowseViewModel` +
  `CatalogViewModel.titleCredits` (already in feature:catalog, used by the phone).
- Home arrival focus lands on "Recently added", cover story and features scrolled off (confirms M6).
- Feature cards are 2:3 portrait crops of backdrops, ~416dp tall on a 540dp screen (confirms M6).
- "Recently added" reads Z→A on real data — to check (shared rule vs same-date ties).

## Critical
- **C1** `TvSearchResults.kt:~80` — `var index = 0; val at = index++` inside lazy items counts compositions,
  not positions: Back to a result below the fold focuses the wrong row (or none). Regression from
  `itemsIndexed`. Fix: per-section start offsets + `itemsIndexed`.

## Major
- **M1** Department pages steal focus from Search/Menu: `TvPage` re-provides `LocalTakesArrivalFocus = true`
  and `TvMoviesDepartmentPage` requests focus unconditionally (`TvDepartmentPages.kt:80,82,182`).
- **M2** `PlateRow` loops `0 until HOME_ROW_LIMIT` (6): department rows (≤12) and Latest (limit 48) cut at 6;
  Latest rows show a dead "See all" (`TvHomeRow.kt:147`, `TvLatestPage.kt`).
- **M3** Series/Tutorials front page: hero + rows in one `TvWall` header item taller than the screen; arrival
  focus on wall plate 0 scrolls past them; header rows have no restore target.
- **M4** Tabs reset after Back from Cast/Similar: title/series pages are not in a `SaveableStateProvider`
  (`TvLibrary.kt:357`), `rememberSaveable` restarts; Cast/Similar plates get no restoreKey.
- **M5** `TvCastRow` is a plain `Row` without scroll: cast 7–12 squeezed to zero width; all 12 portraits
  fetched at once.
- **M6** Magazine too tall: feature cards 2:3 `TvPlate` at weight(1f); arrival focus on first row scrolls the
  cover off. Landscape cards; arrive on cover's Watch now.
- **M7** TV has no theme choice (always dark) though the user decided "port theme + seven accents"; not
  recorded as a deliberate difference. → needs the user's decision.

## Minor
- N1 Watch now opens details; web plays (`home-cover.js:137`). Department hero on web has no rotation/kicker.
- N2 Similar passes `{ false }` for watched (`TvLibrary.kt:129,184`); phone passes watch.watched.
- N3 No "Part of <franchise>" / crew line on the title page (web + phone have both).
- N4 Collections: franchise tiles have no arrival/restore focus; lists squeezed to ~80dp.
- N5 Person page flashes "Nobody…" while loading; no watched/offline marks.
- N6 Franchise page `leave()`s while the catalogue is still Loading (cold restore).
- N7 Movies front page re-requests focus when the library changes; genre restoreKey unmatched;
  `TvMoviesPageEntryKey` never mapped to "all".
- N8 Portrait marked requested before the fetch: a cancelled fetch never retries (`RememberLookups.kt:155`).
- N9 Movies front page composes ~60 non-lazy plates at once (weak box) — use LazyRows.
- N10 Latest matches rows by title string; no `heldIds`.

## Rules
- Over 200 lines after this change: TvLibrary.kt 425, TvDepartmentPages.kt 367, TvSearchResults.kt 345,
  TvCatalogScreen.kt 321, TvCollection.kt 278, TvTitlePage.kt 216, TvSearch.kt 204.
- Plan references in comments: TvCatalogScreen.kt:114, TvTheme.kt:19, TvLatestPage.kt:25, TvHome.kt:48,
  TvTitlePage.kt:192, TvMenuPage.kt:41, TvCollectionsPage.kt:28, TvDepartmentPages.kt:133, TvCollection.kt:105,
  RememberLookups.kt:142, PortraitRequestLog.kt:9.
- Duplicates: `byIdOf` (TvDepartmentPages.kt:297) duplicates public `catalog.allSetsById`.
- Deliberate differences claimed in the TV report are not in `docs/system-architecture.md`; Continue/My List
  moved to the Menu matches the web's rail but should be recorded.

## Unresolved
- M7: keep the TV always dark, or port Dark/Light/Auto as decided?
- Whether `requestFocus()` on an unattached requester crashes (decides C1's severity).

## Fixes and device walk (TV box, benchmark build, `andre` profile, 2026-09-26 evening)

Branch `review/tv-editorial-parity` on top of 1e851e77: 906fdc41 (launch crash), cf142d28 (review
fixes, two agents), e9eee4ca, 58232dbf, de875726 (found on the box). `scripts/check.sh` green.

Walked and fixed on the box, beyond the review's list:
- Title/series tabs: default pill indicator + underline colours made the selected tab's label vanish
  once the row had focus → underline indicator, start-aligned.
- Back from Cast/Similar still opened Overview: credits load after the page, so the restored tab was
  chosen from empty credits and kept → switch once they arrive (test with late credits).
- Movies front page: hero full-bleed (outside overscan, taller than the screen) and arrival on Featured
  → inset like Home, arrive on the hero like Home and Series.
- Person page: portrait + first row taller than 540dp, header scrolled off → 120dp portrait.
- Search: Back from a show/person/collection landed on the first result — frame recorded bare ids,
  rows are keyed `show:`/`person:`/`dest:` → recorded under the rows' keys (test added).
- Collections: lists double-indented; arrival on the first list; 70 franchise posters non-lazy →
  inset once, arrive on the first franchise, LazyRow.
- "Recently added" Z→A: not a bug — real `created_at` (Telegram send time) shows the Z titles were
  uploaded last (851 distinct stamps over 902 films).

Verified on the box: launch; Home cover + landscape features; Movies/Series fronts; film page tabs,
franchise line, crew line, cast portraits, 8th cast member reachable; person page; Back to Cast +
that person; genre page; grouped search with chips; franchise page; Latest wall (55); Menu utilities;
Collections.

Still open (minor):
- Up into a title's tab row lands on the geometrically nearest tab, not the selected one.
- Menu order: My List/Continue/Latest/Genres come after "Start over".
- Search chip "Movies" vs section heading "Films".
- Not walked: Tutorials front, Settings › Appearance accent, a Watch now play, the emulator.
