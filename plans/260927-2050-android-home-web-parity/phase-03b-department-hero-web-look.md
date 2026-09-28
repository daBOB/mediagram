# Phase 03b — Department hero in the web's look

Reported by the user 2026-09-28: "the hero element in android app is broken layouted".
Side by side (tablet 1164×777 vs web stub, same index): the Movies / Series / Tutorials /
Documentaries hero is still the pre-redesign one.

## Context links

- Web: `web/public/lib/catalog/department-hero.js`, `department-pages.js` (callers and
  their `kicker`/`title`/`line`/`lead`/`leadName`), `web/public/styles/departments.css`
  (wide and ≤900px rules), `theme.css` tokens. Reference shot:
  scratchpad `web-series-top.png` (and `web-movies-top.png`).
- Android: `android/ui-mobile/src/main/kotlin/ui/catalog/DepartmentHero.kt`
  (+ `DeptRowHeading`), its callers (Movies department, `ShowsDepartment` for Series/
  Tutorials, the new Documentaries page), `PullQuote.kt` (the quote under the hero),
  `ui/catalog/home/HomeType.kt` / `designsystem` `CoverTitle` + the static Fraunces
  cuts, `HeroArtwork` + `LocalBackdrop`, the chrome seam `LocalTopChrome` (Home draws
  under the bar; department tabs today pad below it).

## What differs today (tablet, Series)

| | Web | Android |
|---|---|---|
| Art | Under the glass bar, fades into the page from the left (scrim like the home cover) | Hard-edged box below the bar, ~475dp tall |
| Words | Geist eyebrow "ONLY IN YOUR LIBRARY" (0.32em), huge uppercase Fraunces "SERIES", Newsreader line "48 shows · 1511 episodes" | Small "SERIES" over the art's bottom-left corner, Geist line |
| Quote | Newsreader italic, top-right inside the hero, with a small uppercase attribution | Large serif pull-quote below the hero |
| Height | ≈330dp; the first row ("Continue your series") is on the first screen | Hero fills most of the first screen |

## Requirements

- Rebuild `DepartmentHero` to the web's department hero, numbers from
  `departments.css` (wide + ≤900px), sharing the cover's type (`CoverTitle` cut, eyebrow,
  scrim order as fixed on the cover: first CSS layer on top).
- Department tabs draw the hero under the departments bar like Home's cover (bar
  translucent over it, opaque once it passes — reuse the cover's blend, driven by that
  department's own list state); pages without a lead image keep padding below an opaque
  bar.
- The quote moves inside the hero (top-right on wide, below the line on compact), as the
  web; `PullQuote` stays only where something else still uses it.
- Honour `LocalBackdrop` exactly as the web's `appearance.css` does for department art
  (SOLID hides it).
- Light theme contrast as on the web.
- Tablet side-by-side sheets for Movies, Series, Tutorials, Documentaries vs the web.

## Todo

- [x] DepartmentHero rebuilt (wide + compact) with tests
- [x] hero under the bar on department tabs, blend reused
- [x] quote inside the hero; PullQuote callers checked
- [x] backdrop modes + light theme
- [x] gate green incl. `:core:designsystem` and `:ui-tv` (TV untouched)
- [x] tablet sheets vs web, patch bump, changelog
