# B1d — TV browse parity (group D)

2026-10-04, worktree branch `worktree-agent-a8a22d0cebf8266cc` (fast-forwarded to `main` at
`f2268a00`, 0.102.1 — the branch had been cut at 0.99.9). No version bump (lead bumps on merge).
Phone precedent: `adb37ed3` (b1a).

## Done, by gap-report item (12)

| What | Where |
|---|---|
| One TV art tile: a focusable tv-material `Card` at the 12dp picture-card corner, art + scrim + name over the art (Fraunces 24sp genre / 28sp uppercase destination, 16sp count at 80%) | new `ui-tv/.../TvArtTile.kt` (`TvArtTile`) |
| Scrim stops and the three proportions (16:9, 16:8, 4:3) shared with the phone, which now draws through them unchanged | new `ui-common/.../catalog/ArtTileShape.kt`; `ui-mobile/.../ArtTile.kt` (constants moved, same `ui.catalog` package, so no phone call site changed) |
| Genres page: 16:9 tiles four across (≈205dp at a pushed frame's 864dp ≈ the web's 13rem floor), the web's empty sentence as a focusable stop | `TvGenresIndex.kt`; `TvWall` gains `columns` (default 6, so every other wall is unchanged) |
| Movies genre row: 16:8 tiles, 240dp wide (12rem would leave a two-line 24sp name no room) | `TvDepartmentRows.kt#GenreTileRow` |
| Collections: franchises and lists as 4:3 destination cards, three to a line, each line its own lazy item, every card `key(id)`-ed with its own always-present requester; a list pictured by its first pictured title (`listArtOf`); "Franchises"/"Your lists" as the web's count-less row heads; "＋ New list" a round outline pill | `TvCollectionsPage.kt` (rewritten), `TvCatalogBody.kt` (passes `byId`), `TvPagePill` in `TvArtTile.kt` |
| Franchise: the department hero with the franchise title rule (52.8sp = 5.5vw at 960dp, up to three lines), "two films · 1999–2003", TMDB's introduction *inside* the hero (which grows past 360dp to hold it), then "In release order" | `TvFranchisePage.kt`; `TvDepartmentHero.kt` (`franchiseTitle`, `overview` slot — both default off); `TvHeroWordsAboveQuote.kt` (grows only when given no height ceiling) |
| Person: round portrait (120dp, the search row's `TvPortraitCircle`) before the name, "N in your library" (figures, as `cast.js`), Films and Series as two headed parts (headed even alone, as the web does) | `TvPersonPage.kt`, `TvSearchRows.kt` (circle made internal) |
| Shelf head (`.shelf-head`): title 42sp SemiBold, caps extent flush right, 1dp rule — on the web's four `heading()` pages: Genres ("SIX GENRES"), one genre ("TWELVE TITLES"), Latest ("NEWEST ARRIVALS FIRST"), a person | `TvHeadings.kt#TvShelfHead`; `TvGenre.kt`, `TvLatestPage.kt` (one line each) |
| Search: "Films" → "Movies" (its chip), "Tutorials" → "Lessons" (web and phone heading); a franchise row "N films", a list "N titles", a person "one title" — spelled | `TvSearchGroups.kt`, `TvSearchRows.kt` |
| Spelled counts ≤ 20 via the existing `catalog.spelledCountOf` everywhere above | — |

Genre and Latest heads were not named in group D; they took the shelf head because the web
draws all four reference pages through one `heading()` and leaving two on "Name · n" would have
split TV's own convention. One line each; no test asserted the old wording.

## Kept TV differences (written under `docs/system-architecture.md` § Television differs)

- **A franchise's introduction is a stop, and a fresh visit lands on it.** TMDB introductions
  in the live index average 350 characters (265 franchises, max 997 — measured from
  `~/.local/share/mediagram/library.db`), about seven lines at TV's reading size; landing on
  the first film would scroll the franchise's name off before it was read. Down is the first
  film (explicit `focusProperties`), Up from a film reads it again, Back from a film lands on
  that film. Once per visit (`rememberSaveable`), so a lazy header scrolled back into view
  never pulls the remote up.
- **"No lists yet." under Your lists** — same as the phone (web leaves an empty grid).

DESIGN.md: Shelf head, Art tile and Page pill entries gain their TV line.

## Tests

`./gradlew -q --continue :ui-tv:testDebugUnitTest :ui-mobile:testDebugUnitTest :ui-common:testDebugUnitTest :ui-tv:compileDebugAndroidTestKotlin lint`
— green: ui-tv 454, ui-mobile 272, ui-common 73 tests, 0 failures; androidTest compiles; lint
0 errors (warnings all pre-existing: none in a file this change created; the two in touched
files, `TvDepartmentHero`/`TvListRow` `ModifierParameter`, predate it).

New Robolectric (960×540 unless noted): `TvGenresIndexStateTest` (4: tiles + head, arrival +
Right/Down by four, restore, empty stop), `TvFranchisePageStateTest` (5: hero words/span/
introduction/heading, undated span, arrival + restore, introduction-first + Down/Right/Up/Down,
restore with introduction), `TvCollectionsPageStateTest` (6, one kept: heading clearance, cards
+ spelled counts, arrival/restore franchise and list, Right/Left/Down by line → lists → pill →
Up, open callbacks, empty → pill + name question), `TvPersonPageStateTest` (+2: shelf head +
Films/Series + Down crossing at 960×1080, lone part headed), `TvSearchResultsStateTest` (+2:
Movies/Lessons headings, spelled destination/person captions), `TvDepartmentPagesStateTest`
(+1 assertion: genre row's "three titles"). Updated expectations: `TvCatalogScreenStateTest`
and `TvKeptWallStateTest` (destination names in capitals, list cards instead of rows).

## Concerns

- **Department files touched, additively.** `TvDepartmentHero`, `TvHeroWordsAboveQuote` and
  `TvDepartmentRows` are shared with the departments. The hero's two new parameters default
  off and keep the fixed 360dp exactly (`heightIn(min=max=360)`); the existing
  `TvDepartmentHeroStateTest` is unchanged and green. `GenreTileRow` was named in the task.
- **Arrival geometry is arithmetic, not observed.** Robolectric's font fallback measures text
  far smaller than the box (a 330-character introduction measured one line), so "the name
  stays on screen" for the franchise page cannot be asserted there; the introduction-first rule
  is pinned by focus, not by bounds. Needs the box walk: franchise with and without an
  introduction, Collections with >3 franchises, Genres page, a person with films and series.
- **Lazy prefetch:** the person page's Down-into-Series test runs at 960×1080 — Robolectric's
  clock never runs a lazy grid's idle-time prefetch, so a plate just past a 540dp screen is
  not composed there (on the box it is, 320dp cache window).
- **Seen, not touched (outside group D):** TV search still lists films, shows and collections
  as text rows where the web draws posters and destination cards — never written down as
  deliberate, so owed; Movies' "All N films →" is a text row where the web has the `.dept-all`
  pill (departments); a list's own page (`TvList`) still heads "Name · n" where the web uses
  the shelf head; `TvLists` (the kept COLLECTIONS tab) looks unreachable on TV —
  `collectionsIndex` routes that tab to `TvCollectionsPage` first.
