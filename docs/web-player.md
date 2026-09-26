# The web player

Moved out of `system-architecture.md` §7 (2026-09-25) to keep that file under
the 800-line documentation budget. Section numbering there is unchanged; §7
now points here.

`web/` is a second program in a second language, and the first question a
reader will have is why.

`mediagram serve` (Rust, `serve/`) answers Range requests over a set's
concatenated parts and is complete and verified, but it can only run where
the uploader runs: it needs the uploader's `library.db` and the uploader's
session file, both on local disk. A player is wanted on a phone, on a
television, on a small host somewhere else. So the player is a separate
process that needs nothing from the uploader's filesystem — and that means it
has to speak MTProto itself.

```
browser ──HTTP──> Bun (web/src) ──MTProto──> the channel
                    │
                    ├─ catalog:  a published package, else the channel's pinned index
                    │            (this machine's library.db only as a fallback)
                    ├─ bytes:    Range over the parts, through a disk cache
                    └─ ffmpeg:   HLS, for what the browser will not decode
```

**Two MTProto implementations, on purpose.** Rust uses `grammers`; the player
uses `teleproto` (the maintained fork of the archived GramJS) on Bun. Two, not
three: the Android app ([§8](#8-playback-the-android-app)) reuses the Rust one
through UniFFI rather than growing a Kotlin client. They are not a port of one
another and never share code — what they share is the wire
format in `mlib-spec` and the index schema, which is exactly the boundary
[§9](#9-backend-portability) says a second client reuses. The Rust
implementation stays as the reference, and the player's bytes are checked
against ground truth rather than against it agreeing with itself: a set
streamed out of the player hashes to the `parts.sha256` the uploader recorded,
whether fetched whole or reassembled from separate ranges. That check is run
by hand against the live channel rather than in the test suite — there is no
channel in CI — and its results are recorded in
[`docs/project-changelog.md`](project-changelog.md).

The consequence to keep in mind is that a player holds an **auth key for an
account with access to the channel**. Not a read token, not a scoped
credential — the account. One auth key also cannot serve two clients at once,
so a host running both needs two. Both facts are why the player binds to
loopback and belongs behind something that authenticates:
[`docs/running-the-player.md`](running-the-player.md).

## Module map (`web/src`)

```
index.ts           executable entry point and application service composition
application/       catalog selection/following, subscription catch-up, ordered
                   shutdown, listener address reporting, and import-safe startup
config.ts          MEDIAGRAM_* environment, with secrets redacted in the log
server.ts          the listener, on node:http rather than Bun.serve
routes.ts          HTTP dispatch and feature-router composition
response.ts        byte-range planning and shared buffered-response framing
http/              request/response contracts, browser-write checks, static
                   files, and streaming with explicit range headers
catalog/           catalog/search presentation, metadata readers, asset and
                   artwork endpoints, and audio-track probing
range.ts           byte ranges to per-part reads, and the 4 KiB alignment
login.ts           issues this host's session; writes web/.env, mode 600
catalog.ts         library.db queries; PLAYABLE_SQL, mirrored from mlib-spec
client-reach.ts    a viewer on this network, or one across an uplink

status/            what the player is doing: the startup facts worth keeping,
                   a pure snapshot builder, and a route only a local viewer
                   is answered on
telegram/          teleproto client, turning planned reads into bytes, and
                   dependency-free caption conventions shared by channel policy
cache/             512 KiB chunks on disk: keys, store with quota, reader,
                   the readahead tracker behind MEDIAGRAM_CACHE_READAHEAD, and
                   which sets are held in full, for the offline badge
package/           the mlib-package-v1 reader: pointer, cipher, tar, refresh,
                   and the artwork a package carries
transcode/         playback HTTP negotiation, ffmpeg arguments, encoder probe,
                   session registry, process supervision, and HLS delivery
public/            the page: the start page, shelves, the player dialog,
                   hls.js when needed, and the buffer watch that converts
                   down on a slow link
```

Within `public/lib/`, `playback/` owns the player and its controls, with
`playback/streaming/` owning adaptation and HLS resources and `playback/notes/`
owning note loading, parsing and rendering. `catalog/` owns shelf and detail
rendering; `status/` owns the system
panel. Shared catalog, state, formatting, and playback-policy helpers remain
at the library root. The installed HLS client is still served at `/lib/hls.mjs`.

`public/style.css` imports the presentation modules in `public/styles/`:
`theme.css` owns local fonts, tokens and the reveal primitives; `shell.css`
owns the library rail (`.library-rail`, the reader's own shelves; `.rail` is
the player's control row) and the sticky department bar; `catalog.css` owns
shelves, title pages and the backdrop band; `home.css` owns the magazine home;
`library-controls.css` owns profiles, collections and status; `playback.css`
owns the dark player and Featured dialogs. Dark is the default, with a light
"paper" variant under `prefers-color-scheme: light`; artwork-backed blocks use
fixed on-image colours. Fraunces sets display type, Newsreader (upright and
italic) decks and quotes, Geist the interface; all are self-hosted.

Application shutdown closes admission to speculative cache reads and waits for
existing warming to finish before disconnecting Telegram. The HTTP listener
also drains routing, streaming and cancellation cleanup; the shared source
remains available until those owners release it.

## Pages of the Movies shelf

The Movies shelf draws 48 films a page, which fills the last row of plates
at every column count the grid uses. The page is part of the address
(`#/movies/page/3`; page one stays the plain `#/movies`), so back, reload and
a shared link all land on it. The library is already whole in the page:
`public/lib/catalog/pager.js` only slices it and draws the links, and the
router matches `page` before any collection name. Series and Tutorials shelves
are short enough not to page, and the Android catalog does not page yet.

## The Featured reel

The Movies heading's Featured button opens `<dialog id="featured">`, drawn by
`public/lib/catalog/featured-reel.js`. Which films it shows is the pure rule in
`featured-picks.js`: films with a poster that the profile has not watched,
shuffled, at most twelve. Each slide is built when shown and crossfaded over
the last; the drift, fade and rising text are CSS, so reduced motion is the
stylesheet's global rule. Opening pushes a history entry: back closes the
reel, and Play or Details wait for that entry to be popped before acting, so
the film's page is not undone by it. Taglines and scores come from
`/api/shows/:key`, asked once per film and for the next film while one holds.
The phone has the same reel (`FeaturedReel`, `FeaturedSlide` in
`ui.catalog`), over the posters it fetches itself: `pickFeatured` is a port of
`featured-picks.js`, a tap on the poster stands in for Space, and back closes
it as the dialog's own dismissal.

## Where the player opens

`#/home`, and the rows on it are decided in `public/lib/catalog/home-shelves.js` and
drawn in `public/lib/catalog/home-view.js` — the same split every view here has, and
the reason the rules are testable without a DOM.

The page is laid out as a magazine front section: a rotating cover story on a
TMDB backdrop, three single-title features, Continue beside a pull-quote, then
Recently added beside a numbered "This month". What each part holds is the pure
`editorial-picks.js`: the **Editor's choice** is a household pin
(`/api/editors-choice`, watch-state v8 `editors_choice`, synced like the Kids
mark, newest live pin wins), **Trending on TMDB** ranks by `shows.popularity`
(index v8; without it the card says "New in the library"), and the **Staff
pick** rotates daily among the ten best-rated unwatched films. The cover and
quote are seeded by the day, so a redraw never reshuffles them. Every label and
line comes from the catalog; none is invented. Android has none of this yet:
owed under Surface Parity, recorded in
`plans/260925-2014-web-player-magazine-redesign/plan.md`.

Two of those rows answer "what now?" from the two facts the library actually
has. **Next up** carries one card per show or course underway: the episode in
progress if there is one, otherwise the first unwatched episode after the one
finished most recently, shows ordered by when they were last watched. **The
latest rows** count arrival rather than release, and rank a show by its
newest episode so a series still being uploaded keeps its place.

Both needed a fact that was being thrown away. A catalog row carries
`addedAt`, because `groupLibrary()` sorts by title and the arrival order the
query produced does not survive it. And `/state` serves `watched` as
`[{setId, finishedAt}]`: finishing an episode *clears* its position, so
without the completion's date a show watched to the end of an episode has no
timestamp anywhere the page can see, and would rank behind one glanced at
months ago.

The main dispatcher and the extracted catalog and HTTP handlers each stay under
200 lines; the state router remains a larger module. A catalog swap rebuilds
catalog presentation and state routing together; requests already in flight
retain the router and database they started with. Buffered responses share one
framing helper, including HEAD responses. State, preload and HLS session deletion
share the same Origin/Host checks; body-bearing writes also require JSON.
Media workers obtain their internal HTTP address from the bound listener, so
OS-assigned ports and specific IPv4 or IPv6 binds work for audio probing,
transcoding and thumbnail generation.

Thumbnail generation first checks the held-title badge, then reads
`GET /api/sets/:id/cached-stream`, which also supports HEAD and the same single
Range framing as `/stream`. This route reads disk chunks only, with no upstream
fetch or readahead. Missing or truncated chunks fail the response body, so a
failed generation removes its partial sheet instead of publishing it. The
ordinary `/stream` route still fills cache misses from Telegram; without a cache,
the cached-stream route answers 404 and thumbnail generation is disabled.

`routes.ts` deliberately builds a description rather than a `Response`:
`Bun.serve` replaces a manually set `Content-Length` with chunked encoding for
any streamed body, and ffmpeg cannot seek an HTTP source without one — it
reads from byte zero instead, which would quietly make every conversion start
at the beginning of the film. Framing is therefore stated in one place and
written verbatim by `server.ts`.

Nothing the browser is served ever carries a `chat_id`, a `message_id` or a
`doc_id`. The browser is told what it may play, never where the bytes live.

## Consumers of the index

Five now, which is the reason the schema and the caption format are specified
rather than implied:

| Consumer | Reads | Writes |
|---|---|---|
| `mediagram` (add, resume, edit, remove, rescan, verify) | `library.db` | `library.db` |
| `mediagram export-package` | `library.db`, read-only | the package |
| `mediagram serve` | `library.db`, read-only | nothing |
| the web player | a package's `library.db`, the channel's snapshot, or a local one, read-only | its installed channel snapshots |
| the Android app | a channel snapshot's `library.db`, read-only | its own sidecars |

Every read-only consumer opens SQLite with `SQLITE_OPEN_READ_ONLY` rather
than merely not issuing writes: a writable handle would let it checkpoint the
WAL or replay a migration on an index the uploader owns.

## Adapting to the link

Which titles are converted is decided twice. Once before playback, from the
catalog: codecs a browser cannot decode, and — for a viewer the server places
outside the local network — a bitrate above the uplink budget. That decision
is made from numbers, and numbers about a link are frequently wrong.

So the page also measures, in `public/lib/playback/streaming/`:

```
buffer-health.js   seconds buffered ahead, and the rate it is filling at
adapt-bitrate.js   given a measurement, what to switch to — or nothing
adapt-playback.js  the loop: watch the element, act, do not thrash
```

Split three ways because the parts fail differently. A measurement is wrong
when it misreads a satisfied player as a starving one; a decision is wrong
when it restarts playback for a gain nobody would notice; a loop is wrong when
it does either of those every few seconds. Each is pure enough to test on its
own, and the two properties that took a live run to find are written down in
`buffer-health.js`: a full buffer looks exactly like a slow download, and a
stalled player looks exactly like a healthy one unless the rate is measured
against the wall clock.

The switch is a conversion at a requested bitrate — `?maxrate=` on the
transcode route, clamped between a floor and the configured cap, and part of
what identifies a session, since two viewers wanting different rates want
different encodes.

## Saying what it is doing

The player works a great deal out at startup and only ever printed it: which
catalog opened and whether the refresh actually succeeded, which encoder
initialises, what the cache may hold. That terminal is usually on another
machine, in another room, or gone. `status/` keeps those facts instead
(`facts.ts`), folds them with what has to be read at the moment of asking
(`snapshot.ts`), and serves the result at `/api/status`.

The one fact worth naming is the refresh verdict. A player quietly serving a
package it could not refresh looks exactly like one serving a current package,
and nothing else the page shows would say otherwise.

**The route answers this household's own devices and 404s everyone else** —
404 rather than 403, because a 403 confirms to a caller from outside that
there is something here worth a second request, and this API has no
authentication of its own. The address is checked before the method, so
"wrong method" and "nothing here" are indistinguishable from outside. The
page follows the same rule: the only way to `#/system` is the masthead entry,
and it is unhidden only after `/api/status` has answered a `HEAD`. A viewer
from outside never learns the page is there.

Own devices, not the local network, and the difference is the tailnet.
`client-reach.ts` answers two questions about an address, and they part over
100.64/10: `isLocalAddress` says what the *link* can carry, so a tailnet peer
is remote there and its titles are converted; `isOwnNetwork` says whose
device is asking, and a Tailscale peer had to be admitted to the tailnet
before it could send a packet, so it is the same phone that would be on the
sofa if it were home. Only this route asks the second question.

What it reports beyond the startup facts: the cache's hits, misses and
evictions; bytes fetched upstream, and the failed reads that are the one
upstream problem a viewer feels and cannot see; what the conversions are
holding on disk, which unlike the cache has no budget and no eviction beyond
the idle reaper; and the uptime. The *rate* upstream is not in the snapshot:
the server does not know how often it is being asked, and an average since
startup is not the number anyone watching a stall wants. Two readings and the
seconds between them go to the page, which subtracts.

Four more groups sit alongside those, each read fresh on every poll:

- **Telegram link** (`telegram/link-stats.ts`). Per-DC request counts, bytes,
  latency percentiles over the last 256 requests, and flood waits. Measured
  around `MeasuredClient.invoke`, the one seam every download passes through —
  `iterDownload` and `partMedia` both call it on the instance — so the
  latency includes any lease wait and flood sleep, which is what the viewer
  actually waited for, not just the wire time. Reconnects count the *main*
  MTProto connection only: a download-DC sender is pooled and rebuilt without
  notice, and its failures already show up as that DC's own request errors.
- **Watching now** (`status/playback-reports.ts`). Every open player POSTs its
  own reading — mode, codecs, bitrate, buffer health, dropped frames — every
  5s to `POST /api/status/playback`, kept for 15s and shown without the
  per-page viewer id that names it in memory. This is the one group the
  server cannot measure itself: the System page and the player it describes
  are usually different devices, a phone checking on what the television is
  playing.
- **Conversion progress** (`transcode/progress.ts`). ffmpeg's own
  `-progress pipe:1` output — speed, fps, output position — read off stdout
  independently of the `-loglevel error` stderr log, plus CPU time from
  `/proc/<pid>/stat` between two readings. Segment counts come from a
  directory listing taken at poll time, not tracked as ffmpeg writes.
- **Host** (`status/loop-lag.ts`, `status/disk-free.ts`). Resident and heap
  memory, event-loop lag over the last complete 10s window, free space under
  the cache and transcode directories (merged into one row when they share a
  device), and the Bun version.

None of this touches the byte path: a download counts a request and writes a
ring-buffer slot, once per `upload.GetFile`, and nothing else runs per chunk.
Percentiles, directory listings and `/proc` reads happen when the page polls
or on ffmpeg's own 2s progress tick.

The panel polls every two seconds, which is why both directory measurements
sit behind a fifteen-second memo (`status/dir-bytes.ts` for the transcodes,
`ChunkCache.sizeOnDisk` for the cache): counting bytes on disk means statting
every file, and a twenty-gigabyte cache is some forty thousand of them. The
two scans run together rather than one after the other, so the slow case is
the longer of them and not their sum.

What a reading *says* is split by concern rather than kept in one file:
`status-lines.js` for the readings from the first pass, `status-link-lines.js`
for the Telegram link and host groups, `status-session-lines.js` for
conversions and playback — apart from where their nodes go in `status-view.js`,
for the reason `buffer-health.js` is apart from `adapt-playback.js`: only the
formatters can be tested without a browser.

### Differences from Android

Android has a system screen and a playback-stats overlay
(`docs/superpowers/specs/2026-09-20-android-system-menu-and-playback-stats-design.md`
§5, §7, §9). Against the four groups above:

- **Telegram link and host are owed.** Android's system screen already shows
  `connected`, fetch counts and failed reads, and its own uptime and version —
  but not per-DC latency, flood waits, reconnects, memory or free disk. Those
  belong in `mediagram-core`'s grammers byte path, not this player, so they
  are a separate Android/core plan rather than something this phase builds.
- **Watching now has no Android counterpart, on purpose.** Its
  `PlaybackStatsOverlay` already shows codec, buffer, cache, reads and dropped
  frames — covered on the device doing the playing. The web's version exists
  because the web *can* be asked about a device it is not running on; Android
  cannot watch itself from across the room.
- **Conversion is a deliberate difference**, already recorded as "No
  Conversion block" in
  `docs/superpowers/specs/2026-09-20-android-system-menu-and-playback-stats-design.md`
  §9: Android decodes natively and never transcodes, so there is no encoder,
  no session and nothing this group could report.

Three smaller readings come from measurements that were already being taken
and thrown away — the buffer's fill rate in the HUD (`buffer-health.js`
measured it; `preload-readout.js` showed only the depth), the reason a title
is being converted, now on the shelf badge as well as in the player, and the
technical line under a title, whose average bitrate is what makes the
`needs transcode` badge legible.

## Settings: changing the account and the cache without a restart

`/api/settings/*` (`web/src/settings/`) is the one router with a lock of its
own. It answers the same own-network 404 `/api/status` does, and past that a
token: `POST /api/settings/unlock {token}` mints an HttpOnly, `SameSite=Strict`
session cookie (`admin-gate.ts`), scoped to `/api/settings` and compared with
`crypto.timingSafeEqual` against a token generated on first start (or
`MEDIAGRAM_ADMIN_TOKEN`) — only its path is ever logged. Every write also
needs the same same-origin, JSON-only guard (`http/browser-write.ts`) every
other write route already used.

**The account is swappable at runtime.** `telegram/connection.ts`'s
`TelegramConnection` holds the live `Telegram` client or `null` (signed out);
`TelegramSource` and `TelegramStateChannel` read it fresh on every call rather
than holding one, so a restart — sign in, sign out, a new api id/hash — never
leaves them pointed at a client that is gone. `restart()` is strictly
sequential: gate reads, disconnect the old client, open the new one, release —
never two clients on one auth key (`login.ts`, measured). A channel switch
needs none of that: `Telegram.withChannel` reuses the same client with a
different `InputChannel`, and `CatalogFollower.retarget(root)` points the
existing follower at a fresh per-channel directory
(`~/.cache/mediagram-channel-catalog/<chat id>/`) so a newly chosen channel's
`pushed_at` is never compared against an unrelated channel's history.

**Sign-in runs on its own client**, on an empty session — a separate auth key
from the live one, so an attempt in progress can never collide with it
(`settings/sign-in.ts`). Only a finished attempt's session string reaches the
live connection, through the same `restart()` every other identity change
uses.

**Sessions**: `GET /api/settings/sessions` / `POST .../sessions/revoke` list
and end this app's logins (`account.getAuthorizations`/`resetAuthorization`),
scoped to this app's `api_id` plus the current row — the account's official
Telegram apps are not listed or touchable from here. `settings/sessions.ts`'s
`shape`/`revokeError` are pure and pinned to
`web/test/fixtures/authorizations/cases.json`, which
`crates/mediagram-core::api::sessions` also reads, so the web and Android
session lists agree.

**The admin gate is this plan's one deliberate difference from Android's
Settings screen** (§8): the phone has no gate at all, because the account
holder's own phone does not need one, while the web player can be reached by
anyone on the household's network or CGNAT range. Everything else — sections,
rows, wording, the sessions list — is the same on both.

## Updates Telegram pushes

Every session of the account hears about a message sent, edited or pinned in
the library channel within milliseconds (measured: 0–41 ms on teleproto and
grammers alike). `telegram/channel-events.ts` listens for those, reduces each
to a small shape, and `telegram/updates.ts` decides whether it matters:
another device's `#mlib-state` document is a **state** event, a newly sent
`#mlib-index` or a pin is an **index** event, and everything else — this
device's own writes, part captions sharing the `#mlib` hashtag, unpins, pin
notices, other channels — is nothing. Events of a kind are released at most
once per five seconds, timed from the first, so an upload's burst of part,
index, pin and unpin is one event and a long upload cannot starve it.

An event is a hint, never data. A state event runs the ordinary sync round,
which reads the pin list itself; the five-minute timer stays, and a missed
update costs exactly the wait it always did. Nothing missed while the
connection was down is replayed — a `StringSession` keeps no update state —
so the listener is subscribed before the start-up round, which covers what
came before it. The listener runs whether or not state is shared.

An index event installs the channel's newest snapshot, chosen as the Android
core chooses it (`channel-index/pick-newest-index.ts`, pinned to core's
`pick_index` by `web/test/fixtures/pick-index/`): proven to be a library while
staged, then swapped in under `MEDIAGRAM_CHANNEL_INDEX_DIR`. The server builds
its router again over the new handle, rescans which titles are held, and tells
every open page over `GET /api/events` — server-sent events, one way, with a
heartbeat. The page never speaks to Telegram: it reads `/api/sets` again when
told, and whenever its event stream reconnects, since an event sent while it
was away is not sent again. A title playing defers the redraw to its close.
The snapshot has descriptions but no artwork, so the server then runs the
uploader's own `mediagram posters --index <snapshot>` — one TMDB client, not a
third — and sends a second notice once the covers are on disk.
A configured package is still the catalog; the channel's pins do not override
it. The state documents the listener reads are written by the Android app too,
in the same format; see [§8](#8-playback-the-android-app).

The rule is shared with the Android core and pinned by one set of fixtures
both read, `web/test/fixtures/channel-updates/`.

## The codec policy

Which profiles are handed to the browser as they are, and which are converted
first, is in
[`docs/running-the-player.md`](running-the-player.md#what-plays-directly-and-what-is-converted).
The lists live once, in `web/public/lib/playable.js`, and a test fails if the
document stops matching them.

