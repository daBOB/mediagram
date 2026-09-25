# Project changelog — 2026-09-19 to 2026-09-23 (archive)

Older dated entries, moved out of `project-changelog.md` to keep it within the
documentation budget. Newer history is in that file.

## 2026-09-23

**Added**

- Smart series preload. Opening an episode in the web player takes the next
  two into the player's cache in full, in the background, so they start and
  seek with no Telegram wait and play offline. The page names them with the
  same `nextAfter` that drives Play next; `POST /api/preload` accepts at most
  two, episodes only. One set and one run at a time, so a preload holds at
  most one of the four download slots and playback keeps the rest. On by
  default; `MEDIAGRAM_SERIES_PRELOAD=0` turns it off. Measured: a 214 MB
  episode held in about 2.5 minutes. Android plays straight from Telegram with
  no cache, so it has nothing to preload into yet.
- Page changes in the web player turn over with a short fade (View
  Transitions); catalog refreshes and search typing stay still, and reduced
  motion gets the plain swap.
- Age ratings decide the Kids shelf. `mediagram add` and `mediagram metadata`
  now record each title's TMDB age rating for the library's country — the
  FSK, with `tmdb_language = "de-DE"` — in `shows.certification` (index
  schema v7). Its own cached request (`/movie/{id}/release_dates`,
  `/tv/{id}/content_ratings`), so the details payloads already cached stay
  hits. On the web player, a title rated FSK 12 or younger is on the Kids
  shelf without being marked; one rated 16 or 18 cannot be marked and an old
  mark no longer counts; an unrated title is marked by hand as before. The
  film page and the series header show the rating. The web keeps reading v6
  indexes, so a channel whose uploader is not upgraded yet still plays — its
  titles just read as unrated. Measured on this machine's index: 509 of 525
  titles rated in 68 s. **Upgrade `mediagram` on the uploading machine and run
  `mediagram metadata` there once** so the channel's snapshots carry ratings.

- The Android app follows the same age-rating rules. Each catalog row now
  carries its title's rating (`SetSummary.fsk`, attached by poster key, so an
  episode carries its show's). The Kids tab holds films and shows rated FSK 12
  or younger, then unrated titles marked by hand, under the web's own
  headings. In the player, the Kids button reads `For kids · FSK 6` or
  `FSK 16 · not for kids` and cannot be pressed for a rated title. The title
  page and the series header show the rating.

- A film has a page on the web player. Its card opens it — poster, year,
  runtime, the TMDB user score, its genres, the description and a Play (or
  Resume) button — instead of starting playback at once; the Continue shelf
  still resumes directly. Every genre, on a film's page and on a series
  header, is a link to a shelf of the films and series tagged with it
  (`#/genre/<name>`). No new fetching: genres and score were already recorded
  by `mediagram metadata` and carried in every index; `/api/sets` rows now
  include each title's `genres`. The Android app's title page still shows
  genres as plain text — porting the links and the shelf is the next step
  (plan `260923-1551-film-page-and-genre-shelves`).

- The web player follows the channel's index, as the Android app does. Its
  server installs the newest pinned snapshot at startup and again the moment
  the uploader pins a new one — pushed by Telegram, not polled — and tells
  open pages over server-sent events, so a new upload appears on a page
  nobody touched. Before, it read this machine's `library.db` only, and
  uploads from another machine reached it only after a hand-run
  `mediagram rescan`. The newest snapshot is chosen by core's rule, shared
  through `web/test/fixtures/pick-index/`. A package, when configured, is
  still the catalog; this machine's index is only the offline fallback. The
  tab-return refresh added earlier today is replaced by the event stream.
- New titles from the channel get their covers without anyone running
  anything: after each snapshot, and once at startup, the web player runs
  `mediagram posters --index <snapshot>`, which reads the snapshot's titles
  and writes their art where the player looks, then tells open pages again.
  Descriptions already travel in the snapshot. Measured: 20 missing covers
  fetched in about a second, every film on the shelf then had one.

**Fixed**

- The web player resumes from the newest position, not the one the tab
  loaded with. A tab read its profile's positions once, when the profile was
  chosen, so after watching further on the phone or in another browser the
  open tab resumed at the old place. Opening a title now reads the positions
  again first (at most 1.5 s, then it goes with what it has), and returning to
  the tab refreshes the shelves if one changed. Newest wins per title, so a
  position this tab has just saved is not undone by the server's older copy.
- The core read `shows.certification` unconditionally, so on a v6 index
  (what the channel holds until the uploading machine is upgraded) every
  title description on the phone failed to load. The column is now read only
  where it exists, as the web does. A v6 metadata package is also accepted
  again: v7 had narrowed `SUPPORTED_SCHEMA` to `[7]`.
- Starting a film no longer trips Telegram's flood limit. The browser's range
  requests, the audio-track probe and readahead each turned every cache miss
  into its own `upload.getFile` stream, and a dozen in the same moment drew
  `FLOOD_WAIT` of 1–2 s that stalled every reader, the viewer's included. Part
  downloads now pass one process-wide gate of four (`web/src/telegram/download-gate.ts`);
  the rest queue for milliseconds instead.
- The Android player's Watchlist, Kids and Add to list sit below the status
  bar's band. Flush to the top of a full-screen film they shared the strip
  the system keeps for its own gestures, and taps there often went to the
  system; they are now inset by the bars' height even while the bars are
  hidden, so they do not move when the picture goes full screen.

**Added**

- Watchlist, Kids and Collections travel between devices, web and Android
  alike. The sync record now carries each viewer's watchlist and collections
  and the shared kids shelf, every row with its time, and a removal travels
  as a tombstone so a merge cannot bring back what was taken off; a record
  from before this, which carries none of them, changes nothing. The web's
  state schema is v6. Measured: a title put on the watchlist from the
  tablet's player was on the web player's watchlist half a minute later.
- The Android app has the web player's kept shelves: Continue, Watchlist,
  Collections and Kids, as tabs set apart after Home, Movies, Series and
  Tutorials. Collections can be made, renamed and deleted, and the player
  carries the web's three toggles — Watchlist, Kids and Add to list (a
  checklist of lists with a New list field, where the web asks for a
  number). The start page's rows now read "Latest films", as the web's do.
- The Android start page opens on Continue and Next up, by the web player's
  rules and with its "See all" targets, and every card and episode row shows
  how far it got or a tick once finished. Next up is held to the web's own
  fixture cases, and a course level now interleaves its lessons and folders
  by number as the web does, which Next up's order depends on. Measured
  across devices: a position recorded on the tablet was in the web player's
  state moments later, and the web's positions were on the tablet's
  Continue row.
- The Android player remembers where each viewer got to and starts from
  there, by the web player's rules — the same resume thresholds and finish
  line, held to the web's own fixture cases. It saves every ten seconds, on
  pause and on leaving, marks a title finished at the end, and syncs as the
  film is left. On the tablet: 37 s into Justice League, left, reopened —
  playback picked up at 0:43.
- The Android app keeps and syncs watch state with the web player. It asks
  "Who's watching?" with the same profiles, syncs on start, every five
  minutes while open, when a film is left and when the app goes to the
  background, and within seconds of another device writing — its own writes
  do not wake it. Start over now also removes this device's watch state.
  Measured on the tablet: its first round took in the web's viewer, 8
  positions and 31 finished titles, and pinned its own document once.

**Added (groundwork)**

- The Android core can sync that watch state through the library's channel,
  the way the web player does: it reads every device's pinned `#mlib-state`
  document, merges them with its own, takes in what is newer and sends or
  edits its own document only when something changed. A first document whose
  pin is refused is taken back and the round fails, as on the web, so an
  unpinned copy is never left for the next round to multiply. What it writes
  parses back byte for byte through the parser held to the web's.
- The Android core keeps watch state: profiles, positions, finished titles,
  watchlist, kids and collections, in its own `state.db` beside — never
  inside — the catalog a refresh replaces. The record and the merge are
  ports of the web player's, held to it by the web's own fixtures in both
  merge orders; import only ever corrects, never deletes what a merge did
  not mention. Nothing calls it yet: the channel sync and the app's shelves
  come next.

**Fixed**

- Settings on Android, after review. Changing the cache size while a film
  buffered could corrupt the evictor's bookkeeping or spin for ever: the
  change now takes the cache's own lock. Signing out or changing library and
  then backing out of Settings before it finished left the app signed out
  with its shelves still up, or on the old library; the library screen now
  acts on both. Settings re-reads its rows on every visit, so a new sign-in
  no longer shows the previous account. The push listener follows the core
  and restarts with every read, so a replaced core's connection closes and a
  new library is listened to at once. An offline phone is no longer told its
  application id was refused, and the check is described for what it proves:
  the id and the connection — the hash is checked at the next sign-in.

**Added**

- Settings on the Android app, after System in the menu. The Telegram block
  says who is signed in, which library, which datacentre and whether Telegram
  is answering, and offers three changes: another library (installed before
  it is remembered, then the shelves read it), a new application id and hash
  (kept only once Telegram answers through it — otherwise the one in use
  stays), and signing out, which ends the login at Telegram too and keeps
  the application identity and TMDB key. The Cache block shows what is held
  against a size now chosen from 512 MB to 8 GB (2 GB by default); choosing a
  smaller one frees the difference at once.

**Changed**

- Every mediagram client names itself in the account's session list:
  `mediagram uploader · <host>`, `mediagram web · <host>` and
  `mediagram Android · <maker model>`, each with the project's version. They
  showed as "CachyOS Linux 64-bit" and "Android 64-bit" before, and could not
  be told apart — from each other, or from a stale session worth signing
  out. A client takes its new name the next time it connects. The Android
  core's constructor gains the device name, since only Kotlin can read it.

**Added**

- The Android core can say which account is signed in (`account`: name and
  username, never the number), which datacentre it lives on (`dcId`, from the
  stored key, no network), and sign out properly (`signOut`): `auth.logOut`
  at Telegram first — deleting the key file alone left the login valid and
  listed in the account's sessions — then the connection and the key, which
  go even when Telegram cannot be reached. For the Settings screen to come.

**Fixed**

- Update library on the Android app now shows the posters it fetches. Each
  card looks its poster up when the shelves are built, and the fetch runs
  after the read that builds them — so every poster it downloaded sat on disk
  behind the title's initials until the next reload or restart, and Update
  looked as if it had fetched nothing. The shelves are now built again from
  the catalog on this device once a fetch lays down new artwork: no second
  read of the channel, and Update stays available throughout. The same holds
  for the quiet fetch new media brings.
- `rescan` dates a set it rebuilds from the channel by when its upload began
  — the earliest of its parts' messages — instead of by when the scan ran.
  Every set one scan found was stamped with the same second, so "Latest films"
  on a player reading the local index became a tie across all of them, and a
  title uploaded hours earlier won it: the web showed Jackass Forever first
  while the phone, reading the uploader's own index, rightly showed Jeepers
  Creepers. A set the index already had keeps its date, as a rescan has always
  left existing rows alone — so rows a past scan already mis-dated stay as
  they are until corrected.
- The Android core's update listener can no longer go deaf for the life of the
  app. It took the connection's one update receiver before asking Telegram for
  the update state, so a failed or cancelled first call — the phone offline as
  the app came forward, or the call cut off — dropped the receiver, and nothing
  reconnects to bring another. And grammers asks for that state only once,
  ignoring a failure, which left the stream silent with no error and unable to
  recover after a dropped connection. The core now asks for the state itself,
  stores it, and only then takes the receiver; a failure is an ordinary error
  to retry. Measured on the channel after three cancelled calls: one `State`
  event, five seconds after another device wrote, and none for its own write.
- The web player no longer leaves stray watch-state documents in the channel
  when Telegram refuses to pin one. Pins are flood-limited hard (a wait of
  over ten minutes was measured), and an unpinned document is invisible —
  devices find each other's through the pin list — so each later round sent
  another beside it. A first send whose pin is refused now deletes the
  document and fails the round, and the next round starts over. Sync rounds
  also run one at a time now: a pushed update and the timer could otherwise
  overlap on a device's first send and each send a document.

**Docs**

- `system-architecture.md` §8 describes the Android app's watch state — where
  it lives, how it syncs and when, "Who's watching?", and the deliberate
  differences from the web (no profile rename or delete, sync on by default,
  Add to list as a checklist); "What it does not have yet" no longer lists
  resume, watched marks or lists. §7 notes the state documents are written by
  Android too. The earlier parity and watch-state plans' overlapping phases
  are marked superseded.
- `system-architecture.md` now describes push updates on both players (§7,
  §8): what counts as a change, that an update is only a hint beside the
  ordinary round, and that the Android app listens only while its catalog is
  on screen — a deliberate difference from the always-on web server. §5 says
  how `rescan` dates what it finds; §11 adds the limits the design leans on:
  pins are flood-limited hard, updates need a subscribing request, and
  nothing is replayed after a disconnect.

**Added**

- Push updates. Telegram tells every signed-in session about a change in the
  library channel within milliseconds (measured, both client libraries), and
  the players now listen. The web player runs its ordinary watch-state sync
  about five seconds after another device writes, instead of at the next
  five-minute timer; a new index is noted in the log only, because its catalog
  comes from the published package. The Android core exposes the same events
  as `next_library_event`, and the Android app acts on the index half: while
  the catalog is on screen, a new index from another device refreshes it and
  then fetches the new titles' artwork and descriptions — what Update library
  does, without its result dialog, since nobody asked. A push that lands while
  a fetch is already running is picked up by the next one. Nothing listens
  while the app is in the background. Another device's watch state is heard but not acted on yet —
  the app has no watch-state sync to run.
  Updates are hints, never data: the timer stays, a missed update costs what it
  did before, and whoever starts listening runs one round first, because
  catching up after a disconnect replays nothing. Which updates count is one
  rule, pinned by fixtures both the web and the core read
  (`web/test/fixtures/channel-updates/`).

## 2026-09-22

**Fixed**

- The Android app no longer loses a film after a long pause. It keeps each
  part's Telegram document handle for the whole of a set, and that handle
  carries a file reference Telegram expires; once refused, every read of the
  set failed as a network error until another title was opened. A read
  refused with `FILE_REFERENCE_EXPIRED` (or `_INVALID`) now drops the held
  handle, resolves the part again and retries once. The web player resolves
  on every fetch and was never affected.
- Merging watch state from several devices now names a viewer the same way
  whichever order their documents arrive in. One viewer typed as "André" on
  one machine and " andré " on another kept whichever spelling happened to
  be read first, so two devices could disagree about what to call the same
  person. The spelling now follows the device id, as a tie between two rows
  for one title already did.

**Added**

- A series of more than one season opens on a wall of its seasons, each with
  its own TMDB poster, and a season opens a page of its episodes; a show of
  one season still goes straight to them. Web player and Android app alike.
  Season artwork is keyed `tmdb-tv-<id>-s<n>` (the package's poster-key rule
  now allows that one extra part) and comes from the `/tv/{id}` payload
  already cached, so `mediagram posters`, the package export and the phone's
  own fetch gain it with no extra TMDB request. A season with no artwork of
  its own shows the show's.

- Each row on the start page names how much is behind it — "Latest films ·
  124" — on the web player and the Android app alike. A row shows six; the
  figure is the whole shelf "See all" opens, so Continue counts every started
  title, as its own shelf does, including the ones moved to Next up.

**Fixed**

- The web player no longer drops a healthy title to the lowest quality. Its
  watch on the buffer took the fill rate between consecutive samples, smoothed
  over about a second, and judged a link "behind" with twenty or more seconds
  still buffered. But browsers refill in bursts: Chrome playing a file
  directly holds twenty-odd seconds and lets it sag for several before topping
  it up, and each sag read as a link delivering nothing. Six seconds of that
  converted the title, at the source bitrate times that near-zero rate — the
  600 kbit/s floor. A recording of five real minutes came within two seconds
  of doing exactly that on a file served from local disk.

  The rate is now measured across a twenty-second window, and nothing is
  called behind until fewer than ten seconds are buffered; a buffer under five
  and clearly losing is still acted on at once. Replayed against that
  recording and four harsher synthetic refill patterns, the old rule misfired
  on every synthetic one and the new one on none. A link that really is slow
  is still converted, at a bitrate taken from what it carried across the
  window rather than from its worst second.

**Shipped**

- A title held on disk in full now says "cached", instead of reporting how far
  ahead it has read. The browser buffers the same minute or so whether the
  bytes come off a local disk or across the network, so the readout described
  both identically — "0:52 ahead, filling 3.1x" — which read as the cache not
  working on precisely the titles where it had. The seek track is painted
  whole for the same reason: nothing past the browser's own buffer is waiting
  on anything, so drawing a buffer edge in the middle of it described a limit
  that was not there.

  It is asked as the player opens a title, over a new `/api/sets/{id}/held`,
  rather than read from the catalog the page fetched once at load — which goes
  on saying "streaming" about an episode that finished caching since. Behind
  it, `HeldSets.check` looks at the one set now rather than answering from the
  last scan; one set is one directory, so asking fresh is cheap.

  Two things it deliberately does not claim. A conversion of a held title
  still reports its buffer, because it is waiting on an encoder and the buffer
  is the honest answer. And a request that fails leaves the readout as it was:
  not knowing is the old behaviour, not an error worth a sentence on screen.

- The web player and its server negotiate the video codec. Every HEVC title —
  229 of them, all Matroska — was re-encoded to H.264 for every browser,
  because `playable.js` could only name codecs that play in *all* of them.
  The page now asks its own browser once whether it decodes HEVC, counting it
  only when both `canPlayType` and `MediaSource.isTypeSupported` accept
  `hvc1`, and sends `?vcodecs=hevc` with each conversion. For that browser the
  picture is copied rather than encoded: tagged `hvc1`, in fMP4 segments,
  which is the only form hls.js plays HEVC from. Only the soundtrack is
  converted, and the note says "Repackaging" rather than "Converting".
  Verified in Firefox 156 against a cached HEVC episode: 1280×720, playing,
  and thirty seconds of it repackaged in a third of a second. Browsers that
  do not decode HEVC get H.264 exactly as before, and every other session
  keeps MPEG-TS. The server takes only codec names on `NEGOTIABLE`.

  Negotiated means repackaged, never handed over directly: the index cannot
  say whether an HEVC mp4 is tagged `hev1`, which Safari and Chrome refuse,
  and repackaging retags it. And only SDR at 1080p or below is negotiated —
  170 of the 229 — because the probe asks about 8-bit Main at level 4; the
  HDR10, Dolby Vision and 2160p titles are still converted to H.264.

  A WebAssembly HEVC decoder was considered and rejected: it cannot feed MSE,
  decodes on the CPU alone, and would replace an encode that already scores
  SSIM 0.988 against its source. The saving is in not encoding at all.

- A start page on the phone, and the app opens on it. Three rows — latest
  movies, latest series, latest courses — six plates each, with **See all**
  through to the whole shelf. Home is the first entry in the masthead, as it
  is in the web player's.

  Arrival time had to reach Kotlin first. The `sets` table has carried
  `created_at` all along and `list_playable` already ordered by it, but the
  column stopped at the SQL: neither `PlayableSet` nor `SetSummary` held it.
  It is now `addedAt` on both sides of the UniFFI boundary, named as the web
  player names it, because two surfaces over one library should not need a
  translation table for the same fact.

  A collection is dated by its newest episode rather than its first, so a
  series still being uploaded keeps its place and one finished two years ago
  does not hold the top of the row for having been started recently.

  **Continue and Next up are not there.** The web player's start page has
  five rows and this has three: the phone keeps no watch state at all, so
  those two have nothing to read. They are absent rather than empty, because
  a row that is always empty teaches a viewer to ignore the place it sits in.
  They arrive with the local store, which is
  [`plans/260922-0124-android-web-parity/`](../plans/260922-0124-android-web-parity/phase-04-somewhere-to-remember.md).


- The web player says when a title is cached. An episode held in full on the
  player's disk still read "ready · 0:52 ahead, filling 3.1×" with a thin
  buffered band, exactly like one crossing the network, because both came
  from the browser's own minute-long read-ahead. The player now asks
  `/api/sets/:id/held` as it opens a title and, for a held file played
  directly, reads "ready · cached" and paints the whole track. It asks rather
  than trusting the catalog's `offline` flag, which the page fetched once at
  load and which went stale for anything cached since; the route checks that
  one set's chunk directory on the spot rather than serving the 30 s scan.
  Conversions keep their real buffer, since the encoder is still the limit.
  Android's player is owed the same (surface parity).

- The Android catalogue has a design. It had none: `darkColorScheme()` was
  called with no arguments, so every colour in the app was Material 3's
  baseline violet, and every word was Roboto. It now carries the web player's
  identity, in the terms Android states things in.

  The ground is the catalogue's own ink rather than a neutral charcoal, and
  the palette is the web player's own, inverted for a surface held in the room
  the film is about to play in. One imprint red, lifted from `#8c3b2e` until it
  cleared 4.5:1 on both grounds, marks the shelf in view and nothing else.
  Fraunces and Newsreader ship in the app, converted from the same variable
  files the browser loads, so both surfaces set the catalogue in one voice;
  the optical-size axis is declared per face because Android has no
  `font-optical-sizing`.

  The shelves are a wall rather than side-scrolling rails. A rail hides how
  much a shelf holds and puts whatever it shows first ahead of the rest, which
  is how a storefront ranks stock, and this library is finite and already
  owned. Plates replace cards: square corners, no elevation, a hairline, and a
  poster's own 2:3; a title with no artwork gets a plate with its initials
  rather than a grey slab.

  A review of the built screen found the app's platform theme was still
  `Theme.Material.Light.NoActionBar`, on both the application and the activity.
  That is why a dark app opened on a white flash and then sat under a pale grey
  navigation bar: Compose owns the frames, but the platform owns the window
  before the first one and the default look of the system bars. It now has a
  dark theme whose window background is the catalogue's ground, and the
  activity calls `enableEdgeToEdge` with both bar styles pinned dark, for the
  reason the theme is pinned dark.

  One shelf is on screen at a time, chosen from a masthead of three. That was
  not the plan and the build found it: the film shelf alone is three hundred
  plates deep, so with the shelves stacked the courses sat fifty screens down
  with nothing to say they were there. The web player has given each shelf its
  own route from the beginning.

**Fixed**

- The phone could sit on a library older than the channel actually holds, and
  sometimes refused to refresh at all. It read the snapshot the channel had
  *pinned*, but pinning is a separate operation from publishing: a publish
  whose unpin failed, a second machine publishing to the same channel, or a
  snapshot pinned by hand all leave the pin somewhere other than the newest
  index — and two pinned snapshots gave up outright with "there is no way to
  tell which one is current". The newest now wins, decided by the `pushed_at`
  the caption carries, because that travels with the snapshot and a pin does
  not. The pins are still read, and a text search for the index marker finds
  the snapshots no pin names; a channel holding no index at all still says so.

- Course documents were invisible on the phone. The Android catalog mapped
  three of the caption spec's four kinds and returned `null` for anything
  else, so every handout and workbook was dropped between the index and the
  shelves — and a course folder holding no video at all disappeared with its
  documents. A document now rides in the course tree beside the lessons it
  was uploaded with, as it does in the web player, shown greyed and not
  openable with the reason said under its own name: nothing on the phone can
  display a handout, and a tap that could only fail is worse than a row that
  explains itself.

  The same mapping no longer drops a kind it does not recognise either. It
  shelves it with the films, which is the rule `library.js` states and the
  reason it gives — a viewer who notices something in the wrong place can act
  on it, whereas a title that silently vanishes looks like a failed upload.
  Present since the Android catalog was first built.

**Shipped**

- A start page, at `#/home`, and the player now opens on it. Five rows, each
  six cards with a link to the shelf behind them: Continue, Next up, and the
  latest films, series and courses. It replaces the old landing rule, which
  picked Continue when anything was half-watched and otherwise the first
  non-empty shelf — both of them lists of everything, sorted by title.

  **Next up** is the row with rules in it. One card per show or course
  underway: the episode in progress if there is one, otherwise the first
  unwatched episode after the one finished most recently, with shows ordered
  by when they were last watched. An episode seen out of order is skipped
  rather than offered again, and a show with nothing left leaves the row.
  Continue does not repeat what Next up is showing, so one title is one card.

  **Latest** counts arrival, not release: a show ranks by its newest episode,
  so a series still being uploaded keeps its place. Two payloads grew to make
  that possible — a catalog row now carries `addedAt`, and `/state` serves
  `watched` as `[{setId, finishedAt}]` rather than bare ids, because
  finishing an episode clears its position and the completion's date is then
  the only record that the show was touched at all. A state file written by
  an older player still loads.

## 2026-09-21

**Fixed**

- The System entry was missing from the masthead for a viewer reaching the
  player over Tailscale. The entry appears only where `/api/status` answers,
  and that route asked `isLocalAddress`, which excludes 100.64/10 — the range
  Tailscale hands its peers. One predicate was answering two questions: what
  the link can carry, which decides direct play against a conversion, and
  whose device is on the other end, which decides whether the panel may be
  seen. The second is now `isOwnNetwork`, which is the local networks plus
  the tailnet, and only the status route asks it. Playback over a tailnet is
  unchanged and still converts: the phone is the household's, the uplink is
  still an uplink. Present since the panel was added on 2026-09-19.

## 2026-09-20

**Fixed**

- A watch position was lost every time a tab was closed mid-title. The write
  that exists to survive the page goes by `navigator.sendBeacon`, which can
  only `POST`, and the route took `PUT` — so it answered 405, and because
  `sendBeacon` reports success on queueing, the ordinary `PUT` written as its
  fallback never ran either. Nothing was logged and nothing looked wrong.
  Present since watch state was added on 2026-09-18.

**Shipped**

- One scrub bar and one clock, whichever way a title plays. A conversion is
  encoded as it plays, so the video element's own bar covered only what ffmpeg
  had written and its clock counted from wherever the encode began — two
  timelines beside each other, disagreeing by more the longer a title ran. The
  native timeline and its time displays are now hidden while converting, and
  this player's own bar is the only one; play, volume, fullscreen and the
  subtitle menu stay native, which they were always right for. A title that
  plays directly is untouched: its native bar has the real duration and is
  fully seekable.

- The next episode now actually starts when the countdown ends — nothing in
  the page had ever called `play()`, so "starting in 8…" opened the next
  title and left it paused. It waits for a minute buffered before it begins,
  because a viewer whose episode ended a minute ago would rather the next one
  arrive whole than arrive at once and stop ten seconds in. Pressing **Play
  now** or **Play next** starts as soon as the browser can instead: somebody
  is looking at the screen. Opening a title from a shelf still waits for you,
  as it always has.

- A tick on titles watched to the end — on episode and lesson rows, search
  hits, and the corner of a plate. It needed recording rather than deriving:
  finishing a title *clears* its position, which is right for the Continue
  shelf and left "watched it all" and "never opened it" identical everywhere
  else. State schema v4, scoped to a profile — the opposite call to `kids`,
  because having watched something is a fact about the viewer.
- Nothing is backfilled. The tick only ever claims what it actually saw.

**Fixed**

- The next episode no longer starts before the current one has finished. The
  panel appeared thirty seconds from the end carrying a ten second countdown,
  so every episode was cut off with twenty seconds still to play. It now
  appears as a heads-up reading "when this ends", and the countdown begins at
  the end — two decisions that had been one call.
- **Play next** in the player's rail, which survives cancelling the countdown.
  Saying "not automatically" should not have meant "not at all".

- The player said **buffering** for a title's whole running time even when
  every byte was on local disk and the conversion had finished. The readout
  inferred it from `readyState`, treating anything below `HAVE_ENOUGH_DATA` as
  waiting on data — but the level below it, `HAVE_FUTURE_DATA`, means "I can
  play forward", and a title fed by hls.js sits there by design, because
  hls.js caps its buffer on purpose. Buffering is now taken from the element
  saying it is waiting (`waiting`, `stalled`) rather than from a readiness
  level, which is what the word means.

**Shipped**

- A **Kids** shelf, marked by hand from the player. The mark is the one thing
  in the player's database with no `profile_id`: "this is a child's film" is a
  fact about the title, not about who is watching, so it holds across
  profiles and outlives the profile that made it. State schema v3.
- Marked titles play as a run, so a child handed a tablet does not have to
  come back to the shelf between one film and the next.
- The masthead counts beside Watchlist and Kids now update as a title is
  marked rather than at the next navigation. The watchlist had this all
  along; adding a third counter made it worth fixing.

**Considered and rejected:** finding children's titles by genre. On this
library `Animation` + `Familie` returns ten films, of which four are *Akira*,
*Appleseed Alpha*, *Batman Ninja* and *Batman: The Long Halloween*. Animation
is a technique, not an audience. Age ratings would be the principled answer
and are not fetched from TMDB at all, which makes them uploader work rather
than player work.

**Fixed**

- `push-index` now reads the channel back to see whether an unpin took
  effect, instead of believing the call. `messages.updatePinnedMessage`
  returns the updates it produced and grammers maps them through
  `.map(drop)`, so a request the server accepted and acted on not at all was
  indistinguishable from one that worked. A push reported success, left its
  predecessor pinned, and — because `Ok` erased the id from `library.db` — no
  later push could find it. Only `rescan`, which reads the pins off the
  channel, could. An id that cannot be proved unpinned now stays on the stale
  list, and the push tries a second time before giving up on it.
- An id is dropped only on the errors that mean the message is gone
  (`MESSAGE_ID_INVALID`, `MESSAGE_NOT_MODIFIED`), not on the whole 400 class.
  That class also carries `PEER_ID_INVALID`, `CHANNEL_INVALID` and
  `CHAT_WRITE_FORBIDDEN`, which say nothing about the message; reading one of
  those as "gone" stranded a pin the same way.

## 2026-09-19

**Shipped**

- A collection is a run you can play. **Play all** starts at the top, every
  row plays into the rest of the list, and a title that ends hands over to the
  next one with the same countdown a series uses — the up-next machinery was
  already general, and only needed a different answer to "what follows this".
  The run is a snapshot taken when playback starts, so removing a title
  halfway through does not change the run.
- Collection rows carry artwork, a runtime and the offline badge. A run of
  unrelated films is told apart by the picture, where a numbered lesson is
  told apart by its number.

- **Add titles** on a collection: a search picker at the head of the list,
  because until now the only way to file anything was to open it in the
  player and answer a numbered `prompt` — which meant starting playback of a
  film in order to put it in a list, and left the collection page, the one
  place a viewer looks, with no way in at all. Clicking a title already on
  the list takes it off again, so the same row is the way in and the way out.

- A green **offline** badge on any title held on the player's disk in full —
  every chunk of every part present, so it plays with Telegram unreachable.
  Worked out by counting chunk files against what the index implies each set
  needs, which is a few milliseconds for four hundred sets. Counting proves
  presence rather than integrity; a truncated chunk would pass it, and
  `ChunkCache.get` already removes one on read, so the cost of being wrong is
  a refetch rather than a failed play.

- A poster grid for Movies and Series, toggled from the shelf header and
  remembered per device. The same cards in both shapes — `.thumb` already
  carried a poster's 2/3 and already loaded one, so a plate is that thumb
  given a whole column instead of 4.75rem of one. The list stays the default.
  Tutorials keeps the list: a course has no TMDB id and therefore no artwork,
  and a hundred and seventy lessons are a list anyway.

- The web player says what it is doing. A `#/status` panel reports the
  catalogue's origin and age, whether the last refresh actually succeeded,
  what the cache holds and how much of it is being hit, which encoder is in
  use, how many conversions are running and for whom, the Telegram connection
  and the uptime — all of it facts the process already had and only ever
  printed to a terminal usually on another machine.
- The panel also reports what is crossing the wire now (derived by the page
  from two readings, because an average since startup is not the number
  anyone watching a stall wants), failed upstream reads, what the conversions
  are holding on disk — which has no budget and no eviction beyond the idle
  reaper — and resident memory.
- `/api/status` answers a viewer on this network and 404s anyone else. A 403
  would confirm there is something there, and this API has no authentication
  of its own. The only link to the panel is in the colophon, rendered only
  after the route answers a `HEAD`, so a remote viewer never learns it exists.
- **System** in the masthead, shown only to a viewer `/api/status` will
  answer. A menu entry leading to a page that 404s would advertise the page
  is there, which is the thing the 404 exists to avoid.
- The colophon says what the library adds up to — counts by kind, total
  runtime, total bytes, where the catalogue came from and how old it is —
  instead of "N playable sets".
- A technical line under a title: resolution, HDR, container, codecs, size,
  part count and average bitrate. The bitrate is what makes the
  "needs transcode" badge legible, and had never been shown.
- The shelf badge carries the reason a title has to be converted, as a
  tooltip. The player already said it; the shelf did not.
- The player HUD reports the buffer's fill rate and any dropped frames.
  `buffer-health.js` was already measuring the rate against the wall clock and
  the readout was discarding it — and depth alone cannot tell a satisfied
  player from a starving one, which is the whole reason that measurement
  exists.
- A course is no longer only video. PDFs found while walking a course folder
  are uploaded as documents: a new caption kind (`doc`), numbered inside its
  chapter the way a lesson is, so a handout named `03 Signal.pdf` lands on
  the row beside `03 Signal.mp4` without anything having to pair the two, and
  a `Ressourcen/` folder — which the walk never even visited before, having
  no video in it — becomes an ordinary folder whose rows open the file.
- Chapter numbers are still computed from the folders holding video and only
  those. A document-only folder that joined the numbering would shift every
  chapter after it, and chapter plus lesson *is* a lesson's identity, so a
  re-run of `add-course` would have matched nothing and uploaded the whole
  course a second time. Documents take numbers continuing after the last
  video chapter; a player places a folder by its path, not by that number.
- The dry-run table marks each row `L` or `D` and counts the two apart, as
  does the closing summary — a course whose videos all went up and whose
  handouts all failed is a different situation from the reverse.
- The player serves a PDF as `application/pdf` and shows it as a row that
  opens rather than plays. Documents are kept out of the playback order, so
  reaching the end of a lesson never advances into a workbook, and out of the
  film shelf, where the rule that saves an unrecognised kind would have put
  them.

**Not changed**

- No schema migration: `kind` and `container` are TEXT and every video-only
  column was already nullable, so `SCHEMA_VERSION` stays 6 and no index in
  the wild needs migrating. The caption marker stays `v=4` for the same
  reason — the field set did not change — which keeps old builds reading
  every movie, episode and lesson caption as before.

