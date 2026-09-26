# Phase 4 — Movie and series feature pages

**Priority:** P1. **Status:** done (0.62.0, 2026-09-26). It goes into the preview checkpoint.

## Context
`lib/catalog/film-page.js`, `series-header.js`, `series-summary.js`, `season-wall.js`,
`title-band.js`, `styles/catalog.css`.

## Movie detail (panel 03)
- **Split hero:** the left column has "← Back to Movies", a large serif title, a meta
  line (year · runtime · certification · genres), the overview, then Play/Resume, My List,
  and a ⋯ menu that holds the collection-add and pin actions. The backdrop takes the right
  ~60% and fades into the page. The tagline is set as a pull quote with no attribution.
- **Tabs** (`role=tablist`, arrow keys): **Overview** (backdrop and still images, plus the
  director), **Cast** (portrait row, then a person page), **Similar** (poster shelf), and
  **Details** (technical facts from the set: quality, HDR, codecs, audio and subtitle
  languages, file size, versions, franchise link).
- The Cast tab is hidden when there is no cast (a v8 index).

## Series detail (panel 05)
- The same hero: series name as an eyebrow in caps, title, years · seasons · genres ·
  status, overview, **Resume SxEy** (from the watch state), My List and ⋯.
- **Tabs:** **Episodes** (the default: a season `<select>` and a readable list of number,
  thumb, title, one-line summary and a watched mark; not a thumbnail wall), **About**
  (network, status, first and last air date, type), **Cast** and **Similar**.
- Tutorials keep the course view, which has no tabs.

## Todo
- [ ] tabs component (one, shared)  - [ ] film hero + tabs  - [ ] series hero + episodes  - [ ] browser tests

## Checkpoint (before phase 5)
Take screenshots with `cd web && bun run preview` and `/browse`: Home, a film (Dune:
Part Two if held), and a series (Star Trek: TNG if held). Take them at 1440 and 375,
dark and light, into `visuals/`. **Stop and show the user.**
