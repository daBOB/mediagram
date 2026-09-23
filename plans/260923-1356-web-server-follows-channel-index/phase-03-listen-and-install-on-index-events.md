# Phase 03 — Listen always; install on every index event and at startup

Priority: high · Status: todo

## Context
- `web/src/index.ts:202-207`: listener only starts when state sync is on
- `web/src/telegram/channel-events.ts`: `listenForLibraryEvents`, 5 s debounce
- Memories: updates need a subscribing request; catch-up is slow, pushes are ms

## Requirements
- Listener starts whenever Telegram is configured; state events still go to
  sync only when sync is on.
- `"index"` event → `newestIndex` → install if newer → `live.swap` → notify
  (phase 04). One install at a time; an event during one queues one more.
- At startup: install the channel's newest before listening (Android reads
  once per start). Channel unreachable → keep the installed one; nothing
  installed → local `library.db` (plan open question 2).
- Status route reports origin `channel`, `publishedAt` = `pushed_at`, last
  refresh verdict and reason, like the package origin does.

## Todo
- [ ] un-gate the listener
- [ ] install-on-event with single-flight
- [ ] startup install + fallbacks
- [ ] status fields

## Success
Pinning a new index from the other machine changes `/api/sets` within seconds
with no restart and no page reload (phase 04 carries it to the page).
