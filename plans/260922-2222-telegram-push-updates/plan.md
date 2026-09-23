---
title: "Telegram push updates: watch state and library arrive without waiting"
description: "Listen for channel updates so another device's watch state and a newly pinned index reach a running player in seconds; the timers stay as the safety net."
status: pending
priority: P3
effort: 16h
branch: feat/push-updates
tags: [web, android, rust-core, telegram, sync]
created: 2026-09-22
---

# Telegram push updates

Today a running player finds out about change only by asking. The web player syncs watch state every
`MEDIAGRAM_SYNC_EVERY_MS` (default 5 min, `web/src/config.ts:176`). Android will do the same every
5 min while foregrounded (`plans/260922-2135-android-watch-state-sync` phase 04). Android picks up a new
library index only when "Update library" is tapped. MTProto already pushes every change these depend on
to every connected session of the account. This plan listens for them.

Research: `plans/reports/research-260922-2212-telegram-api-premium-opportunities-report.md` § 5.

## Phases

| # | Phase | Track | Blocked by | Effort | Status |
|---|---|---|---|---|---|
| 01 | [Spike: are the updates delivered?](phase-01-spike-measure-update-delivery.md) | rust+ts probe | – | 2h | done — GO ([report](reports/spike-260922-2240-update-delivery-report.md), [clean redo](reports/spike-260922-2305-update-delivery-redo-report.md)) |
| 02 | [Classify updates (shared fixture)](phase-02-classify-channel-updates-shared-fixture.md) | rust+ts | 01 | 2h | done |
| 03 | [Rust core: update stream export](phase-03-rust-core-update-stream.md) | rust | 02 | 4h | done |
| 04 | [Android: react while foregrounded](phase-04-android-react-while-foregrounded.md) | android | 03, watch-state-sync 04 | 3h | done (state half: `STATE` → `WatchSync.soon()`, own writes filtered by `stateDeviceId`) |
| 05 | [Web: sync on push](phase-05-web-sync-on-push.md) | web | 02 | 3h | done |
| 06 | [Docs, versions, device validation](phase-06-docs-versions-and-validation.md) | all | 04, 05 | 2h | done; watch-state latency handed on |

04 and 05 run in parallel.

## Key decisions

- **Push shortens the wait and replaces nothing.** Timers and the manual button stay. A missed update costs
  what it costs today. Both sync paths already tolerate running at any time (`web/src/state/sync.ts` never throws).
- **The spike gates the plan.** If 01 shows updates are not delivered to a second session for this private
  channel, or only after `getChannelDifference` polling, stop at 01 and record why.
- **Events, not payloads.** An update only says *something changed*. The existing round (sync / refresh)
  still reads the pin list, so there's one code path for the data and nothing new to trust.
- **No catch-up (spike).** grammers `catch_up` did not deliver missed channel messages. Every (re)connect of the
  update loop runs one ordinary round first, then relies on live updates. `catch_up: false`.
- **Own echo is filtered anyway.** Neither spike run saw a writer receive its own send/edit/delete, but
  matching `device` in the `#mlib-state` caption costs nothing and guards against a library that does echo.
- **Debounce 5 s** per event kind. An upload pins once but edits and sends several messages.
- **Android: foreground only.** No service or background socket. The Android plan's WatchSync already
  binds to `onStart/onStop`. Background push would mean FCM or a foreground service, which this doesn't justify.
- **Web library reload is out of scope.** The web catalog comes from the HTTP package (`web/src/index.ts:65`),
  not the channel, and has no live reload. Once the Settings plan's phases 02/04 land (swappable router,
  channel index), an `IndexChanged` event can drive it: one line in 05's handler.

## Unresolved questions

1. Should the push path also lengthen the default timer (5 min → 15 min) to save calls? The plan keeps 5 min.
2. ~~Should Android's `IndexChanged` also run `fetch_missing`?~~ **Answered by the user 2026-09-23: yes** — new media
   triggers the metadata and poster fetch, quietly (phase 04).
