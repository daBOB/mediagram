# Browser player lifecycle repair

Status: verification in progress; focused checks passed, independent review and coordinated whole-web gates pending.

## Changes

- `public/app.js` owns a generation for pending opens. Every play request supersedes the previous request; dialog close and page exit invalidate pending refresh continuations. An older refresh can no longer replace a newer title or reopen a dismissed player. The library annotation now uses the existing `Library` declaration.
- `public/lib/playback/player.js` uses one `trustedRuntime` translation based on the active `converting` flag. Transport, progress/completion, up-next, and buffered autoplay share that answer. Changing audio on a directly playable file can start conversion, so original eligibility cannot establish whether the media element's growing duration is trustworthy.
- `public/lib/playback/player-next-title.js` is a concrete mounted feature owner for the offer panel, manual-next button, countdown, per-title cancellation, bounded byte preload, and transcode warm. The application still selects the queue; the player still owns source attachment; `hls-playback.js` still owns each acquired server watcher.
- Opening the warmed title retains its warm watcher until the source has joined or failed. Clearing the feature releases it, including a response arriving after cancellation. Cancellation remains remembered for the title across reopening; manual next stays available.
- A local shared teardown saves progress before detaching, then clears probes, source, next-title resources, autoplay wait, save interval, notes, marks, and HUD. It is idempotent when pagehide precedes dialog close; close-only DOM/title resets remain in the close handler.

No scanner state, browser snapshot, manifests, unrelated sources, or live media/network services were changed. Tests exercise production browser modules through controlled DOM/media, timers, and HTTP boundaries.

## Evidence

Before source changes, `bun test test/player-lifetime.test.ts test/browser-application.test.ts` produced **40 passed, 5 failed** in `/tmp/browser-player-second-before.log`:

- Reversed refresh responses attached `First` after the viewer had already opened `Second`.
- Closing the player or hiding the page while Play next refreshed state later attached the next title.
- After audio switching an unknown-duration direct title into conversion, a partial duration of 120 seconds and position of 115 seconds incorrectly set watched=true. Both close and pagehide cases failed.

After the repair, the focused gate passed **153 tests, 801 assertions, 12 files**, logged in `/tmp/browser-player-second-focused.log`. It includes player lifetime/features/initialization/keys, actual application entry, transport, seek, HLS lifetime, adaptation, resume rules, up-next policy, and the served browser module graph. The application fixture bundles the real entrypoint and its modules.

Ten added cases cover the five failing paths above plus per-title cancellation on revisit, late warming after source join, rewind/re-warm ownership, byte-preload headroom and once-per-title behavior, and pagehide followed by close. Existing warm-before-join, late cancellation, source replacement, audio retry, progress-save ordering, countdown, and startup cases remain green.

Two new warm-lifetime assertions initially counted unrelated progress DELETE requests as encoder releases. The shared test helper now counts `/hls/` DELETE requests specifically, preserving verification of every encoder release. No production change was made to satisfy that fixture correction.

Scoped `git diff --check` passed. All temporary application bundles were removed by the existing fixture teardown; no server or background process was started.

## Remaining validation

The coordinating agent requested holding full Bun and TypeScript checks until concurrent watch-state/type changes settle. Independent source review and those coordinated gates remain before final completion. Existing browser fixture limits still apply: it exercises production orchestration with controlled browser IO boundaries, not a real hardware decoder.
