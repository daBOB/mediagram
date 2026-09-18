# Player system information — design

The player knows a great deal it never says. Facts computed at startup are
printed to a terminal nobody is looking at; measurements taken during
playback are used once and discarded. This adds four surfaces that say them,
each to the audience that can act on it.

## The four surfaces

| Surface | Audience | Source |
|---|---|---|
| Per-title technical line | anyone browsing | the catalog row the page already has |
| HUD reassurance | a viewer mid-playback | measurements the page already takes |
| Colophon | anyone | arithmetic over `/api/sets`, plus three fields |
| Diagnostics panel | a viewer on this network | a new `/api/status` |

## 1. Per-title technical line

`format.js` has `codecLine(set)` — `container · vcodec · acodec` — used by
`search-view.js` and `course-view.js`. It stays exactly as it is; those are
compact places and a longer string would wrap.

A sibling, `technicalLine(set)`, adds what the index already carries and the
page has never shown:

```
1080p · HDR10 · MKV · HEVC · EAC3 · 14.2 GB · 5 parts · 9.4 Mbps
```

`SDR` is omitted — it is the absence of a fact, not a fact. The bitrate is
`total / duration`, which is the number that makes the `needs transcode`
badge legible: a viewer who sees 13.9 Mbps beside a badge understands it,
and a viewer who sees only the badge does not.

Shown in the player HUD beside the title, and on shelf cards.

## 2. HUD reassurance

Three additions, all from measurements already taken:

- **Fill rate.** `buffer-health.js` measures the rate the buffer fills at,
  against the wall clock, and `preloadReadout` shows only the depth. Depth
  alone cannot tell a satisfied player from a starving one — that property
  is written down in `buffer-health.js` as the one that took a live run to
  find. The readout gains the rate when it is known and interesting.
- **Why a title converted.** The note says *that* a title is converting and
  never *why*. `playbackFor()` already decides, and its reason is one clause.
- **Dropped frames**, from `getVideoPlaybackQuality()`, and only when the
  count is not zero. A zero is noise.

## 3. Colophon

`app.js` writes `N playable sets` into the footer. It becomes what a book
prints in the same place: counts by kind, total runtime, total bytes, where
the catalogue came from and how old it is, and the schema it is at.

The arithmetic needs no server. `/api/sets` already carries `kind`,
`duration` and `total` for every row, so the page sums what it holds.

Three fields do come from the server, and they go onto `/api/player` — the
route that already answers "facts about this session" and is already fetched
once at startup:

```json
{ "remote": false, "maxBitrate": 8000000,
  "catalog": { "origin": "package", "publishedAt": "2026-09-16T…", "schema": 6 } }
```

`origin` is `package` or `local`. `publishedAt` is the package pointer's
`created_at`, and `null` for a local index. Saying *when* a catalogue was
published tells a remote viewer nothing about *where* it lives.

## 4. `/api/status` and the diagnostics panel

### Local viewers only, by 404

`isLocalAddress(request.client)` is already computed in `routes.ts`. A
request from outside the local network gets a 404, not a 403: a 403 confirms
there is something there, and this API has no authentication of its own.

### Its own router

`web/src/status/routes.ts`, following `state/routes.ts` exactly — a router
that answers `null` for paths it does not own, so `routes.ts` does not grow.
`routes.ts` is already over twice the 200-line limit, which the architecture
document names rather than hides.

### The snapshot

`index.ts` computes the catalog verdict, the encoder and the cache budget at
startup, prints them, and throws them away. They are collected into one
`StartupFacts` value instead and passed to the server. Live numbers are read
per request from the objects that hold them:

```
catalog     origin, publishedAt, refresh verdict + reason, schema, sets, posters
cache       held / budget / readahead, hits, misses, fetched, evicted — or null
encoder     name, kind, device
transcodes  running, max, and per session: setId, seek, maxrate, audio, watchers
telegram    connected
state       remembered
process     uptime seconds
```

New counters: plain integers on `ChunkCache` and `CachedReader`. Nothing
allocates per request. `TranscodeRegistry` gains `list()` beside `count()`.

### The panel

`#/status`, a hash route like every other section, absent from the masthead.
It is reached by one link in the colophon, rendered **only when
`/api/status` answers 200** — so a local viewer finds it and a remote viewer
never learns there is anything to find.

Polls every 2s while open and stops on leaving. Set in the page's own faces,
as definition rows: this catalogue is Fraunces and Newsreader, and a
monospace telemetry grid would look bolted on.

## What is never shown

`chat_id`, `message_id` and `doc_id` reach no surface here, on the same
terms as the rest of the player: the browser is told what it may play, never
where the bytes live. The package URL and its key are absent from the
snapshot. Host paths, the DRM render node and the uptime appear only behind
the local-only route.

## Testing

The snapshot builder and the format helpers are pure and unit-tested the way
`response.ts` and `range.ts` are. The panel's polling loop is the one
stateful piece and gets the `adapt-playback.js` treatment: watch, act, do not
thrash. `scripts/check.sh` is the gate.
