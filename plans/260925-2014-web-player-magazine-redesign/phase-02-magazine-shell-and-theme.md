# Phase 2 — Magazine shell and theme

Priority: high. Status: todo.

## Direction
- **Top department bar** (the magazine sections): Home, Movies, Series, Tutorials,
  Collections, plus search, profile and System on the right. It floats over the cover
  on home and turns solid (blur is allowed here, since it is sticky) once scrolled.
- **Slim index rail** on the left (the library's own shelves): Continue, Watchlist,
  Genres, Latest. It carries a small spaced-caps masthead line built from real counts,
  e.g. `829 FILMS · 6 SERIES`, not a slogan.
- Mobile: the rail collapses into the department bar's overflow row. There is no
  hamburger with fake depth.
- Every existing `href`, `data-section` and element ID that `app.js` consumes stays the
  same; only the placement changes.

## Tokens (`public/styles/theme.css`)
- Dark (default): ink-black `#0c0c0d`, raised `#151517`, text `#f2efe8` (warm off-white),
  secondary `#a9a59c` (≥4.5:1), hairlines `rgba(255,255,255,.08)`, and the existing
  red accent warmed for dark.
- Light "paper": `#f6f3ec`, ink `#1b1a17`, the same accent, deeper.
- Type: Fraunces for display (cover headlines 5–8rem, `opsz` high, tight tracking),
  Newsreader italic for decks and pull-quotes, Geist for UI and spaced-caps eyebrows
  (`letter-spacing:.32em`, 11px).
- Motion: `--ease: cubic-bezier(.32,.72,0,1)`. Reveals use transform and opacity only,
  are driven by IntersectionObserver, and honour reduced motion.
- The dialog-based player stays dark in both themes (unchanged contract).

## Files
- `public/index.html` (restructure the masthead into bar + rail; same IDs)
- `public/styles/theme.css`, `public/styles/shell.css`
- `public/lib/reveal.js` (new, ~30 lines: IntersectionObserver `.reveal` → `.in`)

## Success
- No horizontal overflow at 375, 768, 1024 and 1440 px; keyboard order is bar → rail →
  main; skip link works; both schemes pass 4.5:1.
