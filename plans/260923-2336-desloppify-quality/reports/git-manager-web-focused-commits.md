# Focused web delivery commits

Status: DONE

Created six local commits on `desloppify/quality-20260923`, from `55f8ab2` through `48f0a5c`. All 220 raw changed paths are authored web source, browser modules, and tests. Raw path counts include both sides of moves. No push, PR, scanner commit-log operation, hook change, or staging outside the delegated scope occurred.

## Commit boundaries

| Commit | Group | Raw paths | Focused tests passed |
| --- | --- | ---: | ---: |
| `6bbdd17` | login | 5 | 28 |
| `d595c87` | catalog-publication | 9 | 70 |
| `e6f6ff4` | state | 10 | 122 |
| `24ac685` | channel-protocol | 7 | 12 |
| `7e9e053` | server | 63 | 306 |
| `48f0a5c` | browser | 126 | 557 |

Login, catalog publication, state storage/sync, and channel caption contracts have independent source and test boundaries.

The server group stays together because `index.ts` now composes application lifecycle and catalog-follow modules with the listener and media-resource shutdown contracts. The router split moves shared HTTP, stream, catalog, preload and HLS contracts together with their production importers. Media consumers use the actual bound endpoint, and startup/shutdown integration tests exercise that same graph. Splitting the final file contents by folder would strand imports or pair new orchestration with old resource contracts.

The browser group keeps the catalog/playback/status module moves, app coordinator, player lifetime changes, watch-state notifications, declarations and all affected imports together. Its new test support exercises the current browser composition. Original pre-play and visibility-refresh behavior travels with app.js, watch-state.js and watch-state-refresh.test.ts in this group.

## Verification and preservation

- Each group was exported from the exact Git index, without later unstaged changes, and its affected test files passed before committing.
- Final isolated whole-web gate: **1,556 passed, 0 failed; 11,320 assertions across 115 files**. Log: `/tmp/mediagram-web-commit-final-tests.log`.
- Final staged-snapshot TypeScript check passed with no diagnostics: `bunx --no-install --package typescript tsc --noEmit --pretty false`. Log: `/tmp/mediagram-web-commit-final-types.log`.
- Focused logs: `/tmp/mediagram-web-commit-<group>-tests.log`, using the group names in the table.
- Exact path allowlists, credential-pattern scans, and whitespace checks passed before every commit. Final `git diff --check 55f8ab2..HEAD` passed.
- Commit preparation exposed trailing blank lines in three previously untracked modules. The controller authorized EOF-only cleanup in `web/src/telegram/channel-captions.ts`, `web/src/http/browser-write.ts`, and `web/src/http/stream.ts`; these were the only source edits made by this task.
- All versions remain **0.40.2**. Manifests, dependencies, Rust, Android, docs, plans, skills and scanner state were not staged.
- The context optimization hook refused a proposed dependency-directory symlink. No hooks were altered or prohibited reads redirected. The owned temporary export instead lived below web/ and used ordinary ancestor package resolution.
- The temporary source export was removed after verification. Every owned test/typecheck process completed; no server or child process was left running.

## Exact commits and files

### `6bbdd17c9bd1973ce871f0040a39056ed86820a9` — fix(web-login): scope authentication resources and redact failures

```text
M	web/src/login.ts
A	web/src/login/authenticate.ts
A	web/src/login/prompts.ts
A	web/src/login/setup.ts
A	web/test/login-setup.test.ts
```

### `d595c87efb00229a964d50907673643eda88d869` — fix(web-catalog): retain installed data when publication fails

```text
M	web/src/channel-index/install-channel-index.ts
M	web/src/package/catalog-versions.ts
M	web/src/package/pointer.ts
M	web/src/package/refresh.ts
A	web/test/catalog-versions.test.ts
M	web/test/install-channel-index.test.ts
M	web/test/package-pointer.test.ts
M	web/test/package-refresh.test.ts
M	web/test/refresh-from-channel.test.ts
```

### `e6f6ff4e190ed5cf0653da69a6719dcfce8daa1b` — fix(web-state): commit sync imports atomically and retain failures

```text
M	web/src/state/lists-exchange.ts
M	web/src/state/store.ts
M	web/src/state/sync-record.ts
M	web/src/state/sync.ts
M	web/test/state-store.test.ts
A	web/test/state-sync-coalescing.test.ts
A	web/test/state-sync-import-ties.test.ts
M	web/test/state-sync-record.test.ts
M	web/test/state-sync.test.ts
A	web/test/state-write-failures.test.ts
```

### `24ac685053b7eb12157a1217e4209a4929d69c20` — refactor(web-telegram): separate caption contracts from adapters

```text
M	web/src/channel-index/find-newest-channel-index.ts
M	web/src/channel-index/pick-newest-index.ts
A	web/src/telegram/channel-captions.ts
M	web/src/telegram/state-channel.ts
M	web/src/telegram/updates.ts
A	web/test/channel-index-discovery.test.ts
M	web/test/state-channel-caption.test.ts
```

### `7e9e0533b673ec4d278433b416e922c50f861156` — fix(web-server): own startup, media lifetimes and HTTP boundaries

```text
A	web/src/application/catalog-follow.ts
A	web/src/application/lifecycle.ts
R100	web/src/listen-address.ts	web/src/application/listen-address.ts
A	web/src/application/open-catalog.ts
A	web/src/cache/preload-route.ts
M	web/src/cache/reader.ts
M	web/src/cache/series-preload.ts
M	web/src/cache/store.ts
A	web/src/catalog/artwork-routes.ts
R092	web/src/assets.ts	web/src/catalog/assets.ts
R097	web/src/audio-tracks.ts	web/src/catalog/audio-tracks.ts
A	web/src/catalog/routes.ts
R090	web/src/shows.ts	web/src/catalog/shows.ts
M	web/src/channel-index/fetch-posters-for-index.ts
A	web/src/http/browser-write.ts
A	web/src/http/contracts.ts
A	web/src/http/static-files.ts
A	web/src/http/stream.ts
M	web/src/index.ts
M	web/src/response.ts
M	web/src/routes.ts
M	web/src/server.ts
M	web/src/state/routes.ts
M	web/src/status/routes.ts
M	web/src/telegram/source.ts
M	web/src/thumbs/args.ts
M	web/src/thumbs/sheets.ts
M	web/src/transcode/encoders.ts
M	web/src/transcode/ffmpeg.ts
M	web/src/transcode/registry.ts
A	web/src/transcode/routes.ts
M	web/src/transcode/server.ts
A	web/test/application-catalog.test.ts
A	web/test/application-fixture.ts
A	web/test/application-lifecycle.test.ts
A	web/test/application-media-endpoint.test.ts
A	web/test/application-media-shutdown.test.ts
A	web/test/application-startup.test.ts
M	web/test/assets.test.ts
M	web/test/audio-tracks.test.ts
M	web/test/cache-source.test.ts
M	web/test/cache-store.test.ts
A	web/test/catalog-read-failures.test.ts
M	web/test/fetch-posters-for-index.test.ts
M	web/test/http.test.ts
M	web/test/listen-address.test.ts
M	web/test/posters.test.ts
M	web/test/series-preload.test.ts
M	web/test/server-backpressure.test.ts
A	web/test/server-startup.test.ts
M	web/test/shows.test.ts
M	web/test/state-http.test.ts
A	web/test/static-file-failures.test.ts
M	web/test/status-http.test.ts
A	web/test/thumbnail-route-handoff.test.ts
A	web/test/thumbs-sheets.test.ts
M	web/test/transcode-files.test.ts
M	web/test/transcode-registry.test.ts
A	web/test/transcode-runtime.test.ts
```

### `48f0a5c5efc8c1f1e814f779a2299943e80d0518` — fix(web-player): bind playback and catalog views to active ownership

```text
M	web/public/app.js
R079	web/public/lib/collection-add.js	web/public/lib/catalog/collection-add.js
R074	web/public/lib/collections-view.js	web/public/lib/catalog/collections-view.js
R063	web/public/lib/course-view.js	web/public/lib/catalog/course-view.js
R095	web/public/lib/film-page.js	web/public/lib/catalog/film-page.js
R097	web/public/lib/genres.js	web/public/lib/catalog/genres.js
R095	web/public/lib/home-shelves.d.ts	web/public/lib/catalog/home-shelves.d.ts
R090	web/public/lib/home-shelves.js	web/public/lib/catalog/home-shelves.js
R093	web/public/lib/home-view.js	web/public/lib/catalog/home-view.js
R093	web/public/lib/plate.js	web/public/lib/catalog/plate.js
R098	web/public/lib/search-view.js	web/public/lib/catalog/search-view.js
R096	web/public/lib/season-wall.js	web/public/lib/catalog/season-wall.js
R096	web/public/lib/series-header.js	web/public/lib/catalog/series-header.js
R094	web/public/lib/series-summary.js	web/public/lib/catalog/series-summary.js
R094	web/public/lib/set-badge.js	web/public/lib/catalog/set-badge.js
R100	web/public/lib/shelf-mode.js	web/public/lib/catalog/shelf-mode.js
R073	web/public/lib/shelf-view.js	web/public/lib/catalog/shelf-view.js
M	web/public/lib/format.d.ts
D	web/public/lib/hls-playback.js
M	web/public/lib/library.d.ts
M	web/public/lib/library.js
M	web/public/lib/playable.d.ts
M	web/public/lib/playable.js
R100	web/public/lib/ab-loop.js	web/public/lib/playback/ab-loop.js
R100	web/public/lib/adapt-bitrate.js	web/public/lib/playback/adapt-bitrate.js
R097	web/public/lib/adapt-playback.js	web/public/lib/playback/adapt-playback.js
R096	web/public/lib/audio-chooser.js	web/public/lib/playback/audio-chooser.js
R100	web/public/lib/autoplay.js	web/public/lib/playback/autoplay.js
R098	web/public/lib/buffer-health.js	web/public/lib/playback/buffer-health.js
R100	web/public/lib/framing.js	web/public/lib/playback/framing.js
A	web/public/lib/playback/hls-playback.js
R100	web/public/lib/markdown.js	web/public/lib/playback/markdown.js
R098	web/public/lib/notes-view.js	web/public/lib/playback/notes-view.js
A	web/public/lib/playback/player-hud.js
R098	web/public/lib/player-keys.js	web/public/lib/playback/player-keys.js
A	web/public/lib/playback/player-library-marks.js
A	web/public/lib/playback/player-notes.js
A	web/public/lib/playback/player.js
R100	web/public/lib/preference-scope.js	web/public/lib/playback/preference-scope.js
R097	web/public/lib/preload-readout.js	web/public/lib/playback/preload-readout.js
R099	web/public/lib/seek-model.js	web/public/lib/playback/seek-model.js
R099	web/public/lib/subtitle-panel.js	web/public/lib/playback/subtitle-panel.js
R100	web/public/lib/subtitle-style.js	web/public/lib/playback/subtitle-style.js
R094	web/public/lib/thumb-strip.js	web/public/lib/playback/thumb-strip.js
R099	web/public/lib/transport.js	web/public/lib/playback/transport.js
R100	web/public/lib/up-next.js	web/public/lib/playback/up-next.js
R100	web/public/lib/volume-store.js	web/public/lib/playback/volume-store.js
D	web/public/lib/player.js
R098	web/public/lib/status-lines.js	web/public/lib/status/status-lines.js
R098	web/public/lib/status-view.js	web/public/lib/status/status-view.js
M	web/public/lib/watch-state.js
M	web/test/ab-loop.test.ts
M	web/test/adapt-bitrate.test.ts
M	web/test/adapt-playback.test.ts
M	web/test/audio-chooser.test.ts
M	web/test/autoplay.test.ts
A	web/test/browser-application.test.ts
A	web/test/browser-module-assets.test.ts
M	web/test/buffer-health.test.ts
A	web/test/collection-add-async.test.ts
M	web/test/collection-add.test.ts
M	web/test/framing.test.ts
M	web/test/genres.test.ts
A	web/test/hls-playback-lifetime.test.ts
M	web/test/home-shelves.test.ts
M	web/test/library-documents.test.ts
M	web/test/library.test.ts
M	web/test/markdown.test.ts
A	web/test/player-features.test.ts
A	web/test/player-initialization.test.ts
M	web/test/player-keys.test.ts
A	web/test/player-lifetime.test.ts
M	web/test/preference-scope.test.ts
M	web/test/preload-readout.test.ts
M	web/test/season-wall.test.ts
M	web/test/seek-model.test.ts
M	web/test/series-summary.test.ts
M	web/test/shared-watch-state-fixtures.test.ts
M	web/test/shelf-mode.test.ts
A	web/test/shelf-view.test.ts
M	web/test/status-lines.test.ts
M	web/test/subtitle-style.test.ts
A	web/test/support/browser-application.ts
A	web/test/support/player-environment.ts
M	web/test/transport.test.ts
M	web/test/up-next.test.ts
M	web/test/volume-store.test.ts
A	web/test/watch-state-async.test.ts
A	web/test/watch-state-refresh.test.ts
```

## Remaining scope

Dirty authored web paths: **0**. Final index: **empty**. The only remaining visible web status is the original untracked `web/.claude/` directory, which was preserved. Ignored local scanner state was not changed by this task.

The controller retains ownership of scanner commit attribution, documentation/plan delivery, remaining Android work, and publication.

Unresolved questions: none.
