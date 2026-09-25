# Browser playback lifetime

Status: DONE

Implemented the five assigned playback findings in the original browser files.
No application entrypoint, library declarations, watch-state implementation,
scanner state, or server protocol changes were made.
At the controller's request, the obsolete player `keptChanged` event and its
three callers were removed after the application owner introduced
`state.subscribeChanges` as the owner of shelf invalidation.

## Behavior

- `player.js` owns each title's probes and each source's startup with separate
  AbortControllers. Closing, changing title, seeking again, or leaving the page
  invalidates the old source immediately. Late successes and failures cannot
  attach media or update the current title's message.
- `hls-playback.js` owns a server session as soon as its ID is available. Native
  and HLS teardown, keepalive cancellation, and DELETE release are idempotent.
  Unsupported HLS, construction/attachment failure, fatal playback errors, and
  cancellation all release the acquired session. Library loading remains lazy.
- The startup response deliberately settles after cancellation because only
  that response identifies the server session to release. No AbortSignal is
  passed to that request. This existing transport has no deadline; a request
  which never settles cannot be explicitly released by ID and remains subject
  to server cleanup. The controller approved this protocol constraint.
- Desired audio is distinct from attached audio. A remembered language or
  discovered default actually changes playback; a failed choice remains
  retryable. Stream ordinals, current/pending resume position, bitrate cap and
  playback intent survive source changes. Automatic bitrate fallback resumes
  playback when its new source is ready.
- Title state resets before controls and asynchronous probes run. Metadata
  listeners belong to their source. Summary, audio, held-state and thumbnail
  results belong to their title, including reopening the same set. The only
  adjacent helper change is the announced optional signal guard in
  `thumb-strip.open`.
- Leaving the ended phase clears its countdown. Seeking back either hides the
  card or returns it to the waiting phase. Repeated natural-end updates retain
  one countdown; cancelled warm starts release late sessions. A warmed session
  is released only after playback joins it.

## Evidence

New tests execute the original player, transport, buffer monitor, audio chooser,
thumbnail helper and HLS adapter. The fixture supplies browser nodes/media
events, a controllable clock and HTTP replies; only the external hls.js module
is replaced with controlled library IO. These are orchestration tests, not an
actual browser decoder or live Telegram session.

- Original behavior: 9 player regressions failed and 1 existing-behavior check
  passed (`/tmp/player-lifetime-before.log`); 11 HLS lifetime regressions failed
  (`/tmp/hls-lifetime-before.log`).
- Expanded real-flow checks then proved two additional failures before repair:
  fallback stayed paused, and audio discovery discarded a pending resume
  position (`/tmp/player-transition-before.log`).
- Final focused gate: **116 tests passed across 9 files**, including **34 new
  lifetime tests** (`/tmp/browser-playback-focused.log`).
- Earlier full web gates passed **1,487**, then **1,490 tests across 107 files**
  with zero failures, recorded in the tool output and reported to the controller.
- The latest broader run contains the concurrent application worker's new
  tests: **1,500 passed, 1 failed across 108 files**. Its sole failure is the
  active collection-picker mutation regression in
  `test/browser-application.test.ts:168`; the controller was notified. The
  current log is `/tmp/browser-playback-full.log`. The final playback changes
  passed the focused gate above after the event integration update.
- `bunx tsc --noEmit` passed (`/tmp/browser-playback-typecheck.log`).
- Prettier check at the repository's existing 100-column style and scoped
  `git diff --check` passed. HLS production adapter is 198 lines.

No process remains running. Controller review and scanner resolution remain
with the parent agent; this report makes no scanner score claim.
