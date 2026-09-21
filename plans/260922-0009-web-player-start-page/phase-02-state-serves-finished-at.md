# Phase 2 — `/state` says when something was finished

Spec: `docs/superpowers/specs/2026-09-22-web-player-start-page-design.md` §2.

## Overview

Priority: with phase 1, before phase 3. Next up ranks a show by when it was
last touched, and finishing an episode is the commonest way to touch one.

## Key insight

The state database already records `finished_at` on every `watched` row, and
the sync record already reads it. Only the profile read route drops it, so
this is a shape change on one payload, not a schema migration.

A finished episode also *clears* its progress row — so without this, a show
whose last episode was completed has no timestamp anywhere the client sees,
and would rank behind a show glanced at months ago.

## Requirements

- `GET /api/profiles/:id/state` serves `watched: [{ setId, finishedAt }]`.
- The client keeps the timestamps and exposes `watchedAt(setId)`.
- `isWatched` keeps its signature; nothing else changes.
- A state file written by an older player still loads.

## Related code files

- `web/src/state/store.ts` — `read()`, the `watched` query and its type
- `web/public/lib/watch-state.js` — `held.watched`, `useProfile`, `setWatched`
- `web/test/state-http.test.ts` — pins the served shape

## Implementation steps

1. `store.read()`: select `set_id, finished_at` and return objects. Update
   the return type beside it.
2. Client: `held.watched` becomes a `Map` of setId to millis. `isWatched` is
   `has`; add `watchedAt`. `setWatched(id, true)` records `Date.now()`.
3. Reader tolerates both shapes — a bare string row is kept with 0 — so the
   first load after an upgrade does not drop every completion.
4. Update the HTTP tests to the new shape; add one asserting the timestamp
   is present and plausible.

## Todo

- [ ] `store.read()`
- [ ] `watch-state.js`
- [ ] tests
- [ ] `bun test`

## Success criteria

The route serves objects, the client ranks by them, and no existing state
test is weakened to pass.

## Risks

Any other reader of `said.watched` would break silently. Grep before
editing; the Android app reads none of this API.

## Security

None: this is the viewer's own state, already served to them.
