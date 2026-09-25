# Browser grid and declaration contracts

Completed 2026-09-24 in original `web/` source. The browser assessment snapshot
and scanner state/plan were preserved.

## Changes

- `movieGrid`, `setGrid`, and `collectionGrid` now accept a final options object
  with an optional `mode`, defaulting to list mode. Existing `setGrid.caption`
  behavior remains intact. Updated every caller in `home-view.js` and six
  mechanical callsites in `app.js`; no application lifecycle logic changed.
- The buffer sample annotation now exposes optional `duration` and documents
  that missing/non-finite values disable its end-of-media check.
- Watch-state's held shape now includes its existing watched, kids, and
  preference stores. Removed the obsolete watched-string adapter: both the
  current snapshot query and the table's original creation schema require dated
  `{setId, finishedAt}` records. Valid server responses retain their behavior.
- The playability companion now declares `conversionNote`; the decision's
  JSDoc and companion share the complete `PlaybackDecision` result, including
  blocking flags and picture handling. `countsUnder` was already declared by
  intervening work and was preserved.
- Related compiler diagnostics were repaired with truthful contracts:
  `pickerRows` preserves its input row type while requiring a set ID;
  `nextInQueue` returns the original generic item; count helpers accept the
  title-less root shape they actually inspect; optional progress, keyboard, and
  preload inputs match existing defaults; series metadata parameters expose
  their actual optional fields. Two catalog fixtures now supply the required
  `addedAt` field instead of weakening `CatalogSet`.

The original user changes in `app.js` and `watch-state.js` remain present. The
diff against the assessment snapshot shows only the six grid call adaptations
in `app.js`, and the held-type/obsolete-adapter changes in `watch-state.js`.

## Verification

- New production-grid regression before the fix: four passed, two failed.
  Movie and collection grids incorrectly rendered list containers when given
  `{mode: GRID}`. After the fix: all six pass.
- Focused Bun suite: **287 passed, zero failed**, 7,768 assertions across 15
  files, including existing buffering, playability, grouping, migration, store,
  and refresh behavior.
- Configured whole-web TypeScript gate passed with zero diagnostics:
  `bunx --package typescript tsc --noEmit --pretty false` (7.0.2). This includes
  concurrently completed server/fixture fixes owned by the root agent.
- Browser bundle validation passed for all 52 modules:
  `bun build public/app.js --target browser --external /lib/hls.mjs --outfile /tmp/mediagram-browser-contracts-app.js`.
- Full Bun run: **1,408 passed, one failed**, 10,301 assertions across 102 files.
  The failure was the untouched cache test
  `the quota > evicts what was read longest ago` at `test/cache-store.test.ts:111`:
  chunk 1 was retained. The isolated cache suite passed afterward. Initial
  access-time ties and asynchronous touch updates are plausible causes; the root
  agent owns the investigation. The broad failure has not been hidden or marked
  fixed by this batch.
- Independent source/type/grid review passed. `git diff --check` passed.

Logs: `/tmp/mediagram-browser-contracts-before.log`,
`/tmp/mediagram-browser-contracts-tests.log`,
`/tmp/mediagram-browser-contracts-types.log`,
`/tmp/mediagram-browser-contracts-build.log`,
`/tmp/mediagram-browser-contracts-full.log`, and
`/tmp/mediagram-browser-contracts-cache.log`.

Root owns finding resolution and manifest integration. No commits, application
servers, live Telegram requests, or scanner-state mutations were made.
