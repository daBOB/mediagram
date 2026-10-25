# TV: one frame for every page

User report (2026-10-22): "we need to unify the layout on TV. sometimes we have a sidepanel, the gaps of the rows in home, or series page is different."
User decision: rail + departments bar on every library page, as the web's shell (`index.html:51,74,119`, `shell.css:17-81`).

## Findings (code audit, not yet measured on the box)

- Only `TvCatalogScreen` draws `TvLibraryChrome`. Every pushed frame (title, series/course, person, franchise, genre, Genres, Latest, Movies page, list, search, Stats, Preloads) draws neither rail nor bar and pads by `Overscan` (48/27) instead of rail+gutter (91.5) and bar+overscan (84).
- Series page: one `spacedBy(Spacing.small)` for everything. Web: spread→tabs 64px (48dp), tabs→panel 36px (27dp), picker→rows 20px (15dp), episode rows flush, ruled, 56px min (42dp).
- Department pages pad their hero twice (Movies, Documentaries, Collections) or three times (Shows, Anime); Continue rows there use ad hoc 24/8/16 instead of `.dept-row` 33/15/12.
- Heading rule 1dp in `TvShelfHead`, 0.5dp in `TvCountedHeading`.

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | [Chrome on every library frame](phase-01-chrome-on-every-frame.md) | done |
| 2 | [Web gaps on series, title and department pages](phase-02-web-gaps.md) | done |
| 3 | Tests (800 pass), lint, emulator walk, review fixes, changelog, version 0.138.3; box walk owed (box offline) | done but box |

## Out of scope (named, not done)

- Settings/System menu screens and the avatar's menu page keep their own layout, as the tablet's `LibraryScaffold` exempts them.
- Home cover's `.cover-side` caption; Search/Preloads/Stats inner type.

## Review (code-reviewer, 2026-10-22) and what was done

- Fixed: pill over a page lost the remote (pillPressed on the pill sentinel); tab row under the bar (`revealsPageBelow(top)`); walls counted the bar twice (`StaysPutWhenOnScreen.clearedOfTheBar`); search's bar button opened a second search (focuses the field); stale docs; `bleeds` with unbounded width.
- Found while testing: the chrome's onExit bound to inner focus groups (page wrapped in one `focusGroup`); an 8dp row inset / tab offset stole Left from the rail (rows start on the page edge; tab offset reverted).
- Not done (shortcuts in code): rail Right lands on the page's first stop (a page restorer hijacks arrivals on the box); avatar menu over a page drops its tab/filter.
- Not a gap: `.dept-hero`'s 12px margin collapses into `.dept-row`'s 44px.
- Verified on the Google TV emulator: title, show, Series, Movies pages; pill press from a film; tabs under the bar; Left to the rail; Back from the rail closes the page.
