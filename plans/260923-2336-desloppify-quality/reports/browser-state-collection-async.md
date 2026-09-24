# Browser state and collection response ownership

Implemented 2026-09-24 in original `web/` source. Browser assessment snapshot
and scanner state/plan remain unchanged.

## Changes

- Every profile selection receives a new generation. Initialization and refresh
  adopt responses only for the still-current selection, including when the
  viewer changes A → B → A or clears the choice. Existing refresh timeout and
  newest-local-progress merge behavior are preserved.
- Profile and collection creation share response decoding inside the existing
  silent-write boundary. HTTP, network, JSON, and interrupted-body failures
  resolve to `null` without appending a local record. Successful responses still
  append once. A failed response body does not prove the server rejected the
  write; no automatic creation retry was added.
- Collection search invalidates pending responses immediately on every input
  and when its panel closes. Clearing a query cannot be undone by a late success
  or error. HTTP failures show the existing failure message, while a successful
  empty result still says “Nothing matches.”
- Independent review identified the same selection race in collection
  creation. A late A response after A → B or A → B → A now returns `null`
  without appending to the current selection. No creation retry is attempted.

Only `public/lib/watch-state.js`, `public/lib/collection-add.js`, and two new
focused tests were changed in this batch. Prior user refresh changes remain
present; no application/player/library edits were made.

## Verification

- New regressions before production changes: **7 passed, 14 failed** across
  21 tests. Failures cover selection races, unreadable creation bodies, HTTP
  search failure, and late picker responses after input/close.
- After changes, the new suites plus existing watch-state refresh and picker
  suites: **30 passed, zero failed**, 59 assertions across four files.
- The two additional collection-creation race tests both failed before their
  fix. The expanded focused suite passed **32 tests**, with 65 assertions.
- Whole-web TypeScript gate passed with zero diagnostics:
  `bunx --package typescript tsc --noEmit --pretty false`.
- Full Bun at this intermediate shared-workspace state: **1,467 passed,
  nine failed**, 10,609 assertions across 107 files. All nine failures are in
  the concurrently changing `test/player-lifetime.test.ts`: startup after
  close, title replacement, repeated seek ordering, stale startup rejection,
  default audio, audio retry, runtime initialization, and two ended-phase seek
  cases. They are outside this batch and were reported to the root agent.
- Independent source review passed except for the collection-creation race
  above, which was fixed and sent back for verification. `git diff --check`
  passed.
- The subsequent application coordination review passed with this guard in
  place. Its final combined gate passed **1,520 tests with zero failures**,
  plus a clean whole-web TypeScript check; the earlier concurrent player
  failures are resolved. See [application coordination](browser-application-coordination.md).

Tests exercise production functions with deferred HTTP responses, real JSON
decoding (including an errored body stream), and a minimal DOM IO boundary
using real event dispatch. No live requests, project servers, or persistent
user data are involved. All owned commands exited.

Logs: `/tmp/mediagram-browser-async-before.log`,
`/tmp/mediagram-browser-async-after.log`,
`/tmp/mediagram-browser-async-types.log`, and
`/tmp/mediagram-browser-async-full.log`.
