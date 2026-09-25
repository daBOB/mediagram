# Web follow-up focused commits

Status: DONE

Committed the stable fourth server/second browser delivery in six focused groups
on `desloppify/quality-20260923`. Final HEAD:
`2a3ca72c131faaa832fa5398b4584b95e0d9ee2b`. The index is empty and all authored
`web/src`, `web/public` and `web/test` paths are clean. No push.

## Groups and boundaries

| Commit | Responsibility | Rename-aware files |
| --- | --- | ---: |
| `29fd09d` | Safe failure formatting, poster diagnostics, transcode directory ownership and contract corrections | 14 |
| `060c201` | Audio probe admission, cancellation/reaping and application shutdown registration | 4 |
| `f906fb4` | Disk-only thumbnail streaming, cached reader consolidation and IO regressions | 13 |
| `7cf7916` | Shelf preference fallback, codec startup, profile/list failure feedback and state/type contracts | 13 |
| `5dea18c` | Pending-open/runtime/next-title ownership, notes/streaming moves and every affected import | 18 |
| `2a3ca72` | Real served-HTML/controller and subtitle orchestration integration coverage | 4 |

Two shared files used index-only boundaries after notifying the controller:

- `web/src/index.ts`: the audio registration line travels with its reader and
  resource contract; cached-route registration and thumbnail comments travel
  with the disk-only source/route implementation.
- `web/test/series-preload.test.ts`: new rejection regressions retain the existing
  buffered test reader in the first commit; the later cache commit switches
  those reads to the common streaming collector together with removal of the
  buffered API and every other test caller.

No working-tree source or test byte changed. Player module moves travel with
all static and dynamic imports, including the cache-busting HLS test import;
indexed source/test search confirms the deleted module paths have no callers.
The stricter HTML/track fixtures and their new integration tests form their own
final test commit after the production player changes. No compatibility wrapper,
manifest edit or temporary source adjustment was introduced.

## Validation and safety

Read applicable repository/git instructions and the current server fixes,
thumbnail cache, audio shutdown, browser state, player and integration reports.
The controller's final integration checkpoint is verified in:

- `/tmp/web-fourth-full-tests.log`: **1,608 passed, 0 failed**, 11,542 assertions
  across 121 files.
- `/tmp/web-fourth-full-types.log`: TypeScript passed with no diagnostic output.
- Root independent review is recorded in `web-fourth-root-fixes.md` and
  `browser-state-second-review.md`; transcode cleanup has its separate PASS
  review. The linked implementation reports contain the failing-before and
  mutation evidence. No gates were unnecessarily repeated during committing.

Every staged group passed whitespace checks and explicit path ownership checks.
Reviewed all added-line credential/security pattern matches; none contain real
credentials, private state or dotenv files. All committed paths are within the
three authorized web trees. No scanner state/snapshot, `.claude`, documentation,
plan, dependency or root setup file was staged.

Before staging, captured SHA-256 hashes for all **71** pending scoped paths,
including deleted old modules. After committing, every working-tree path and
HEAD blob matches that checkpoint exactly, and deleted paths remain absent.
This verifies that the final commits preserve the exact tested source. Per-commit
boundary coherence was checked from actual interfaces/imports; no separate
per-commit runtime gate is claimed.

Normal commit execution succeeded. `.githooks` configuration is unchanged;
this repository has no pre-commit hook and its pre-push hook was not bypassed.
No version changed: this remains the synchronized **0.40.2** delivery. No
background process, source mutation or test rerun was started.

## Remaining scope

There are no uncommitted paths within `web/src`, `web/public` or `web/test`.
Ignored local web state and all out-of-scope work remain untouched. The controller
owns finding commit tracking, reports/docs, remaining integration work and any
later publication.

## Exact commit hashes and paths

### 29fd09d6d356f2c34c555e188067511d05ed28be — fix(web-server): retain fallback errors and isolate transcode cleanup

```text
M	web/src/cache/series-preload.ts
M	web/src/channel-index/refresh-from-channel.ts
A	web/src/failure-message.ts
M	web/src/http/contracts.ts
M	web/src/package/posters.ts
M	web/src/package/refresh.ts
M	web/src/state/store.ts
M	web/src/transcode/registry.ts
M	web/src/transcode/routes.ts
M	web/src/transcode/server.ts
A	web/test/poster-diagnostics.test.ts
M	web/test/refresh-from-channel.test.ts
M	web/test/series-preload.test.ts
M	web/test/transcode-registry.test.ts
```

### 060c2013df90d10bb919d2878d17a684c17aba9e — fix(web-audio): stop and reap probes during application shutdown

```text
M	web/src/application/lifecycle.ts
M	web/src/catalog/audio-tracks.ts
M	web/src/index.ts
A	web/test/audio-shutdown.test.ts
```

### f906fb4125e34c82f154d97638af5c708bce9624 — fix(web-cache): enforce disk-only thumbnail reads

```text
M	web/src/cache/reader.ts
M	web/src/index.ts
M	web/src/routes.ts
M	web/src/telegram/source.ts
M	web/src/thumbs/sheets.ts
M	web/test/application-media-endpoint.test.ts
M	web/test/cache-source.test.ts
M	web/test/cache-store.test.ts
M	web/test/series-preload.test.ts
A	web/test/support/cache-reader.ts
A	web/test/thumbnail-cache-only.test.ts
M	web/test/thumbnail-route-handoff.test.ts
M	web/test/thumbs-sheets.test.ts
```

### 7cf7916e81caec24e7f22a9cc08944d1c9114c06 — fix(web-state): retain shelf choices and report management failures

```text
M	web/public/lib/catalog/collections-view.js
M	web/public/lib/catalog/home-shelves.d.ts
M	web/public/lib/catalog/home-shelves.js
M	web/public/lib/catalog/plate.js
M	web/public/lib/catalog/shelf-mode.js
M	web/public/lib/colophon.js
M	web/public/lib/library.js
M	web/public/lib/link.js
M	web/public/lib/profile-picker.js
M	web/public/lib/watch-state.js
A	web/test/link-initialization.test.ts
A	web/test/management-controls.test.ts
M	web/test/shelf-mode.test.ts
```

### 5dea18ccfb62310de295348074fa7bc70200c137 — fix(web-player): own pending opens, runtime and next-title resources

```text
M	web/public/app.js
R100	web/public/lib/playback/markdown.js	web/public/lib/playback/notes/markdown.js
R098	web/public/lib/playback/notes-view.js	web/public/lib/playback/notes/notes-view.js
R100	web/public/lib/playback/player-notes.js	web/public/lib/playback/notes/player-notes.js
A	web/public/lib/playback/player-next-title.js
M	web/public/lib/playback/player.js
R100	web/public/lib/playback/adapt-bitrate.js	web/public/lib/playback/streaming/adapt-bitrate.js
R100	web/public/lib/playback/adapt-playback.js	web/public/lib/playback/streaming/adapt-playback.js
R100	web/public/lib/playback/buffer-health.js	web/public/lib/playback/streaming/buffer-health.js
R099	web/public/lib/playback/hls-playback.js	web/public/lib/playback/streaming/hls-playback.js
M	web/test/adapt-bitrate.test.ts
M	web/test/adapt-playback.test.ts
M	web/test/browser-application.test.ts
M	web/test/buffer-health.test.ts
M	web/test/hls-playback-lifetime.test.ts
M	web/test/markdown.test.ts
M	web/test/player-features.test.ts
M	web/test/player-lifetime.test.ts
```

### 2a3ca72c131faaa832fa5398b4584b95e0d9ee2b — test(web-player): exercise served HTML and subtitle orchestration

```text
A	web/test/browser-html-player.test.ts
M	web/test/support/browser-application.ts
A	web/test/support/html-application.ts
M	web/test/support/player-environment.ts
```

Concerns/Blockers: none in the authorized web commit scope.
