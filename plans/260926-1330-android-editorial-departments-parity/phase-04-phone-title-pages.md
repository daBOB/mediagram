# Phase 4 — Phone title pages

**Status:** pending. Web reference: `title-spread.js`, `film-page.js`, `series-page.js`,
`tabs.js`, `cast.js`, `styles/title-page.css`.

- Film: spread (backdrop right, fading into the page; title, facts, overview clamp,
  Play/Resume pill, My List pill, ⋯ with editor's choice), tagline as pull-quote; tabs
  Overview (poster + fact sheet + "Part of" franchise only when it has a page) · Cast
  (only when credits exist) · Similar · Details (technical facts).
- Series: same spread with the SeriesResume pill; tabs Episodes (season picker + list,
  replacing the season wall) · About · Cast · Similar. Season stays in the saved stack.
- Tabs keep their selection across recomposition/state changes (web review finding).
- Robolectric tests in `ui-mobile/src/test` for tab presence, resume label, cast gating.
