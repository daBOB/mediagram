# Browser player initialization and feature ownership

Status: DONE

The four assigned queue findings are implemented in the original browser
directory. No scanner state, commits, server API or package layout changes.

## Boundaries

- `player.js` evaluates without looking up page elements or mounting controls.
  `initializePlayer()` explicitly mounts once; a second call is a no-op.
  `openPlayer(set, options)` retains its argument contract and requires that
  initialization. The application owner updated `app.js` to initialize after
  acquiring its DOM handles; I did not edit the application entrypoint.
- `player-notes.js` owns notes fetch/render/visibility and cancellation when
  the title changes or closes. `open` and `clear` control the feature; the
  coordinator asks `contains` when interpreting keyboard events. Responses
  remain text rendered into nodes, and missing notes remain nonfatal.
- `player-library-marks.js` owns watchlist, age-rating/kids presentation and
  collection selection against its current title. `open`/`clear` determine
  which title receives actions. Existing state-owned notifications remain the
  only shelf invalidation mechanism.
- `player-hud.js` owns focus rules, media/pointer listeners and the rest timer.
  `open`/`clear` delimit its lifetime; `show` responds to coordinator actions.
  Closing or leaving the page prevents a pending timer from changing the HUD.
- The DOM panel is `upNextPanel`, the queued set is `nextTitle`, and the buffer
  monitor callback is `evaluateBitrateSwitch` at registration and removal.

The three new production modules are 50, 66 and 40 lines. Player source/session
coordination remains in `player.js`; no general controller or dependency
framework was introduced. Existing subtitle and transport mount patterns were
retained. No new declaration file was necessary for these JavaScript exports.

## Validation

- Both initialization regressions failed before the refactor: importing
  without `document` threw, and import with a page had already mounted controls
  (`/tmp/player-initialization-before.log`).
- Final focused gate: **127 tests passed across 11 files**, including two
  import/idempotence checks, nine notes/marks/HUD lifecycle checks and all
  preceding playback lifetime regressions (`/tmp/player-boundaries-focused.log`).
- Notes tests cover lesson/film visibility, clearing during response-body
  settlement, reopening the same title and missing notes. Mark tests execute
  real state mutations/notifications and collection writes. HUD tests use
  real media/focus events with a controlled clock.
- The application owner ran the combined final suite after integrating the
  explicit mount: **1,520 passed, zero failures, 10,787 assertions, 110 files**
  (`/tmp/mediagram-browser-app-full.log`). Its real application integration
  includes film navigation, awaited state refresh, player resume, close, saved
  progress and shelf redraw.
- Whole-web TypeScript passed both locally
  (`/tmp/player-boundaries-typecheck.log`) and in the combined application gate
  (`/tmp/mediagram-browser-app-types.log`). Scoped diff and formatting checks
  passed. No processes remain running.

Controller review and scanner resolution remain with the parent. Tests run
the production modules against controlled browser/media/HTTP boundaries; they
do not claim actual browser decoding or live Telegram coverage.
