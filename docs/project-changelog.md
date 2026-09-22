# Project changelog

Dated entries summarizing what shipped, grouped by day. Commit hashes refer
to `main`. Full phase-by-phase detail lives in
`plans/260914-1954-telegram-linux-uploader-mlib-spec-v2/plan.md`'s
"Implementation log" sections.

## 2026-09-23

**Fixed**

- Update library on the Android app now shows the posters it fetches. Each
  card looks its poster up when the shelves are built, and the fetch runs
  after the read that builds them — so every poster it downloaded sat on disk
  behind the title's initials until the next reload or restart, and Update
  looked as if it had fetched nothing. The shelves are now built again from
  the catalog on this device once a fetch lays down new artwork: no second
  read of the channel, and Update stays available throughout. The same holds
  for the quiet fetch new media brings.
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

## 2026-09-18

**Shipped**

- `prepare` says what it is doing while it does it: a count while it probes,
  then one line per file carrying its position, size, speed and eta, read
  from `ffmpeg -progress`. A season of remuxes was an hour of a cursor not
  moving. ffmpeg's own complaint goes into the error now too, rather than
  only its exit status.
- `mediagram status` names a set before describing it, prints the id the
  advice below it refers to, and makes its numbers agree with their nouns.
  "waiting 1 set(s)" invited the question it was meant to answer.
- `mediagram status`: the set currently uploading and how far it has got, each
  show against what the provider says exists, and whatever is left unfinished.
  Inferred from the index rather than from any record of a running job, because
  a bulk add is a sequence of `add` calls and nothing durable says how many are
  meant to follow — which is what a real queue would record later.
- `add --delete-source` deletes the file once every part of it is in the
  channel, and `add-show --delete-source` frees a season's disk as it goes
  rather than all at the end. The index is what says a set is complete; this
  trusts the upload rather than reading the parts back, which is what `verify`
  is for and what to run first when the local copy is the only other one.
- `prepare --delete-source` removes each original once its replacement has been
  written and passed every check. Refused without `--out`, where the source is
  the destination and deleting it would delete the result.
- `prepare --mp4` copies audio that a browser already plays instead of
  re-encoding it. Turning AAC into AAC cost a generation of quality to change
  nothing.

- A series says how much of it is here: `8 of 10 episodes`, `1 of 2 seasons`.
  The index can count what it holds and only the provider knows what exists,
  so schema v6 records both totals. Holding all of a show says nothing about
  totals — "16 of 16" is a fact about arithmetic, not about the show.
- The years are the show's run, from the provider's first and last air dates,
  falling back to the years of the episodes held. One season of a show that
  ran seven should still say when the show ran. A show still running has no
  end date and is not given one.
- Schema v5 adds a `shows` table, and a series page leads with what the
  provider says about the show: synopsis, genres, rating, network, status.
  A show was not an entity in this index — it is what you get by grouping sets
  on a provider id — so there was nowhere a synopsis belonging to the whole of
  it could live. It is keyed the way a poster key is, because TMDB numbers
  films and series independently.
- `mediagram metadata` fills that table from payloads `add` already cached, so
  a library recorded before it existed needs no API key and no network to
  catch up. `add` records the show it just identified, from the same payload
  it used to identify it.
- The player asks for a description on a separate route rather than carrying
  one on every catalog row: it is a page's worth of text that a shelf never
  shows, and 171 rows would each have carried a copy.
- A v4 index still works. The player accepts either schema and reads the table
  only if it is there, so nothing has to be upgraded in step.

- A show says what it is. Opening a series now leads with its own facts —
  episodes, seasons, runtime, size, years, resolution, HDR, codecs and the
  audio and subtitle languages — all derived from the episodes already in the
  index, so a show describes itself as soon as one episode of it exists.
  Nothing is fetched and nothing new is stored.
- A field nobody recorded is left out rather than printed empty, and a show
  that is not all one resolution shows the span. Half a show in 720p is worth
  knowing; picking either value to display would hide it.
- Subtitle languages come from the file's own tracks, like audio already did.
  Reading them from the `assets` table instead reported no subtitles for a
  show whose files carry them — that table answers a different question, which
  track the player can serve separately.
- `PosterStore` re-reads its directory when it changes. Listing once was right
  for a package, whose artwork arrives all at once; it was wrong as soon as
  the same store served a local library, where `mediagram posters` adds
  artwork to a directory a player is already running against. A poster fetched
  at noon was invisible until a restart.

- `mediagram add-show <dir> --tmdb <id>`: a series folder, uploaded. Only
  `add-course` walked a directory before, and it marks everything a course, so
  a series meant a shell loop. Two such loops were written by hand and both
  were wrong — one filed a show under another show's id, the other converted a
  show nobody asked for. The command prints what it would file where before it
  moves a byte, which is what made both mistakes visible.
- `add-show` refuses when two files claim one episode rather than picking by
  sort order. A folder holding an original and its converted copy is the
  ordinary way that happens.
- `add-show` says which episodes the player would convert on every play, and
  separates what `prepare` can fix — the wrapper, the audio — from what it
  cannot. Telling someone to convert an HEVC show would cost them hours and
  change nothing.
- `prepare --mp4`: converts to a browser-playable mp4 alongside dropping
  tracks, copying the picture untouched. `--out <dir>` writes a parallel tree
  instead of replacing the originals. `--mp4` rewrites a file that already
  fits its part limit, because playability is not a size question.
- Direct-play policy lives in `media::direct_play`, and
  `tests/shared_direct_play_policy.rs` fails when it and the player's copy
  disagree — the same guard `PLAYABLE_SQL` already has.
- Quality labels come from the frame, not its height. A 2.39:1 film is stored
  1920x804, and 804 read as 720p though every pixel across is 1080p. Width
  alone would have broken 4:3 the same way, so the label takes the greater of
  the height and the height a 16:9 frame that wide would have.

- `mediagram posters`: cover art for the films and series in the index,
  written to `<data dir>/posters/`. Artwork had only ever shipped inside the
  published package, so a player reading the local index showed initials on
  every card and had no way not to. Posters now live beside the index
  whichever index that is — the catalog a package unpacked, or the library on
  this machine — which is one expression in the player and no second code
  path. Re-running skips what is already held.
- The download loop moved from `Staging` into `export::posters`, which is
  where it belonged: staging borrowed it, and a local directory needs the same
  limits, the same key check and the same refusal to fail a run over an
  unreachable image. `Staging::fetch_posters` now only adds the archive paths
  the manifest names.
- The player page was redesigned as a printed catalogue: warm uncoated stock,
  Fraunces over Newsreader, hairline rules in place of cards, and a masthead
  where a 220px sidebar used to spend a fifth of the width naming three
  shelves. Artwork is now set as a plate at the 2:3 a poster is actually drawn
  at — the old tile was 16:10 and cropped the bottom third off every one of
  them — with the title and its figures on one baseline beside it. The player
  dialog keeps its own dark palette: a poster reads best against a page and a
  picture reads best against black.
- Both faces are self-hosted from `web/public/font/`. The page is routinely
  opened on a link with no way out to the internet, and a catalogue that falls
  back to Times because the house wifi is down looks broken. `web/src/routes.ts`
  serves `.woff2` as `font/woff2`.
- A show holding a single episode said "1 episodes · 1 season". Counts now go
  through `countOf`, which spells a number while it is small enough to read as
  a word and agrees with the noun it counts: "one episode · one season".

- The player is fullscreen. The picture fills the window and everything else
  floats over it on two rails that fade once the pointer rests, return on
  movement, and never hide while playback is paused or while focus is inside
  them. Notes stopped opening by themselves — over a picture, a page of text
  is in the way until it is asked for — and are a button now.
- Opening a title preloads it and stops there. `preload="auto"` with no
  `play()` anywhere, and a readout that says what the buffer holds and whether
  the browser thinks it is enough, because a still first frame otherwise looks
  identical to nothing happening.
- The bottom rail projects when the title will finish, from the catalog's
  runtime rather than `video.duration`: a conversion's duration is only as far
  as it has encoded, so a film would claim to end four minutes from now and
  keep moving. Divided by the playback rate, and blank when no runtime is
  known rather than guessing.
- An audio track chooser, for the 26 sets in this library holding more than
  one. The list is probed off the file, not read from `sets.alang`: that column
  holds *distinct* codes with untagged streams dropped, so its positions are
  not the ordinals `-map 0:a:N` selects by, and a file running
  `[und, en, en-commentary, de]` would have sent "German" to the untagged
  stream. The chosen ordinal joins the seek and the bitrate in identifying a
  transcode session, so two viewers watching one film in two languages do not
  share one encode.
- A title playing directly converts when a non-default track is picked, and
  says so. Chrome and Firefox do not implement `HTMLMediaElement.audioTracks`;
  there is no other way to honour the choice.

- A course is browsed one level at a time. Geldhochschule is 162 lessons
  across four levels, and the whole tree on one page meant scrolling past a
  hundred things to reach the folder you wanted. The course now opens on its
  three top-level folders, each saying how much is behind it, and every level
  is its own URL — linkable, and the back button walks out the way you came.
  A breadcrumb replaces the back button, which only ever went one place.
- A folder sits where its number puts it. "3. Signal" holds lessons numbered
  1 to 21 with 14 missing, and the folder filling that gap is called
  "14. Exkurs TWS": it belongs between 13 and 15, not after 21. `levelEntries`
  in `library.js` interleaves lessons and folders by their leading number, and
  is tested there rather than inside a render loop.
- A show is unchanged. Its seasons are one flat level with episodes under
  them, so there is nothing to walk into and a screen per season would be a
  click that bought nothing.

- A lesson's notes are a column beside the picture, not a panel over it. The
  video gives up the width and letterboxes into what is left, so nothing is
  covered and nothing has to be dismissed to read. Open already for a lesson,
  because notes are the point of a course; behind the button for a film.
- The notes render as markdown. These summaries are written in it — `###`
  sections, bullet lists whose items lead with a bold run-in, ordered lists
  nested four spaces under them — and were being shown with the markers
  visible. `markdown.js` parses to a tree and `notes-view.js` builds nodes
  from it. Not one string of HTML between them: the text comes from a file
  beside a video, and `dom.js` already says where that road ends. A link
  whose scheme is not `http`, `https`, `mailto` or a local path keeps its
  words and loses its link, because `javascript:` in an `href` is the same
  hole as a script tag.
- Checked against all 162 summaries in the library: every one parses, and no
  block marker survives into rendered text.

- The player writes something for the first time. Watch positions, the
  watchlist and hand-built collections live in its own database at
  `MEDIAGRAM_STATE_DB`, defaulting under `~/.local/share` — a data directory,
  not a cache. It could not go in the index: that one is opened read-only,
  belongs to the uploader, and is replaced wholesale when a package refresh
  lands. Keyed by `set_id`, a ULID that survives every catalog refresh.
- Resume across devices, which needed nothing beyond the above. The player is
  a server, so a phone and a laptop pointed at it read the same rows; putting
  a film down on one and picking it up on the other is not a sync problem.
- A `Continue` shelf, the landing when it has anything in it, with a rule
  across the foot of each plate. Not every position is a place to go back to:
  the first half minute was looking rather than watching, and the last 5% or
  last minute is the credits — both start again from the top.
- `Up next` over the end of a title, counting down and advancing unless
  cancelled. What follows is derived from the tree `library.js` already
  builds, so it crosses a season or a folder boundary without being told
  there was one. Cancelling is remembered for that title.
- The next title's first 8 MB are fetched and dropped while the current one
  plays, once its buffer is comfortable. The point is the server's chunk
  cache, which the transcoder reads through as well.
- A watchlist, and collections: named lists, any title in any of them, made
  and renamed and deleted from their own shelf.
- Profiles. Positions, watchlist and lists are each a profile's own; the
  chooser is answered per device and kept in that browser. A profile is a
  convenience and not a login — anyone who can reach the port can pick any of
  them, and the chooser says so.
- The API takes writes for the first time. It still has no authentication, so
  a proxy in front remains the answer; what is checked is that a write carries
  a JSON content type and a same-origin `Origin` where the browser sends one,
  which stops a form on another page from deleting a collection.

**Fixed**

- A title was counted finished far too early, which deleted its position every
  time one was saved and kept it off the Continue shelf for good. The rule read
  "past 95% **or** within a minute of the end", which takes whichever of the
  two is more generous — so a one-minute lesson was finished before it started
  and a two-minute one a few seconds in. 155 of the 197 titles in this library
  are short enough to have been affected, three of them fatally. The tail is
  now the **smaller** of the two: the last minute of anything over twenty
  minutes, a proportionate sliver of anything below. The test describing the
  intended behaviour predated the code that contradicted it.
- The same scaling applies to the other end. Half a minute is a glance at a
  film and half of a sixty-four-second lesson, so "opened rather than watched"
  is now the first half minute or the first tenth, whichever is shorter.
- A conversion's `video.duration` is no longer mistaken for the title's
  runtime. It is the length of what has been encoded so far and grows as it
  goes, so for a set the index never measured it put the end of the film a few
  seconds ahead of the viewer for the whole film — read as finished, and the
  position deleted on every save. The rule is `trustedRuntime` in
  `resume-point.js`, which answers "nobody knows" instead, and everything
  downstream already refuses to judge without a runtime.
- The state store replayed every migration on every open. Version 1 survived
  that because every statement was `CREATE TABLE IF NOT EXISTS`; version 2
  rebuilds three tables to add `profile_id`, and a second open copied the live
  rows into a fresh table under an invented profile, dropped the original and
  renamed the copy over it. It read as working. Only groups above the recorded
  version run now, each in its own transaction.
- `hidden` did nothing to the transcode scrub bar or the audio chooser. The
  attribute is a user-agent rule of the lowest specificity there is, and both
  elements are `display: flex`, so they stayed on screen while the script that
  set `hidden` was convinced they were gone — the jump bar showed on titles
  that were playing directly and had nothing to jump. `[hidden]` is now
  enforced for the whole page.

**Unchanged on purpose**

- `inspect` still reads a file's length from the format header alone. A
  fallback to a stream's own duration, and then to frames over frame rate, was
  written and then reverted: tracing found `add` to be the only place a
  duration is ever created, and no file that reaches it benefits. MKV, MPEG-TS,
  fragmented MP4 and WebM all carry a format duration; a truncated MKV reports
  one from its header and a truncated MP4 makes ffprobe fail, which aborts the
  add rather than recording a null. The one file that lacks a format duration —
  a raw elementary stream with no container — lacks the stream duration and
  frame count the fallback would have used. A null reaching the index is far
  likelier to arrive in a caption written by another build or another
  implementation of the spec, which `rescan` trusts and never re-probes, and
  which no amount of probing here would have caught.
- `-map` still names exactly one audio stream. The reasoning in
  `transcode/args.ts` is unchanged: letting ffmpeg pick "best" means the track
  with the most channels, which on a film is routinely a commentary. What
  changed is only who names it.
- No schema change. The player opens the index read-only and must not migrate
  what the uploader owns, so `EXPECTED_SCHEMA` stays at 4 and the track list
  comes from the file instead.
- Only films and series get artwork. `titles::distinct_titles` already selects
  `kind IN (movie, ep) AND tmdb IS NOT NULL`, so a course is excluded by
  having no provider id rather than by a rule written somewhere about courses.

## 2026-09-17

**Shipped**

- `mediagram edit <set-id>`: correct a set's metadata without re-uploading
  its bytes. Every part carries the whole record, so a correction rewrites one
  caption per part; `--refresh` asks the provider again in the configured
  language, and `--dry-run` shows the change first. The channel is written
  before the index, so a run that dies between them leaves `rescan` able to
  reconcile from the side that now holds the truth.
- `tmdb_language`: TMDB answers in English unless asked, so a German library
  got "Forsaken" where the file said "Verlassen". Added ahead of the disk
  cache, since the cache keys on the query and a language behind it would
  leave two languages sharing one entry.
- TMDB v4 read tokens (`Authorization: Bearer`) alongside v3 API keys. Sent
  the wrong way a read token answers 401 with nothing to say why.
- Caption v4 carries the folders a set came from, so a course nesting one to
  four levels deep keeps its shape. Schema v3 stores it; schema v4 adds an
  assets table for subtitles and summaries.
- A Bun player backend and web UI: catalog, HTTP Range streaming over a set's
  concatenated parts, and shelves for Movies, Series and Tutorials.
- A disk cache in front of Telegram: 512 KiB chunks, a quota that evicts by
  last use, and readahead for sequential playback.
- `mediagram remove`: delete a set's messages and then its rows, in that
  order, so a run that dies between them leaves `rescan` able to reconcile.
- Conversion to HLS for what a browser will not decode. ffmpeg reads the
  player's own Range route, writes segments, and the page plays them through
  hls.js — or natively where Media Source Extensions are missing. A
  conversion has only encoded as far as it has got, so the dialog carries a
  second scrub bar covering the whole running time that restarts it where it
  lands. Blade: Trinity (mkv/HEVC/AC-3, 2h 2m) plays from cold in 1.7 s,
  6.2 s seeked ninety minutes in, at 5.1 Mbit/s against an 8 Mbit/s cap.
- The player tells a viewer on the LAN from one on the internet, and offers a
  conversion rather than the original file for anything above the uplink
  budget. `MEDIAGRAM_TRUST_PROXY` decides whether `X-Forwarded-For` may be
  believed; the last hop is read, not the first, because Cloudflare appends
  to whatever the caller sent.
- The `mlib-package-v1` reader, the half of the format that has been
  published since 2026-09-15 and never read. `MEDIAGRAM_PACKAGE_URL` and
  `MEDIAGRAM_PACKAGE_KEY` are enough to run a player with no access to the
  uploader's filesystem: it fetches the pointer, verifies and opens the
  package, and reads the index and artwork inside. Freshness comes from the
  five authenticated fields, never from `sha256`, which anyone who can
  rewrite the pointer can set to the digest of the copy the reader holds.
  Posters travel with it and appear on the shelf cards.
- The player watches its own buffer and converts down when the link cannot
  keep up, instead of stalling every few seconds forever. It measures seconds
  buffered ahead of the playhead against the wall clock, and on a sustained
  shortfall restarts at a bitrate the link was observed to carry — keeping the
  viewer's place and saying so under the player. Only ever downward, 25
  seconds between switches, and it stops when there is nothing lower left
  rather than restarting the same encode forever. `?maxrate=` on the transcode
  route carries the request, clamped between a floor and the configured cap.
  Verified over a deliberately throttled 2.4 Mbit/s link: a 4.3 Mbit/s episode
  that had been stalling continuously converted itself to 1.96 Mbit/s.
- `MEDIAGRAM_LIBRARY_DB` is no longer required when a package supplies the
  catalog. A player with no uploader filesystem to read refused to start
  without a path to a file it would never open.
- `docs/running-the-player.md`: issuing a session for a player host, the
  configuration, which profiles play directly and why the rest do not, Caddy
  with TLS and authentication, what to check when playback stalls, and what
  the player deliberately does not do.

**Shipped**

- 162 lesson summaries imported from `video_tutor`'s database into the
  library's `assets` table, matched to their sets by folder and lesson number
  with a folded-title fallback for the chapter whose files carry no number.
  All 162 matched, none left over. They are searchable immediately; they did
  not need re-uploading because an asset lives in `library.db` rather than in
  the channel — which is also why `rescan` would lose them.
- Search, at `GET /api/search?q=…` and a box in the sidebar. Matches titles,
  show and course names, chapters, folder paths and summary bodies, folding
  case and diacritics so `uberblick` finds "Überblick". Ranked by which field
  matched, with a summary hit carrying the words around it. A hundred and
  sixty-two lessons named "Definition" and "Mobile App" are not browsable,
  only searchable.

**Verified live**

The uploader's phase 5/6/7 acceptance gates, run against the real channel
with `MEDIAGRAM_PART_SIZE=10485760` so ordinary files make several parts:

- A 3-part `add` (25,537,985 bytes), then `verify --full` → 3/3 hash matches.
- One `parts.sha256` row overwritten → `verify` exits 1, names `part 1`,
  prints the expected and actual digests, and still reports the other two ok.
- `kill -9` with 3 of 12 parts done, then `resume` → 12 parts across message
  ids 49-60. A span of exactly twelve for twelve parts: the part in flight
  when the process died was adopted, not re-uploaded.
- Peak RSS during the 12-part upload: 35.6 MB against a 200 MB budget.
- A push pinned a new `library.db` whose caption said `sets: 18`, matching
  local, and unpinned the one it replaced.
- `rm library.db && rescan` → all 18 sets and 32 parts identical across every
  column the captions carry.

**Fixed**

- The id of the pinned index lives in `library.db`, so the first
  `push-index` after `rm library.db && rescan` pinned a new snapshot and left
  the previous one pinned beside it — a reader listing pins then had two
  indexes and no way to tell which was current. `rescan` now asks Telegram
  which messages are pinned and records the index snapshots among them, so
  the next push clears them. Found by the phase-6 live gate, in the one path
  that gate had never been run against.
- `SPEC_VERSION` had drifted from the caption marker, which would have put the
  wrong version in every published package. A test now pins them together, as
  another pins the player's expected schema to the uploader's, and a third
  pins the documented codec table to the code that decides.
- `mediagram edit` rewrote a set's captions but left the index saying what it
  said before, so moving a set to another shelf was undone by the next
  listing. The year went missing for the same reason: a refresh asked TMDB for
  the title and threw the release date away.
- Byte offsets went to the Telegram client as native bigints, which it
  advances between requests with big-integer arithmetic — so the first
  request succeeded and the second threw. Anything larger than one request
  size needs several, which is nearly everything.
- A run of missing cache chunks was fetched in one call. Over a cold cache
  that run is the rest of the film, so the reader held gigabytes in memory and
  yielded nothing; and a short answer was cached as it arrived, which put the
  next run's bytes where the missing ones belonged. Runs are capped and a
  short read now fails.
- Left to ffmpeg's own stream selection, a forced subtitle track with eight
  cues across two hours stalled the muxer outright: it held the video back
  waiting for the next cue and the encode stopped dead.
- Two viewers pressing play together both got past the "already running?"
  check while the session directory was being made, and the second ffmpeg was
  never tracked — surviving stop, reaping and shutdown, holding the encoder
  for good. Stopping a session also deleted the directory of one restarted in
  the same three seconds. Sessions are shared, so they are reference counted:
  the first viewer to close the dialog no longer ends the other's playback.

## 2026-09-16

**Shipped**

- `mediagram serve`: a local HTTP API a player can use. `GET /sets` lists what
  `PLAYABLE_SQL` matches, with the codecs a browser needs to decide between
  direct play and a transcode. `GET /sets/{id}/stream` serves a set's parts as
  one virtual file with Range support, seeking into the right Telegram message
  by skipping 512 KiB chunks rather than downloading from zero.
- `db::open_read_only`: the serving process never checkpoints or migrates the
  uploader's index.
- `serve_addr` in config, loopback by default. The API has no authentication
  of its own; exposing it is a later, deliberate step.

Verified live against the channel: five 2,000-byte ranges of a
7,011,563,463-byte two-part film — including one crossing the part boundary —
came back byte-identical to the local source file, as did a 400 MiB slice; a
whole one-part lesson matched its recorded part hash; seeks anywhere in the
6.5 GB file cost 0.15-0.21 s; memory moved 164 kB over 800 MiB streamed; and
`library.db` was byte-identical throughout, including after shutdown.

## 2026-09-14

**Shipped**

- Workspace scaffolding and the `mlib-spec` crate: caption struct + codec
  (marker line, minified JSON, 1,024-UTF-16-unit budget), raw byte-range
  part planning, ≤60-char part file naming grammar, the fallback filename
  parser, `set_hash`, and the `library.db` SQLite schema
  (`5a45684`).
- Telegram client construction on grammers 0.10 (`SqliteSession` +
  `SenderPool` + `Client`), interactive login (phone/code/2FA), `whoami`,
  and a hidden `smoke-upload` debug command (`cbf9610`).
- Media inspection (`ffprobe`), HDR/quality classification, MP4
  trailing-moov detection and faststart remux (`3cdc6dd`).
- TMDB metadata resolution: search, disk-cached HTTP client, interactive
  disambiguation prompt for ambiguous matches (`c034fef`).
- A `lib.rs` library target replacing `#[path]` test includes, so
  integration tests import the crate normally (`a39e1c8`).

**Review fixes** (`30d7a5b`): TMDB API key no longer leaks into `reqwest`
error URLs; unprotected libsql WAL/SHM session sidecars now inherit the
session directory's restricted permissions; a `u64` wrap fixed in the MP4
atom scanner; TMDB's server-side year filter no longer defeats the ±1-year
matching rule; `--imdb` input is normalized (`tt`-prefix, digits-only); the
retry wrapper no longer retries permanent RPC errors; a failed remux no
longer leaves partial output on disk; empty TMDB search result pages are
no longer cached; `Config`'s `Debug` impl redacts secrets instead of
deriving it.

**Phase-1 live gate — PASSED 2026-09-15 00:00** (recorded same day as the
work, timestamped after midnight): a real 3,758,096,384-byte (3.5 GiB)
file uploaded to a Premium account's private channel, sent, and deleted
successfully in 1,187 s (≈3.2 MB/s / ≈25 Mbit/s, upstream-bound), with no
`FLOOD_WAIT` and no retries observed. Confirms 3.5 GiB parts are safe on
this account and that `throttle_ms = 0` is a safe default. Also confirmed
in this session: interactive login cannot run through a `!`-prefixed
in-session shell (no TTY) and must be run in a real terminal.

Test count at end of day: 131 (unit + fixture integration + probe suites,
phases 1-4).

## 2026-09-15

**Shipped**

- Streaming part upload pipeline with resume: a hashing byte-range reader
  (`part_reader`), the `Transport` trait plus its Telegram implementation,
  adoption-based resume (scans the last `3 × part_count` channel messages
  and matches parsed captions instead of re-uploading), and the `add`
  command's full inspect → resolve → remux → plan → upload → index → push
  flow (`0ca2392`).
- Channel title resolution made case-insensitive and extended to
  supergroups, not just broadcast channels (`5757a59`).
- Index push and rescan: `push-index` (WAL checkpoint → `VACUUM INTO` →
  upload → pin → unpin previous), and `rescan` (pages the whole channel
  history, folds `#mlib v=` captions into `sets`/`parts` additively)
  (`b1373e5`).
- `verify` command (metadata check by default, `--full` re-download +
  streamed SHA-256 hash per part) plus this documentation set (this
  change).

**Review fixes**

- Upload/resume (`be70497`): temp-file deletion now matches by suffix
  instead of an assumption that could delete the wrong file; source file
  size is re-validated before resuming a set (catches a source that
  changed since `add`); a retry-induced duplicate send is now caught by
  the adoption scan instead of silently producing two messages for the
  same part.
- Index push/rescan (`3231740`): a previously-failed unpin is now retried
  on the next push (`stale_index_message_id`); concurrent pushes use a
  per-process snapshot temp file name so they cannot clobber each other;
  rescan summary counts (`sets_complete`/`sets_incomplete`) are no longer
  double-counted across batches; the unparsed-caption counter is now
  populated; push failures carry operation context.
- `3bd76f9`: rescan's part-row helpers moved into their own module to
  respect the 200-line-per-file limit.
- `723a423`: config probe tests that mutate `MEDIAGRAM_*` process
  environment variables now run serialized, removing a source of
  parallel-test flakiness.

**Test counts:** 161 after phase 5's review fixes, 187 after phase 6, 198
passing (+1 `#[ignore]`d live test) after phase 7's `verify` command, 240
after phase 7's review and test round.

## 2026-09-15 (phase 7 review round)

**Fixed**

- Reachable panic on remote data: `grammers`'s `Document::id()` unwraps an
  optional field that `Media::from_raw` never checks, so a message carrying
  stripped or expired document media could panic a run. `telegram/document.rs`
  is now the only accessor, used by `verify`, `rescan` and `upload/transport`.
- `verify --full` no longer discards a long run: a download failure becomes
  a per-part verdict and each set prints as it completes, instead of every
  result being withheld until all sets succeeded.
- `verify` honours `parts.chat_id` and names both chats when a part was
  recorded elsewhere, instead of reporting "message not found", which reads
  as data loss and invites a re-upload.
- A truncated download reports "download ended after N of M bytes" rather
  than a hash mismatch, which read as corruption on Telegram.
- A failing part's stale `verified_at` is cleared, so the index snapshot
  pushed to the channel cannot advertise an old success beside a failure.
- `verify --all` skips sets that are still uploading rather than failing
  while `resume` still has work; byte sums use `checked_add`.
- An empty `tmdb_key = ""` in `config.toml` now loads as absent, so `add`
  gives the "set a key or pass --manual" message instead of a late TMDB
  authentication failure.

**Added**

- `verify --since <unix>`: skips parts already verified at or after the
  timestamp, making an interrupted `--full` sweep resumable. It was a
  written phase-7 requirement; the roadmap had recorded it as cut, citing a
  quote that appears in no plan file.
- 36 edge-case probes for the verify decision layer plus 6 new unit tests.

**Docs**

- Caption line 2 is documented as UTF-8, matching the plan's locked
  decision and `caption_codec::to_text`; the spec previously said ASCII.
- Spec example part count corrected (`n:18` → `n:17`, the value the total
  implies) in both the document and the fixture it is copied from; index
  caption key order corrected to what `serde_json` actually emits;
  `verified_at` semantics documented as "result of the last verification".
- The grammers import boundary, the one-`run`-per-command rule, the
  200-line rule's scope, and the commit-trailer rule now describe the code
  as it is.

**Status:** phases 1-7 are code-complete. The phase-1 live smoke gate
above passed; the remaining live acceptance gates (live 3-part `add`, kill
mid-upload + `resume`, `verify --full` hash match and tamper detection,
`rescan` reproduction) need a real terminal against the live channel. They
do **not** need a TMDB key: `add --manual` takes metadata by hand. See
[`docs/development-roadmap.md`](development-roadmap.md#open-live-gates).

## 2026-09-15 (prebuilt metadata package)

**Added**

- `mediagram export-package [--publish] [--dry-run] [--out <dir>]`: copies
  the index, gathers posters from the TMDB responses already cached on disk,
  writes a manifest, packs a gzipped tar and encrypts it with AES-256-GCM.
  With `--publish` it hands the package and then its pointer to a configured
  command.
- `docs/mlib-package-v1.md`, the normative format: layout, manifest, pointer,
  cipher framing, reader algorithm and security model. Its examples are
  generated by a test, so the document cannot drift from the code.
- Config: `package_key`, `publish_cmd`, `publish_base_url`. The key is
  redacted from `Debug` like the API hash and TMDB key.

**Design points worth remembering**

- The pointer's five identifying fields are the cipher's associated data, so
  an archive replayed under an edited pointer fails its tag. The four
  download fields cannot be authenticated, because one of them is the digest
  of the ciphertext the tag protects; a reader must therefore never decide
  "I already have this" from `sha256`.
- Publishing runs argv directly, never a shell, because a shell reports the
  exit status of the last element of a pipeline and would call a failed
  upload a success.

**Fixed**

- The export wrote to `library.db`. Copying checkpointed the write-ahead log
  first, which folds pages into the main file and changes it. Only visible
  with a second connection open, which is the case the design exists for.
  The checkpoint is gone and the connection opens read-only.
- The reader's size ceiling was only checked against an estimate that
  over-counts the index and under-counts posters; it is now checked against
  the bytes actually produced.
- `poster_key_is_valid` had no caller; it now gates every key that reaches a
  path. Poster downloads have a timeout and a size cap. Packing skips
  symlinks that would otherwise pull outside files into the package.

## 2026-09-15 (tutorials, and a parser that validates)

**Added**

- Caption `v3`: a third kind `tut` for course lessons, plus `chap` (chapter
  title) and `cid` (a general collection id for sets with no provider id).
  Course, chapter and lesson reuse `show`, `s` and `e`, so nothing downstream
  needed a second vocabulary. `v2` captions stay readable and are never
  rewritten.
- `mediagram add-course <dir>`: subdirectories are chapters, videos inside
  them are lessons, leading digits are numbers and the rest is the title.
  `--dry-run` shows every inference before anything uploads. Re-running skips
  finished lessons by identity (collection id plus the two numbers), so it
  survives renaming or moving the folder, and reports unfinished ones for
  `resume` instead of uploading them twice.
- `add` gained `--course`, `--cid`, `--chapter`, `--chap` and `--lesson`. The
  tutorial path never contacts TMDB, which has no courses, so it needs no key.
- Schema version 2: `sets.chap`, with `group_key` finally carrying the
  collection id.

**Changed**

- Migrations are applied by version in one transaction instead of replaying
  every statement on each open. The old shape relied on every statement being
  `CREATE ... IF NOT EXISTS`, which cannot express adding a column.

**Fixed**

- The caption parser validated nothing, so any message in the channel could
  introduce an arbitrary `set` id, including one containing path separators,
  which `rescan` would write into the index as a primary key. `set`, part
  indices and lengths are now checked at the boundary. `sha256` deliberately
  is not: it never reaches a path, and refusing it would make rescan drop a
  part it could otherwise recover.
