# Phase 4 — Next, auto-advance, preload

- `nextAfter(collection, setId)` in `library.js`, beside the other things
  derived from the tree. Display order, across season and folder boundaries,
  which is the order `levelEntries` and `divisionBlock` already walk.
- An "Up next" card over the end of a title, with a countdown that advances
  unless cancelled. Cancelling is remembered for that title so it does not
  count down again on a rewatch of the last minute.
- Preload: once the current title is comfortably buffered, ask the server for
  the first few megabytes of the next one and drop them on the floor. The
  point is the server's `ChunkCache`, which is what actually holds them, and
  which the transcoder reads through as well — so it helps whichever way the
  next title plays.

Bounded: one preload per title, 8 MB, only when the buffer is healthy, and
abandoned the moment the dialog closes.

Success: next crosses a season boundary; the last episode of the last season
has no next; a cancelled countdown stays cancelled; preload fires once.
