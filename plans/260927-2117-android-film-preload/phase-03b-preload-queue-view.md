# Phase 03b — Show the preload queue

User, 2026-09-28, after queuing "Der Pate" (30 GB) on the TV box: "if the queue is active we
need a way to show". Decisions (user, same day): **a line on the film page + a Preloads
page**; **first come, first served** (no reordering — cancel and re-add to change order).

## Requirements

- **Engine:** expose the queue as one observable list — the running film first (with its
  held/total), then queued films in order — from `FilmPreloadQueue`/`FilmPreloader`, without
  changing the engine's rules. "On this device" = films whose bytes are fully held (the
  catalogue's existing held set), not a new record.
- **Film page, Queued state:** "Queued · after <running title>, 36%" when this film is next;
  "Queued · 2 ahead" when more wait before it. Same on TV. Tap still cancels.
- **Preloads page** (phone/tablet and TV): sections *Preloading* (title, bar, "x of y · n%",
  paused reason, Cancel), *Queued* (in order, Cancel), *On this device* (films fully held,
  Remove). Each row opens its film. Empty sections hidden; when all are empty the page says
  nothing is preloading.
- **Entry point, visible only while something is preloading or queued, with the count:**
  on this branch the overflow menu (phone/tablet) and the TV menu get "Preloads · 2". When
  this branch meets the home redesign (rail + icon row), the entry moves to the rail and the
  compact icon row — note it for the merge, do not build the rail here.
- Android-only (the web has no film preload) — say so beside the page.
- **NeedsSpace names the budget** (user, 2026-09-28, after a TV test walk silently moved
  their budget to 8 GB): "Needs 30 GB · budget is 8 GB · Try again", same on TV, so the
  reason is visible without opening Settings.

## Todo

- [x] observable queue from the engine + test
- [x] Queued label names what is ahead (phone, TV) + tests
- [x] Preloads page (phone/tablet, TV) + tests
- [x] menu entries with count, hidden when idle
- [x] gate green (`testDebugUnitTest lint :app:assembleDebug`); minor bump to
      0.72.0; changelog — device check on the TV box/tablet still pending
      (TV box and tablet both in use by other work this session, per
      instruction)
