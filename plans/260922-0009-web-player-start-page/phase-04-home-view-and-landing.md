# Phase 4 — The page, and where the player opens

Spec: `docs/superpowers/specs/2026-09-22-web-player-start-page-design.md` §1, §3.

## Overview

Priority: after phase 3. Renders the model and makes `#/home` the landing
route.

## Key insight

`app.js` is 600 lines and every other composite view already lives in its
own module (`search-view.js`, `collections-view.js`, `status-view.js`).
Rendering belongs beside them, and `app.js` gains a `viewHome()` of a few
lines.

The cards already exist: `setGrid` draws any set with its show, episode
label and resume line; `collectionGrid` draws shows and courses. Neither
needs changing.

## Requirements

- `renderHome(main, shelves, { play, open })` draws the rows that have
  anything in them, each with a **See all** link.
- The masthead gains `Home` as its first entry; the wordmark links there.
- No hash means `#/home`, replacing the Continue-or-first-shelf rule.
- `#/home` is a known page, so an unknown hash still falls back to Movies.
- A library with nothing in it falls back to the Movies empty state.

## Related code files

- create `web/public/lib/home-view.js`
- `web/public/app.js` — `viewHome`, `PAGES`, the router, the startup hash
- `web/public/index.html` — the nav entry, the wordmark link
- `web/public/style.css` — the row heading and the See all link

## Implementation steps

1. `home-view.js`: one `row()` helper — heading, See all, grid — and
   `renderHome` composing five of them.
2. `app.js`: put the model together from `library`, `state.inProgress()`
   and `state.watchedAt`, then render. Add `home` to `PAGES`, route it, and
   make the startup hash `#/home`.
3. `index.html`: `<a href="#/home" data-section="home">Home</a>` first in
   the nav; wrap the wordmark in a link to the same.
4. `style.css`: the See all link, and the spacing between rows. Reuse the
   existing `.shelf-head`; do not invent a second heading.

## Todo

- [ ] `home-view.js`
- [ ] `app.js` wiring
- [ ] `index.html`
- [ ] `style.css`
- [ ] `bun test`

## Success criteria

The page renders against the offline stub, every row links to its shelf, a
card plays, and no title appears twice.

## Risks

The masthead wraps its items on their base widths; a ninth entry may drop
the search field to a second line. Check at 1280 and at phone width.

## Security

None.
