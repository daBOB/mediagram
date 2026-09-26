# Phase 3 — Shell, nav, and Home

**Priority:** P1. **Status:** done (0.62.0, 2026-09-26). It goes into the preview checkpoint.

## Context
`web/public/index.html` (rail + departments), `styles/shell.css`, `styles/theme.css`,
`lib/catalog/home-view.js`, `home-cover.js`, `home-features.js`, `app.js` router.

## Requirements (reference panel 01)
- **Top bar:** Home · Movies · Series · Tutorials · Collections. Search icon, bell
  (omitted: no notifications exist, YAGNI), profile avatar. The active tab is a filled
  pill, as in the mockup.
- **Rail:** Home, Movies, Series, Tutorials, Collections, then a hairline, then My List,
  Latest, Genres, Settings. Continue stays reachable, and System stays gated as today.
  New routes: `#/latest` and `#/genres` (a genre index). `#/settings` comes in phase 7.
  The rail foot keeps the vertical tagline, from real counts.
- **Home:** a full-bleed cover carousel (already built) with the eyebrow
  "Featured this week", a serif-caps title, the tagline, Watch Now and My List pill buttons, and
  pagination dots. Below it sit **three editorial blocks** in one row, each a backdrop card with
  a serif-caps headline and one real line. These are the existing Editor's choice / Trending /
  Staff pick spotlights; the headline is the title and the line is its tagline. Continue and the shelves follow.

## Visual rules (skill + locked aesthetic)
- Serif caps (Fraunces, tight tracking) for display. Geist for UI. No banned fonts.
- The pill buttons: primary is solid light, and secondary is a hairline ghost with an icon.
- Motion uses `cubic-bezier(0.32,0.72,0,1)`, reveals only through `reveal.js`, only
  transform and opacity, and respects `prefers-reduced-motion`.
- `backdrop-filter` only on the sticky bar.
- Below 768px, the rail becomes a bottom tab bar (Home, Movies, Series, Collections,
  Search). The editorial blocks stack.

## Todo
- [ ] nav markup + routes  - [ ] latest/genres views  - [ ] home cover + blocks restyle  - [ ] mobile

## Success criteria
There is no overflow at 375/768/1024/1440. The keyboard reaches every nav item, and
existing hashes still resolve.
