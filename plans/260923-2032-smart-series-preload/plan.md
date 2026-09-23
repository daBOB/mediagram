# Smart series preload

Opening an episode fetches the next two episodes of the show into the chunk
cache in the background, in full, so they start (and seek) with no Telegram
wait.

## Decisions (user, 2026-09-23)
- Whole episodes, not their first minutes.
- A server config key, not a UI toggle: `MEDIAGRAM_SERIES_PRELOAD`, **on** by
  default; `0`/`false`/`no` turns it off.
- Series episodes only (kind `ep`); not lessons, not hand-built lists.

## Design
- **Which episodes:** the page names them, with the same `nextAfter()` that
  drives Play next — the web player's ordering is the reference; the server
  does not invent a second one.
- **Route:** `POST /api/preload` `{"setIds": [...]}` → 202. Ids that are not
  playable episodes are dropped. 404 when the key is off.
- **Worker:** one set at a time, one run at a time, so a preload never holds
  more than one of the four download-gate slots — playback keeps three.
  A new request replaces what is still waiting (the viewer moved on); the set
  already downloading finishes.
- **Cache:** `CachedReader.fill()` fetches every missing chunk of a part,
  without readahead. Held sets are skipped.

## Phases
| # | Phase | Status |
|---|-------|--------|
| 01 | `CachedReader.fill`, `SeriesPreload` worker, config key, route, wiring, page call, tests | done |
| 02 | Docs (running-the-player, changelog), version | done |

## Deliberate difference
Android plays straight from Telegram with no server cache, so there is
nothing for it to preload into; it is not owed this until it has a cache.

## Validation (2026-09-23)
- 10 new tests (`web/test/series-preload.test.ts`); web suite 1271 pass.
- Live player: `POST /api/preload` for 30 Rock S4E15 (214 MB) → 202, fetched
  at ~1.6 MB/s, held after ~150 s, catalog row flipped to `offline` at once.
- Throughput is one sequential run at a time: fine for SD/720p episodes,
  roughly 2x realtime for a 2 GB, 45-minute one. Two runs at a time would
  double it if needed.
