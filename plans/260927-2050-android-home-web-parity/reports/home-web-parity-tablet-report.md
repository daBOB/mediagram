# Home web parity — tablet walk-through (phase 04)

2026-10-04 · Android 0.99.8 → 0.99.9 · tablet `caad49da` (Redmi Pad Pro, 1164×777 dp
landscape / 777×1164 dp portrait), profile andre · web: the live player on :8770 at the
same CSS widths, profile andre, headless Chromium (light theme; the tablet runs dark —
theme is the viewer's setting, not a difference).

## Verdict

The home, the chrome and the departments match the web. Two defects found on the way
were fixed in this phase. Every other difference is either written down as deliberate
(DESIGN.md › Navigation) or belongs to a plan that already owns it (follow-ups below).

## Test sweep

`:ui-mobile :ui-common :feature:catalog :core:data :ui-tv` `testDebugUnitTest` — before
the fixes 1,210 tests, 0 failures; after them 1,212 (two new), 0 failures.

## What was walked

| Area | Landscape | Portrait | Sheet |
|---|---|---|---|
| Home top / mid / lower | ✓ | ✓ | `web-vs-tablet-261004-L1-home.jpg`, `-P1-home.jpg` |
| Home section order (text, scrolled) | ✓ identical to web | — | below |
| Departments: Movies, Series, Anime, Documentaries, Tutorials, Collections | ✓ all six | Movies, Documentaries, Collections | `-L2-depts`, `-L3-depts`, `-P2-depts` |
| Rail: My List, Continue watching, Latest, Genres, Stats, Settings, System | ✓ all seven | Genres | `-L4-rail`, `-L5-rail`, `-L6-misc` |
| Search, profile chooser, ⋮ menu | ✓ | search | `-L6-misc` |
| Back from a pushed page (title) | ✓ returns to home | — | — |

Home order, read off both surfaces as text while scrolling: cover → Editor's choice →
Trending on TMDB → Best-rated in the library → Continue Watching (+ pull quote) →
Recently Added → Latest series → Latest courses. Identical.

**Compact width:** no phone is attached. Covered by the Robolectric suites at the default
(compact) width — `OverflowUtilitiesTest`, `LibraryFlowTest` — and by the tablet in
portrait (777 dp), which draws the compact header: wordmark + icon row, scrolling pills,
search field + avatar, and the header returns on scroll up (`P1-home` sheet).

## Fixed in this phase

1. **Back on a reopened profile chooser closed the app** (phone and TV). The gate draws
   the chooser in place of the library and handled no Back, so Back fell through to the
   activity. Now Back is "Stay as I am", as Escape is on the web; a first run (nobody to
   stay as) still leaves. `ProfileGate.kt`, `TvProfileGate.kt`. Tests:
   `LibraryRailTest.backOnTheReopenedPickerStaysAsTheViewerRatherThanLeavingTheApp`,
   `TvHousekeepingTest.backOnTheReopenedPickerStaysAsTheViewer` — both seen failing
   with the handler removed. Verified on the tablet three times.
2. **My List's page was titled "Watchlist · 1"** where the web says "My List".
   `KeptKind` is a port of `app.js`'s `KEPT` map, which the web renamed to "My List" /
   "Nothing on your list." in its editorial-departments change; Android never followed.
   Fixed on phone and TV (`KeptShelves.kt`). Verified: `web-vs-tablet-261004-fix-my-list-heading.jpg`.

## Deliberate differences (written where the code is, and in DESIGN.md › Navigation)

- **No blur behind the departments bar.** Mid-scroll over the cover, the cover's title
  shows through the bar sharper than on the web (`L1-home`, mid). Compose cannot blur
  what lies behind a node without a new dependency.
- **Compact header hides on scroll down**, returns on scroll up.
- **⋮ menu** carries Update library, TMDB key…, Start over — the web server does these.
- **Pushed pages (Latest, Genres, Search, a title) keep their own back bar** and the rail
  beside them; the web has URLs, Android a back stack. The tablet's search is therefore a
  page, the web's an inline field.
- **Settings and System are Android's own two-pane index** (settings-redesign plan), not
  the web's single page; System shows what this device does.
- **"New achievement" dot**: seen-state is per device by contract
  (`viewing-stats.../shared-contract.md:262`), so web and tablet may differ.

## Differences owned by other plans (not fixed here)

- **Genres index** — web: landscape tiles with the name over the art; tablet: portrait
  posters, name below. **Latest** — web heads its rows "Movies", tablet "Latest films".
  **Collections › Franchises** — web: large captioned cards; tablet: small uncaptioned
  squares. All three are `260926-1330-android-editorial-departments-parity` phase 05.
- **Profile chooser layout** — web centres square tiles and says "Rename or remove…";
  the tablet sets round avatars top-left and says "Remove a profile…". Both chooser
  screens are rebuilt by `260928-0047-profile-roles-pins-kids-age-limits` (phases 03, 07).

## Follow-ups

- [x] TV home to the same layout — done since (`4fcf8f91`, 0.83.0).
- [ ] Backdrop blur behind the bar — needs a dependency (e.g. Haze) or API 31+
      `RenderEffect` work; ask before adding one.
- [ ] The web's right-hand cover genre column above 1180 dp — not checkable on this
      tablet (1164 dp); needs a wider device or the emulator.
- [ ] **Rail too narrow for "Continue watching" + a two-digit count at 1164 dp, on both
      surfaces**: the tablet ellipsizes the label ("Continu… 10"), the web clips the
      count off the edge. Both use the same 184 px/dp narrow rail; a design call
      (wider narrow rail, or a shorter label) for both at once.
- [ ] **Web: the last department pill stays highlighted on rail pages** (e.g. Collections
      lit on My List, Stats, Settings — `L4-rail`, `L5-rail`). Web-side defect.
- [ ] **Tutorials › Continue your courses**: the tablet showed a third card
      (Geldhochschule, "Next up") the web did not. Both build the row from the same
      continues + next-up rule (`department-pages.js:150-155`, `ShowsDepartmentScreen.kt:58`),
      so the difference is in the data each derived next-up from, not the layout.

- [ ] **The player's marks pill still says "Watchlist" / "On the list" on both
      surfaces** (web `player-library-marks.js:14`, Android `PlayerMarks.kt:40`,
      `TvMarksRail.kt:55`) while everywhere else says "My List". In step between the
      surfaces, out of step with the rest of each; a wording change for both at once.

## Unresolved questions

- Should the rail's narrow width or the "Continue watching" label change? It is the
  web's own design too, so it is the user's call, for both surfaces.
- Why the web derived no next-up for Geldhochschule while the tablet did — needs the
  two watch states compared (memory: compare state DBs before blaming sync).
