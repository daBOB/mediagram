# Browser module ownership

## Summary

Completed the organization-only browser move on 2026-09-24. Runtime behavior,
public entry points, and the absolute `/lib/hls.mjs` vendor endpoint are
preserved. No barrels, forwarding wrappers, scanner edits, or snapshot edits
were introduced.

## Findings and changes

| Owner | Files moved | Scope |
| --- | ---: | --- |
| `web/public/lib/playback/` | 24 JavaScript | Player, transport/keys, adaptation, buffering, audio/subtitles, notes, HUD, framing, thumbnails, playback preferences |
| `web/public/lib/catalog/` | 15 JavaScript + one declaration | Shelf and detail pages, hand-built lists, home shelf selection, genres, series/season presentation, plates and badges |
| `web/public/lib/status/` | 2 JavaScript | Status polling/formatting and panel rendering |

The browser import graph determined membership. Shared `format`, `library`,
`watch-state`, `link`, `playable`, `codec-support`, `age-rating`,
`language-label`, `resume-point`, `colophon`, `sprite-plan`, `dom`, and
application-level profile selection remain at the root. In particular,
`sprite-plan` is also imported by server artwork code, and `playable` is used
by the server and the Rust shared-policy contract.

Updated 108 relative/static/type path references across the two stages,
including dynamic test imports, companion declarations, and JSDoc imports.
Updated the current ownership/path descriptions in
`docs/system-architecture.md`; historical specs and reports remain intact.

An independent content comparison canonicalized old and new path strings
across all **252 preexisting authored source/test assets**. It found **zero
non-path content differences** and confirmed all **42 moved files**.

## Verification

1. After the playback move, transport/player-keys/seek: **46 passed**, zero
   failures, 141 assertions.
2. Actual application, player initialization/lifetime, and static module graph:
   **42 passed**, zero failures, 437 assertions.
3. Full Bun suite: **1,521 passed**, zero failures, 11,092 assertions across
   111 files in 4.20 seconds.
4. Whole-web TypeScript check passed with zero diagnostics:
   `bunx --package typescript tsc --noEmit --pretty false`.
5. Browser bundle passed with **55 modules**:
   `bun build public/app.js --target browser --external /lib/hls.mjs --outfile /tmp/mediagram-browser-organization-app.js`.
6. `git diff --check` passed. Independent source review was requested from the
   root agent before resolving the organization finding.

The added `browser-module-assets.test.ts` traverses the real entry's imports
using Bun's parser and requests every dependency from the actual static-file
handler. It verifies successful delivery, JavaScript MIME type, and byte
length at resolved browser URLs, including catalog, playback, status, and the
installed HLS endpoint. This covers path-serving errors hidden by bundling.

Logs: `/tmp/mediagram-browser-organization-playback.log`,
`/tmp/mediagram-browser-organization-focused.log`,
`/tmp/mediagram-browser-organization-full.log`,
`/tmp/mediagram-browser-organization-types.log`, and
`/tmp/mediagram-browser-organization-build.log`.

## Recommendations and unresolved questions

No further source changes are required by this batch. Root owns finding
resolution and subsequent assessment. All owned commands exited; no project
servers, live Telegram requests, or persistent user-data changes were made.
