# Phase 3 — Home as a magazine

Priority: high. Status: todo. **Ends with a screenshot checkpoint for user approval.**

## Sections (top to bottom), all from real data
1. **Cover story.** A full-bleed backdrop with a scrim. The eyebrow reads
   `FEATURED · <genre>`. The headline is the *film's title* in huge Fraunces. The deck
   is its **tagline** (Newsreader italic), falling back to the first sentence of the
   overview. Meta: year · runtime · FSK · ★. **Watch now** (primary) and **Details**.
   It rotates through `pickFeatured` films that have a backdrop (reusing
   `featured-picks.js`); a dot pager, auto-advance paused on hover/focus, and off under
   reduced motion.
2. **Editorial features.** Three wide spotlight cards, **each one title** (not a genre
   list), each with its backdrop, a spaced-caps eyebrow, the title in serif, and the
   tagline as the deck. Clicking opens the film page. The three are always distinct
   titles, also distinct from the cover. Rules (user decisions, 2026-09-25):
   - **EDITOR'S CHOICE**: a title the user **pinned** with a "Make editor's choice"
     action on the film/series page. It is one pick for the household and stored in
     the watch-state db, so it syncs to other devices like lists do. With nothing
     pinned, the card falls back to the Staff-pick rule (next candidate).
   - **TRENDING**: the highest **TMDB popularity** among films this profile has not
     watched. Needs `popularity` stored (phase 1b). The eyebrow says
     `TRENDING ON TMDB`, not a claim about this household.
   - **STAFF PICK**: the highest **TMDB rating** among films this profile has not
     watched, rotated daily (a date-seeded choice among the top 10), so it is not the
     same film forever.
   Titles without a backdrop are skipped for all three.
3. **Continue / Next up.** Quiet landscape cards: backdrop (else poster), title,
   episode line, a thin progress rule. The same click behaviour and `resumeLine` as
   today.
4. **Typographic break.** A large Newsreader-italic pull-quote: a real **tagline**,
   attributed `— <Title>, <year>`, which links to the film. Beside it, a numbered
   "**This month**" list: films added in the last 30 days (arrival `addedAt`), numbered
   01–05 in magazine style. It is hidden if nothing arrived.
5. **Recently added.** Poster shelves (films, series), a quieter cadence, "See all".
   Courses stay an index list (no artwork, as today).

Rows with nothing in them are not drawn (existing rule in `home-view.js`).

## Files
- `public/lib/catalog/home-view.js`: orchestrates only; each section is its own module
  (<200 lines each):
  `home-cover.js`, `home-features.js`, `home-break.js` (new), plus the existing
  `home-shelves.js`, which gains `features` (editor/trending/staff with de-duplication)
  and `thisMonth` (pure, tested).
- Editor's choice: `web/src/state/` (store + route `PUT/DELETE /api/editors-choice`,
  included in sync), and the action button in `film-page.js` / `series-header.js`.
- `public/styles/home.css` (new)
- Tagline source: the catalog projection carries `tagline` for movies (from `shows`),
  avoiding a request per cover. Check `routes.ts forBrowser`; add it if missing.

## Tests
- `home-shelves` unit tests: the feature rules and fallbacks, the no-duplicate rule,
  the daily staff-pick rotation, the pin surviving a sync round trip, the this-month window, the cover pool
  requiring a backdrop.

## Checkpoint
- Stub harness (`stub-offline.ts`, see memory) at 1440 and 375, dark and light. Show
  the user. Proceed to phase 4 only on approval.
