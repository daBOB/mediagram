# Browser application coordination

Implemented in original `web/` source on 2026-09-24. Scanner state, plan, and
assessment snapshot remain owned by the root agent and unchanged by this work.

## Behavior and ownership

- Catalog text is cached only after JSON decoding, library grouping, and the
  set index succeed. Invalid JSON and invalid catalog shape retain the prior
  catalog and retry construction when an identical response arrives again.
- Every route render gets a generation. Search success and failure can update
  the main content only while their originating route is current, including
  navigation away and back to the same search.
- Watch state publishes shelf changes through `subscribeChanges`. Progress,
  completion, watchlist, kids, and collection writes no longer depend on each
  caller remembering to invalidate the view. Refresh emits for list-only
  changes while preserving its existing progress-change return value and
  newest-local-progress merge.
- The app subscribes once and owns redraw timing. Counts update immediately;
  playback and collection-picker editing defer shelf replacement until close.
  Collection controls no longer duplicate mutation notifications. The picker
  reports its editing lifetime, keeping repeated additions possible.
- The app explicitly calls the player's idempotent `initializePlayer` after
  acquiring its DOM handles, integrating the playback worker's import-safe
  initialization API.
- Series/course detail assembly now lives in `course-view.js`; list pages live
  in `collections-view.js`. Shared heading/breadcrumb rendering lives in
  `shelf-view.js`. The app retains routing, catalog state, refresh coalescing,
  visibility/EventSource lifecycle, playback sequencing, and redraw timing.
  No routing framework or new production module was introduced.

Original catalog-refresh and pre-play/visibility state-refresh behavior was
preserved and integrated into the notification path. The app remains the
owner of one active catalog read plus at most one follow-up.

## Evidence

The new suite bundles the actual `public/app.js` and its production imports
into a temporary test artifact, then loads a fresh instance per case. It does
not replace production methods. DOM, media, clock, EventSource, and HTTP are
controlled IO boundaries; the real player is also exercised through a film
page click, remote-position refresh, resume, and close/save.

- Initial application regressions: **4 passed, 6 failed** before changes.
- Expanded application integration suite: **18 passed**, 60 assertions.
  Coverage includes profile-dependent startup; failed catalog parse/group
  retry; notification coalescing; deferred redraw; hide/show reconnection;
  stale search success/error and returning to a query; progress/collection
  mutation; picker editing; remote list-only refresh; rename/delete;
  season metadata/navigation; nested course/document pages; real playback.
- Combined application/state/picker/grid check: **56 passed**, 137 assertions
  across six files, including explicit player initialization integration.
- Whole-web TypeScript check passed with zero diagnostics using
  `bunx --package typescript tsc --noEmit --pretty false`.
- Final combined full Bun gate passed: **1,520 tests, zero failures**, 10,787
  assertions across 110 files in 5.52 seconds. This includes the playback
  worker's completed lifetime, explicit initialization, and notes/marks/HUD
  tests. The TypeScript check was repeated alongside this run and passed.
- `git diff --check` passed.

Independent source review passed. No remaining concerns were found in this
batch. All owned commands have
exited; temporary bundle directories are removed by the test suite. No live
HTTP requests, project servers, or persistent user-data writes were used.

Logs: `/tmp/mediagram-browser-app-before.log`,
`/tmp/mediagram-browser-app-after.log`,
`/tmp/mediagram-browser-app-focused.log`, and
`/tmp/mediagram-browser-app-types.log`; full gate:
`/tmp/mediagram-browser-app-full.log`.
