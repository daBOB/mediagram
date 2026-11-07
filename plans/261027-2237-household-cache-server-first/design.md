# Household cache server first — design

Date: 2026-10-27. Status: builder-checked; awaiting the user's approval.

## Intent

One household, one shared chunk store. Every player in the home — the web
player, phones, tablets, the TV box — reads the home cache server
(`mediagram_cache`, today at `192.168.0.240:7788`) before anything else, and
shares back what it had to fetch. A chunk then crosses Telegram once per
household, not once per device, and the household opens fewer Telegram
connections. The account is flood-limited, and on the TV box Telegram is the
slow path (15–20 Mbit/s).

This reverses a decision recorded on 2026-09-25 in
`plans/260925-2046-external-cache-volume-and-lan-chunk-server/plan.md`
("The web player needs no LAN tier: it is itself a server with its own
cache"). The user reversed it on 2026-10-27 because all players in a
household should share one store.

## Decided by the user

1. **Read order, on every player:** home cache server → the device's own
   cache → Telegram. Chosen after the speed test below showed the server
   reaching the TV box faster than its own USB stick.
2. **A chunk that came from the server is also kept in the device's own
   cache.** A phone away from home, a server that is down, or a film the
   full server has since evicted still plays from the local copy.
3. **Android keeps its own cache in one place: external storage (USB stick,
   SD card) or internal, never both.** External when one is present,
   internal otherwise.
4. **What a player fetches from Telegram is sent to the server in the
   background.** A reader never waits on a write.
5. **The web player gets a "Home cache server" block in Settings**: address,
   pairing token, and a status line ("Connected to `<host>`, holding
   `<size>`"), like Android's.
6. **A server that cannot be reached is skipped** — straight to the
   device's own cache, then Telegram — and not asked again for a minute,
   as Android does today (`LanFirstChunkSource`, 60 s down window).

7. **The default cache size does not change when the default location
   moves to external storage.** It stays 2 GiB (`CACHE_MAX_BYTES`); a viewer
   raises it in Settings → Storage, as was done on the TV box (256 GB). A
   bigger automatic default could quietly fill an SD card the phone also
   uses for photos.

Items 5 and 6 were proposed and accepted ("looks fine"), not specified by
the user word for word. Item 7 is the recommendation the user chose.

## Measured, 2026-10-27 (TV box on 5 GHz Wi-Fi, server full at 100 GiB)

| Path | Speed |
|---|---|
| Telegram → TV box | 15–20 Mbit/s |
| Server → TV box, one connection, 1 MiB GETs pipelined | 191–199 Mbit/s |
| Server → TV box, four connections | 97–152 Mbit/s (worse) |
| TV box USB stick, 4 MB reads / small reads (shell, via FUSE) | 133 / 35 Mbit/s |
| Server → dev box, wired: from server disk / from server RAM | 380 / 769 Mbit/s |

Box → server round trip: 2.6 ms. The box's Wi-Fi, not the server, is its
~200 Mbit/s ceiling. The server is full (budget 100 GiB) and evicts LRU;
the box's stick alone holds 179 GB.

## Goals

- Both surfaces read in the order above and share back to the server.
- The web player reads from and writes to the server exactly as Android
  does: same API, same set ids, same chunk numbers. A chunk the browser
  fetched plays from the server on the TV, and the other way round.
- Android, with nothing chosen in Settings → Storage, caches on external
  storage when present.

## Non-goals

- No change to `mediagram_cache` or its v1 wire protocol.
- No cache split across two volumes.
- No change to the server's budget or eviction.
- No mDNS discovery on the web player (see "My calls").

## Anti-goals — failure even if it technically works

- A slow or dead server stalls playback. A server read has a short
  deadline (Android's is 2 s per GET) and every failure falls through to
  the local cache, then Telegram.
- A write ever delays a read.
- The pairing token reaches a browser, a log, or the wire. Only the HMAC
  signature travels; the web Settings block shows "paired", never the token.
- Server-first costs a local copy: a server hit that is not also written
  locally breaks decision 2.
- The two surfaces disagree on order or on what gets shared. That is a
  Surface Parity defect (`CLAUDE.md`). On this feature Android is ahead
  and the web player follows it.

## Constraints from the code

- **Server API** (`crates/mediagram-cache/src/http.rs`, documented in
  `docs/running-the-player.md` § Home cache server):
  `GET|HEAD|PUT /v1/sets/{id}/chunks/{n}`, `GET /v1/sets/{id}`,
  `GET /v1/status`. Chunk = 1 MiB of the whole set; the last is the
  remainder. GET needs no token. PUT carries `X-Set-Total` (the set's whole
  byte length) and `Authorization: MGC1 <hex>`, where the hex is
  `HMAC-SHA256(token as its 64 ASCII chars, "PUT\n{path}\n{total}\n" +
  hex(sha256(body)))`. The reference implementation and a shared test
  vector live in `android/core/playback/src/main/kotlin/LanChunkSigning.kt`.
- **Chunk numbering differs.** The web cache keys chunks per part
  (`web/src/cache/key.ts`: set, part index, chunk in part). The server and
  Android key them per set. Parts are 1 MiB aligned
  (`crates/mlib-spec/src/part_plan.rs` rejects unaligned part sizes), so
  set chunk = `span.off / 1 MiB + chunk in part`
  (`PartSpan` in `web/src/range.ts`) and every non-final part ends on a
  full chunk.
- **Android's local cache sits in front by construction.** The player reads
  through media3 `CacheDataSource` (`PlayerFactory.kt`), which checks
  `SimpleCache` before calling its upstream
  (`MlibDataSourceFactory` → `ChunkMemo` → `LanFirstChunkSource` →
  Telegram). Server-first needs a server read *before* `CacheDataSource`,
  and a hit must land in `SimpleCache` (decision 2). The series and film
  preloaders share this factory (`CacheDataSourceWriter`), so the new
  order reaches them too.
- **Android volume choice** (`CacheLocation.kt`): today a `null` choice
  resolves to internal. `scheduleStaleVolumeCleanup` (`CacheOpen.kt`)
  deletes the cache on every present volume that is neither opened nor
  chosen, and moving the cache takes effect at the next process start.
- **Android skips the server on a metered network** (`UnmeteredNetworkCheck`).
  Keep that.
- **Web reader** (`web/src/cache/reader.ts`): disk → in-flight → Telegram
  runs of up to 4 chunks (`MAX_RUN_BYTES`), plus readahead and the series
  preload (`reader.fill`). Duplicate concurrent fetches of one chunk drew
  Telegram flood waits before (`InFlightChunks` exists for that).
- **Web settings** live in the state DB's `settings` table
  (`web/src/state/settings.ts`, today only the cache budget). The Settings
  page's cache section is `cacheSection` in `web/public/lib/settings-view.js`.
  Settings writes are admin-gated (`web/src/settings/admin-gate.ts`).
- **Versioning:** bump all three manifests together, a minor bump
  (`CLAUDE.md` § Versioning).

## My calls (made by the designer, not the user — flag any you disagree with)

- **A slow server counts as down.** Today a GET only fails past a 2 s
  deadline, which still accepts ~4 Mbit/s. With the server read first, a
  slow-but-alive server would also slow chunks the device already holds.
  Rule, the same on both surfaces: if a chunk takes longer to arrive from
  the server than it takes to play (from the title's bitrate, which
  Android's `ReadAhead` and the web catalog already know via duration),
  the server is skipped for the same 60 s as a failure. The 2 s deadline
  stays as the upper bound. On the box (~200 Mbit/s) this never triggers,
  even for 4K.
- **Share whatever follows a server miss.** After the server misses, the
  chunk is sent back to the server whether the local cache or Telegram then
  supplied it. That is simpler than today's Android rule (share only after
  a Telegram fetch), and it refills a full server from devices that still
  hold what it evicted. Same bounded, drop-oldest write queue as Android
  (`LanWriteQueue`).
- **Web pairing is manual:** address and token typed into Settings; the
  address is normalised as Android's is (`http://` added, port 7788 added
  when missing — memory: a port-less address hits openresty on :80). No
  mDNS on the web player; it runs on a wired machine with a fixed address
  to type once.
- **The token is stored server-side** in the player's `settings` table and
  never returned to the browser.
- **Android default volume:** with nothing chosen, the first external
  volume present wins, else internal. When the default moves (a stick
  plugged in later), the existing stale-volume cleanup clears the old
  location, exactly as a manual move does today. With the server first,
  the household keeps whatever the server still holds.
- **Web status page** gains one line: bytes served from the home server vs
  from Telegram since start, so the feature can be seen working. Android's
  System screen already has its "Source" row.
- **Server reads go one chunk per GET over a kept-alive connection**, not
  several connections at once: four connections measured slower than one
  on the box.

## Left to the builder

- Where the server-first layer sits on Android (a `DataSource` in front of
  `CacheDataSource` that writes hits into `SimpleCache`, or another shape)
  and how it keeps `ChunkMemo`'s de-duplication.
- How the web reader interleaves server GETs with its Telegram runs and
  readahead without fetching any chunk twice.
- Test shapes, the signing test vector reuse on the web side, phase order.

## Verification the user will expect

- The web player and the TV box each play something only the other one
  fetched, with no Telegram traffic for it (web status line / Android
  "Source" row).
- With the server stopped or unreachable, both still play from local cache
  and Telegram, with no stall beyond the GET deadline.
- A device test must not change settings (memory: a TV focus walk once
  moved the cache budget) and must use the test profile.
