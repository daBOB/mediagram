# Phase 3 — The shelves, as a pure model

Spec: `docs/superpowers/specs/2026-09-22-web-player-start-page-design.md` §1, §4.

## Overview

Priority: the heart of the feature. Every rule that can be got wrong lives
here, and nothing here touches the DOM.

## Key insight

`nextAfter(collection, setId)` and `flattenCollection(collection)` already
exist in `library.js`, and `resumeAt`/`isFinished` already judge a position.
This module is composition, not new judgement — except for one rule the
codebase has never had to state: what a show's *next* episode is when the
viewer has both a position and completions.

## Requirements

`homeShelves({ library, progress, watchedAt, limit })` returns

```
{ continues, nextUp, latestMovies, latestSeries, latestCourses }
```

- `nextUp`: one entry per touched collection, `{ set, collection, resume }`,
  ordered by last touched. A resume point wins over the episode after a
  finished one; forward walks skip already-watched episodes; a collection
  with nothing left is absent.
- `continues`: half-watched sets, most recent first, minus every set id in
  `nextUp`.
- `latest*`: by `addedAt` descending; a collection by its newest member.
- Every row capped at `limit` (6), and empty rows are empty arrays.
- Pure: no DOM, no imports from `watch-state.js`. State arrives as plain
  arguments so the tests need no store.

## Related code files

- create `web/public/lib/home-shelves.js`
- create `web/public/lib/home-shelves.d.ts`
- create `web/test/home-shelves.test.ts`
- read `web/public/lib/library.js`, `web/public/lib/resume-point.js`

## Implementation steps

1. Index collections by set id once, so a touched episode finds its show
   without scanning every collection per lookup.
2. `touchedAt(set)` = later of the progress row's `updatedAt` and the
   completion's timestamp.
3. Per collection: gather touched sets; resume candidates first; else walk
   `nextAfter` from the most recently finished, skipping watched.
4. Latest: sort a copy, never the caller's array.
5. Tests per §4 of the spec.

## Todo

- [ ] `home-shelves.js`
- [ ] `.d.ts`
- [ ] tests covering all seven rules in §4
- [ ] `bun test`

## Success criteria

Tests cover each rule by name and fail if the rule is inverted.

## Risks

`flattenCollection` includes documents; a PDF must never be offered as the
next episode. Filter with `isDocument` on the forward walk.

## Security

None.
