# Project changelog

Dated entries summarizing what shipped, grouped by day. Commit hashes refer
to `main`. Full phase-by-phase detail lives in
`plans/260914-1954-telegram-linux-uploader-mlib-spec-v2/plan.md`'s
"Implementation log" sections.

## 0.115.0 — the web player's episode list

**Added** (web player)

- **☰ in the control card** opens the show's episodes in a panel on the right, while the picture keeps playing:
  - A `‹ Season N ›` switcher opens on the season you are watching.
  - Watched episodes are greyed with ✓, a partly watched one shows a progress line, and the playing one reads "Now playing".
  - Picking an episode plays it.
  - Courses list their lessons the same way.
  - A film, or anything played from a hand-built list, has no ☰.
- **Room is made for the panel.** At 900 px and wider, the card moves left of the panel; the top bar does so from 768 px, so My List and ✕ stay reachable.
- **Closing the panel.** ✕, Esc or a pick returns focus to ☰.
- **Keys stay inside the panel.** Arrow keys inside the panel move through the list rather than changing the volume.

## 0.114.0 — the phone and tablet player's control card

**Changed** (Android phone and tablet)

- **One control card.** The player's controls now sit in one frosted card at the bottom of the picture, in the web's three rows:
  - Row 1: position, the seek bar, then length and "ends at". The length and end time go under the bar when they would squeeze it.
  - Row 2: CC▾, speed, audio and framing on the left; picture-in-picture at the right.
  - Row 3: ↺ ⏮ −15 ▶ +15 ⏭, with ⓘ and ☰ beside them.
- **No blur behind the card.** The card is black at 78% because the video draws on its own surface, which keeps HDR and Dolby Vision working. `DESIGN.md` records this.
- **The slim top bar** keeps the title, My List, Kids, Add to list and Notes.
- **The settings sheet is gone.** CC switches subtitles on and off in one press, and ▾ opens the languages and Style…. Speed, Audio and Framing each open a short menu above their button. On a landscape phone a menu scrolls rather than cover its button. Menu rows are at least 48 dp.
- **☰ opens the episode list** from the right:
  - It offers a season switcher, and watched episodes are greyed with ✓. Partly watched ones show a progress line; the playing one reads "Now playing".
  - Picking a row plays it. The card moves left of the list while the list is open.
  - On a phone under 600 dp the list is full width.
- **Double-tap skips 15 s.**
- **TalkBack** hears CC and stats as on or off, hears menus as expanded or collapsed, and moves into a menu or the list when it opens.

## 0.113.0 — the web player's control card

**Changed** (web player)

- **One control card.** The player's controls now sit in one blurred card at the bottom of the picture, in three rows:
  - the seek bar, running time and "ends at";
  - CC▾, speed, audio and framing on the left; volume and fullscreen on the right;
  - ↺ restart, ⏮ previous, −15, play/pause, +15, ⏭ next, and ⓘ stats.
- **A slim top bar.** It keeps the title, My List, Kids, Add to, Notes and ✕.
- **Small menus.**
  - CC switches subtitles on and off in one press, bringing back the language that was showing.
  - ▾ opens the languages and Style….
  - Speed, audio and framing each open a short list above their button.
- **Framing has a visible control.** Before, it was only the `z` key, with no feedback.
- **Skip is 15 s** on the buttons and the keys.
- **Restart and Previous are buttons.** Restart keeps play/pause. Previous is disabled on the first title, and hidden with no run.
- **ⓘ opens a stats overlay:** video, audio, buffer, cache, and dropped frames when there are any. The one-line file facts and the preload readout moved into it.
- **The card hides on the usual timer.** It stays up while paused, while a menu is open or while the pointer is over it. Clicking the picture no longer keeps it up for good.
- **Blur is allowed in a second place.** The card is now the second place, after the masthead. `web/DESIGN.md` has a Player section saying so.

## 0.112.0 — skip 15 s; the player model behind the coming control card

**Changed** (Android phone, tablet and TV)

- **Skip is 15 s.** The player's skip buttons and the phone's double-tap are now 15 s. The TV D-pad still moves 10 s until the TV card lands.
- **Seeking back off the end stops the Up-next countdown.** Before, rewinding or scrubbing back after the credits kept the countdown running, and it then switched titles under a viewer watching again.

**Added** (Android, not yet drawn on screen)

- **The model the new control card draws from:**
  - an episode list grouped like the web player's season page. Watched, partly watched and now-playing rows; a last "Episodes" group for episodes without a season. There is none for a film or a hand-picked list.
  - Previous and Restart. Restart after the end stays paused.
  - Picking a title from the run.
  - The rule that keeps the controls up while a menu or the episode list is open.

## 0.111.0 — profile security fixes

**Fixed** (Rust core, web player, Android phone, tablet and TV)

- **Manage profiles closes when the app is left.** It closes, and forgets the PIN it was opened with, when the app goes to the background or the library shows again. Before, it stayed open with the PIN still held.
- **The wrong-PIN wait survives a restart.** After five wrong PINs the 60 s wait is now stored, so restarting the app no longer resets it.
- **A first profile waits for the household.**
  - The first profile, and with it the admin role, is offered only after the device has taken in a sync round for the library it follows. Before, a device opened before its first sync could make a profile that took the admin role from the household's existing admin once the two synced.
  - Until then, phone, TV and web show "Waiting for this household's profiles…" with Try again. The web API answers `not-synced` (409).
  - The mark is kept per library, so switching libraries waits for the new household.
- **A first PIN never overrides.** A first PIN (set where the person had none) never replaces a PIN set earlier on another device. Only someone who knows the current PIN, or the admin's Reset PIN, can replace it.
  - Between two first PINs, the oldest wins. A proven PIN always beats a first PIN, and between proven PINs the newest wins.
  - This closes a stale offline device setting a grown-up's PIN that then won everywhere.
  - Schema: web state v13 and core v9 (`pin_proven`). PINs stored before this count as first PINs, so a PIN changed on 0.110.0 that hasn't reached every device yet can lose once to the older value.

## 0.110.1 — quieter menu counts

**Changed** (web player, Android phone, tablet and TV)

- The counts beside the rail items and department pills are smaller and fainter, at 40% of
  the ink: web 0.625rem, phone 10sp, TV 13sp (`TvTypeScale.count`). Over a cover they drop
  to 40% of the bar's ink.

## 0.110.0 — household admin, PINs and each kid's own limit on Android

**Added** (Android phone, tablet and TV; Rust core)

- The core enforces the household's rules, the same as the web player: the admin is a
  grown-up, a kid is never admin and has no PIN, a name another profile answers to is
  refused, five wrong PINs for one profile make that profile wait 60 s, removing a
  grown-up removes that grown-up's kids. PINs are salted and hashed in the core and never
  cross into Kotlin. All four shared rule fixtures (pin-hash, profile-rules, profile-names,
  pin-wait) pass in the core as on the web.
- The picker's three start states — the first profile (created as admin, with a PIN), "Who
  runs this household?", and tiles plus "Manage profiles" — on phone, tablet and TV. Grown-
  ups enter with their PIN; kids open freely; tiles read "Kids · FSK N". A reopened picker
  still offers "Stay as I am", and Back means it.
- Manage profiles: add a grown-up or a kid (a new kid starts at **FSK 6**, 12 selectable),
  set a kid's limit, reset a PIN, remove. On TV, a PIN pad the remote walks (number keys
  type too) and the choices in dialogs — written under "Television differs".
- Each kid sees titles up to its own limit; the player's Kids control chooses Not for kids /
  From 6 / From 12 (a menu on the phone, a dialog on TV); Settings › Profile reads
  "Name · Kids · FSK N".

**Removed**

- The player's one-button Kids toggle; every surface now sets an age.

## 0.109.1 — Back from Home's lead card returns to it on the television

**Fixed** (Android TV)

- Back from a film opened from Home's Editor's choice card landed on the bar's Home pill.
  The pushed page's removal hands focus to the bar; Home's arrival then asked its list for
  the card, and the list's restorer — which never remembers a card, because the chrome's
  exit handler replaces its own — redirected the request to the cover's Watch now, which
  the arrival had just scrolled away. Until the arrival lands, the list now falls back to
  the arrival's own card; the arrival also scrolls to the item that really holds a band.
  Verified on the box; a walk per Home band guards it.

## 0.109.0 — high-bitrate films fetch several chunks at once

**Changed** (Android)

- For a film whose average bitrate is high, the player keeps up to four chunks in flight
  ahead of the reader (N = ⌈2 × bitrate / 16 Mbit/s⌉, capped at 4; anything up to ~8 Mbit/s
  stays sequential). The first chunk of a read is still fetched alone, so first frame and
  seeks are as quick as before; a chunk already on its way is never fetched twice; a
  failed fetch drops every set to one at a time for 60 s. Preloads stay sequential.
- Measured on the TV box, cold, over Wi-Fi: about 30 Mbit/s instead of 15–17. Girl on the
  Train (HDR10, 28 Mbit/s) now plays in real time; The Fall Guy (Dolby Vision, 41 Mbit/s)
  keeps up for its first ~50 s and then rebuffers. 86 titles above 32 Mbit/s still need the
  home cache server or Preload when cold.

## 0.108.1 — what the TV box walk and the final review found

**Fixed** (Android)

- Phone: the Collections tab builds only the lines in view rather than every franchise card
  at once; art tiles grow to hold their words at large font sizes (genre tiles 240 dp).
- TV: arriving on a franchise page keeps its name and hero in view; Left from Search reaches
  the department pills scrolled out of view; the season picker scrolls to and enters the
  season it shows; title pills stay inside the text column, clear of the tagline; Back from a
  franchise returns to its "Part of" link; a course's play line reads "▶ Continue lesson 3";
  the Preload and Remove pills are outlined like the rest.
- Resume wording, season labels and franchise year spans are built once for phone and TV;
  the unused season screen is gone on both, and course pages stop fetching details they
  never show.

## 0.108.0 — the web player: household admin, PINs and each kid's own limit

**Added** (web player)

- The first profile created runs the household and may set a PIN; grown-ups enter with
  their PIN, kids open freely. The admin adds and removes grown-ups and resets PINs; a
  parent manages its own kids. "Manage profiles" adds a grown-up or a kid — a new kid
  starts at **FSK 6** (the user's choice), 12 still selectable — sets a kid's limit and
  removes profiles; removing a grown-up also removes that grown-up's kids.
- Each kid sees titles up to its own limit; a Kids mark says "from 6" or "from 12", set from
  the player. Settings › Profile reads "Name · Kids · FSK N".
- PINs are salted and hashed and never leave through any route but the sync record. Five
  wrong PINs for one profile make that profile wait 60 s; only its own right PIN clears its
  count. A name another profile already answers to is refused ("A profile with that name
  already exists") — sync keys profiles by name, so a duplicate could otherwise turn the
  admin into a kid. The household-admin PIN is separate from the Settings admin token.
- A reopened picker still offers "Stay as I am", and Escape now means it; inside Manage
  profiles, Escape closes the panel first.

Claim admin on the web straight after this reaches the live player: until someone does,
anyone can.

## 0.107.0 — the core carries profile roles, PINs and kid limits (data layer)

**Added** (Rust core, for Android; the rules and screens follow)

- Core state schema v8 — the same roles the web player's v12 carries: an admin claim, a PIN,
  a kid's own limit and parent, and a Kids mark's age — on the sync record, merged and
  exchanged exactly as the web does. All 31 shared role-fixture cases pass, and 3,000
  random cases agree with the web in both orders. The migration repairs before it
  migrates, so an early pre-release file gets the `kids` column the v8 step needs.
- A same-millisecond admin claim breaks its tie in UTF-16 name order, as JavaScript
  compares, so the web and Android never name different admins.
- No change to the Kotlin API yet; Android takes the core with its next native build.

## 0.106.2 — DTS and TrueHD films play on the television

**Fixed** (Android)

- A film with DTS or TrueHD audio sat at 0:00 with no sound on the Realtek Google TV box
  (444 DTS and 18 TrueHD titles in the library). The box advertises both as HDMI
  passthrough, then stalls its compressed output on every buffer (AudioFlinger "pause
  because of UNDERRUN" with frames ready). The player's audio sink now refuses DTS and
  TrueHD passthrough, so FFmpeg decodes them to PCM; AC-3 and E-AC-3 passthrough are
  unchanged. Verified on the box: Magnolia (DTS) and Rocketman (TrueHD) play in real time
  with the HDMI output opened as PCM. A receiver behind HDMI now gets those two as PCM
  rather than bitstream.

## 0.106.1 — courses read as an index on the phone; TV cast as person cards

**Changed** (Android)

- Phone and tablet: a course page opens like the web's — the name, the extent in small
  caps ("two lessons · one document") and a rule over its lessons — rather than a film-style
  header of art and overview a course has nothing for.
- TV: Cast shows round person cards (portrait, name, character) instead of poster plates,
  the same card TV search uses for people; crew-name links and Up-to-the-tab are unchanged.

## 0.106.0 — the last editorial gaps on Android close

**Changed** (Android)

- TV Cast: each "Directed by" / "Created by" name is a stop on the remote that opens the
  person's page, as the web and phone link them.
- A TV course page is the web's index: the page heading ("two lessons · one document")
  over its lessons, without the film-style tabs a course has nothing for. Written under
  "Television differs": a "▶ Continue …" line under the heading, because a remote walks a
  long course one row at a time.
- TV search shows people as round portrait cards with name and count.
- A tablet in portrait overlaps a department hero's words onto its art, as the web does
  below 900 px; a list's own phone page opens with the web's page heading.

## 0.105.2 — Android's tests run the real watch-state repository

**Changed** (internal, Android tests)

- The four hand-written watch-state fakes, their mocks and failure wrappers are gone. Player,
  catalogue, profile, phone and TV tests now run the real `WatchStateRepository` over the
  contract-checked fake core through one fixture, `testing.WatchStateFixture`; failures are
  injected at the core provider. Only `WatchSyncTest`'s recording double remains — it tests
  WatchSync's own calls. A finish's effect (position cleared, watched stamp newer, a second
  finish moves it) is now asserted on real state rather than on a call log.

## 0.105.1 — the last browse gaps on Android close

**Changed** (Android)

- TV search shows films and shows as posters and collections as cards, as the web does;
  a film poster opens its page rather than playing; Down from the field lands on the first
  result.
- TV Movies department ends on the "All N films →" pill; a list's own TV page opens with
  the web's page heading.
- Phone: a department hero overlaps its words onto the art as the web does at narrow
  widths; franchise and department grids keep the page's side gutter.

**Removed**

- The television's old list-of-lists screen, which nothing could reach once Collections
  showed lists as cards.

## 0.105.0 — profiles carry a role, a PIN and a kid's own age limit (web data layer)

**Added** (web player state; the screens and rules follow)

- State schema v12: a profile can be the household admin, hold a PIN, and — as a kid —
  carry its own age limit and a parent; a Kids mark can say "from 6". All of it syncs
  through the state channel as new keys (`admin`, `pin`, `kidsAge`, `parent`, a list
  row's `age`) and merges deterministically: the earliest grown-up claim is admin, a kid
  is never admin and never has a PIN, and at an exact tie a Kids mark from 6 beats one
  without it (an older build echoes a mark without its age at the same stamp).
- Every new time is bounded like the shipped ones (2^53 − 1) and own writes are clamped.
  Builds without this ignore the new keys and echo nothing that undoes them.
- Shared fixtures for the core to follow: `profile-roles-merge.json` (20 cases) and
  `profile-roles-record-parse.json` (11 cases).

## 0.104.0 — the television's title and series pages read like the web's

**Changed** (Android TV)

- A film or show opens on the web's spread: backdrop, title, facts, overview and tagline,
  with Play (or "Resume from 12:30"), "+ My List" / "✓ My List" and ⋯ pills. ⋯ holds
  editor's choice, for shows as well as films.
- Overview, Details and a show's About are the web's fact sheets — Audio, air dates,
  "1 of 73 episodes" — built once in the catalogue for phone and TV alike.
- Similar is always a tab, with the web's sentence when nothing is similar. Episodes is a
  season picker over that season's episode list, not a wall of season posters.
- Up from a panel lands on the selected tab rather than the nearest one.
- Written under "Television differs": a 380 dp minimum spread; tabs switch on OK and the
  tab row rises to the top so a sheet with nothing to focus shows whole; the season picker
  is a row of pills; ⋯ opens its choices in the same row.

## 0.103.0 — the television's browse pages read like the web's

**Changed** (Android TV)

- One focusable art tile, name and spelled count over the picture: the Genres page (16:9,
  four across) and the Movies department's genre row (16:8).
- Collections: franchises and lists as 4:3 cards, three to a line; a list is pictured by
  its first title with art; "＋ New list" is a pill.
- A franchise page opens with a hero — the name, "two films · 1999–2003" and TMDB's
  introduction — then "In release order". A person page has a round portrait, "N in your
  library", and separate Films and Series sections.
- Genres, a genre, Latest and a person open with the web's page heading. Search's
  headings read "Movies" and "Lessons" like its chips, counts up to twenty are spelled,
  and a franchise counts films.
- Written under "Television differs": a fresh franchise visit lands on the introduction
  rather than the first film (it runs to seven lines on a television, and landing below it
  would scroll the name away), and "No lists yet." shows under Your lists, as on the phone.

## 0.102.1 — Android's watch state says who is watching; no position clear without a finish

**Changed** (internal, Android)

- The watch-state repository answers who is watching (`chosenProfile`), so the catalogue,
  the player's marks and Settings › Profile stop each looking the chosen id up for
  themselves. `clearProgress` is gone from it: finishing a title, which always re-stamps
  "watched", is now the only way a position is dropped. A position cleared without that
  stamp is what let another device bring it back.
- Tests can make a core call fail or hold it open (`FakeCoreProvider.beforeCore`), ready
  for moving the remaining test doubles onto the real repository.

## 0.102.0 — Android browse pages read like the web's

**Changed** (Android phone and tablet)

- One art tile, the web's, with the name set over the picture: the Genres page (16:9),
  the Movies department's genre row (16:8), Collections and Search (4:3 cards that wrap).
  A list's card uses its first title's art, and in Search a franchise reads "N films".
- Latest heads its parts Movies / Series / Tutorials and lists courses rather than
  tiling them; the Tutorials department's "All courses" is a list too.
- The Movies department ends with an "All N films →" pill; a franchise page carries its
  overview inside the hero and heads its films "In release order".
- Counts up to twenty are spelled out, as on the web; a person page reads "N in your
  library"; Latest, Genres, a genre and a person open with the web's page heading.

## 0.101.0 — Android title pages read like the web's

**Added / changed** (Android phone and tablet)

- A tablet in landscape draws a title page the way the web does above 900 px: art
  full-bleed from 28% across, the words and buttons bottom-left, the tagline quote
  bottom-right. Phones keep the stacked layout.
- Film page: the facts line ends with the first three genres, and Details has an
  "Audio languages" row. Series page: About has an "Audio" row, the facts line spells
  seasons ("two seasons"), and the season picker reads "Season 1 · one episode".
- Series pages show the provider's air dates, and "8 of 16 episodes" when the library
  holds fewer than the provider lists. The core's title info now carries `first_air`,
  `last_air`, `total_seasons` and `total_episodes`, read from the same `shows` row the
  web reads, so both surfaces print the same thing.
- "Directed by" and "Created by" names open that person's page, as on the web.

## 0.100.1 — no synced row can be stamped into the far future

**Fixed**

- Core and web: a synced position, My List entry, collection, Kids mark or editor's
  choice whose time is above 2^53 − 1 is now dropped when parsed, as watched, stats and
  preference rows already were. Before, a peer's far-future stamp beat every later real
  edit of that row on every device it reached: the position, a removal, a rename, the
  editor's choice pick. One helper per engine now checks every row's time. Android takes
  the fix with its next native core build.

## 0.100.0 — "0 min", "Continue", "My List": the wording the user chose, everywhere

**Changed** (the user's decisions, 2026-10-04 — web, phone/tablet and TV alike)

- Stats: watch time below one minute reads "0 min" instead of "under a minute".
  While nothing has been counted yet, a line "Counting since <date>" sits under the
  three totals, dated from the first play stats saw. Finishes recorded before stats
  existed don't count. Contract §6 says so.
- The rail's resume row reads "Continue" instead of "Continue watching", which no
  longer fit beside a two-digit count at tablet width. Android's narrow-screen overflow
  menu says the same. The home's "Continue Watching" band and the departments'
  "Continue watching" rows keep their names, as on the web.
- The player's list button reads "My List" / "On My List" instead of "Watchlist" / "On
  the list", like every other place the list is named. Android defines the text once,
  for the phone and TV players.

**Fixed**

- Web: a department pill clicked earlier no longer looks lit on a rail page such as
  My List or Stats. The pill kept keyboard focus across Back and Forward, and the next
  key press drew a focus ring around it. A nav link that doesn't name the current page
  now lets go of focus.

## 0.99.12 — a corrupt "watched" time from another device can no longer win or stop sync

**Fixed**

- Core and web: a synced "watched" or "un-watched" time above 2^53 − 1 is dropped
  when parsed, as stats times already were. Before, a peer's far-future mark beat
  this device's real un-mark, and the same import deleted a saved position on another
  title. A mark at the very top of the integer range made the next un-mark store a
  real number, after which every export failed and the device stopped publishing its
  state. Marking and un-marking now step at most to 2^53 − 1, and the core's export
  reads a value an older build already saturated instead of failing on it. Android
  takes the fix with its next native core build.

## 0.99.11 — a department's Continue row keeps its own titles

**Fixed**

- Web and Android: a department page's Continue row (Series, Tutorials, Anime,
  Documentaries) is now built from that department's own titles first and only
  then cut to twelve. Both surfaces used to narrow lists already cut to the
  titles touched most recently anywhere. So on the web a course with a next
  lesson dropped off "Continue your courses" once six series had been watched
  since, and on both surfaces a lesson or episode in progress dropped off once
  a dozen films had been started since. Same function on both surfaces:
  `departmentUnderway` and `departmentUnderwayOf`.

## 0.99.10 — an episode range reads 3-4 on the web, as on Android

**Fixed**

- Web: a file that holds two episodes is labelled `S2E3-4`, not `S2E[3,4]`.
  The index stores the uploader's JSON (`7` or `[3,4]`), which the web printed
  as stored while Android parsed it. The series page's play button had the same
  problem (`S3 E[15,16]`) and now names the first episode, as Android does.
  Both surfaces now read a stored value that is neither a number nor a pair as
  unnumbered.

## 0.99.9 — the Android home checked against the web; Back on the profile chooser stays

**Fixed**

- Android (phone, tablet, TV): Back on a reopened "Who's watching?" chooser now
  means "Stay as I am", as Escape does on the web. The chooser stands in for the
  library, so Back used to close the app. On a first run, with nobody to stay
  as, Back still leaves.
- Android: the My List page and its empty state now read "My List" and
  "Nothing on your list.", like the web. They still said "Watchlist" from
  before the web renamed the shelf.

**Docs**

- The Android home and chrome were walked on the tablet against the live web
  player, landscape and portrait. The home's section order, the rail, the
  departments bar and all six departments match. The remaining differences
  are recorded as deliberate or as owned by later plans, in
  `plans/260927-2050-android-home-web-parity/reports/home-web-parity-tablet-report.md`.
- `DESIGN.md` now describes the chrome and home that shipped in 0.71: the
  rail, the departments bar and the compact header, and the home's section
  order. It no longer describes the serif tab row they replaced.

## 0.99.8 — a cache-server address without a port finds the server

**Fixed**

- Android: a home cache server address entered as just a host
  (`http://192.168.0.240`) is now tried on `mediagram_cache`'s own port,
  7788, instead of HTTP's 80. On the home server port 80 belongs to another
  web server, which answered 404, so Settings showed "Not found" while the
  cache ran. The port is added when probing, so an address already saved
  without one heals on its own. An address with its own port is unchanged.

## 0.99.7 — the player's own controls hover on the shared token

**Changed**

- The player stage's ghost buttons, transport buttons and featured actions
  hover on `--hover` too; they had kept their own `120ms ease`, the last
  hover literal on the web.
- `SwatchCard` drops a `shape` parameter no caller passed and draws at
  `Radius.card`; its comment no longer calls 12dp an exception.
- Five stylesheet citations that 0.99.6 missed or had swapped point at
  their rules again, and `PageHead` no longer claims a web header the
  settings page stopped drawing.

## 0.99.6 — the hover and card-corner rules become tokens

**Changed**

- Web hover transitions take `--hover` (160ms on `--ease`) instead of a
  literal repeated in eight stylesheets; the unused `--dim` is gone.
- Android gains `Radius.card` (12dp, the web's `--radius-card`), used by the
  phone's and the television's feature cards and Appearance's swatch cards,
  so the three cannot drift apart again; `DESIGN.md` lists it.
- Comments citing a stylesheet line (`home.css:180` and the like) point at
  the rules they named before 0.99.3 moved them; the nav icons in
  `index.html` share one scan pin; the redundant `.settings-head` rule is
  gone.

## 0.99.5 — Android feature cards match the web's card corner

**Fixed**

- The home feature cards on the phone and the television round at 12dp, the
  web's `--radius-card` since 0.99.3; they had stayed at the old 14.

## 0.99.4 — the player's remaining design choices pinned for the slop scan

**Changed**

- Every hit the kill-ai-slop scanner still reported in `web/` was a recorded
  choice; each now carries a `deslop-ignore <id>: reason` comment (scrims over
  artwork, link underlines, round dots and portraits, the masthead blur, the
  type system), so a fresh scan reports nothing and a new tic stands out.
- `titleSpread` loses its `eyebrow` option and `.spread-kicker`, which no page
  ever passed or drew.

## 0.99.3 — template tics removed from the player

**Changed**

- The third home feature reads "Best-rated in the library" instead of "Staff
  pick": there is no staff, and the pick is a daily turn through the ten
  best-rated films. Web, phone and television.
- Department and franchise heroes drop the "Only in your library" / "The
  collection" kicker; the department's name is the heading. Web settings drop
  "Make it yours"; the Android Appearance page says "How the player looks on
  this device" instead, in the voice of the other settings pages.
- Web hover feedback is a 160ms colour, border or background change. Theme
  swatches, accent dots, the solid pill and the profile tiles no longer lift or
  grow; artwork still lifts and arrows still nudge toward where they lead.
- Web image cards (home features, genre tiles, collection cards) share one
  `--radius-card` of 12px instead of 12, 14 and 16px.

## 0.99.2 — viewing stats merged with the fake core's sync and preferences fixes

Merge of `feat/viewing-stats` (0.95.3 on that branch through 0.99.1, below) with
`main`'s 0.95.3 and 0.95.4 (test-only fake-core fixes, further below). No
behaviour of its own; one number for the combined tree.

## 0.99.1 — final-review fixes for viewing stats

**Fixed**

- The television's progress rule keeps the track's full height at any fraction.
- A day-based achievement is dated at the day's local noon, so a clock change
  cannot move it to the neighbouring day.
- A stats stamp above 2^53 - 1 is dropped on parse, and a stamp a write pushed
  past that range is not exported.
- The month total starts at the month's first day, so a day numbered 00 is not
  counted.
- A day row stops at a whole day on write, as every reader keeps it.
- A stats answer without its history or achievements shows the failure line.

## 0.99.0 — achievements on Android

**Added**

- Achievements on the phone's and the television's Stats page, derived in the
  core from the same rows and held to the web by the shared fixtures; the dot
  on the Stats rail item lights for one this device has not shown (the seen set
  is per device, never synced).

## 0.98.0 — achievements on the web

**Added**

- An Achievements section on the Stats page (earned with dates, then the next
  few with progress), `GET /api/profiles/{p}/stats` answers them with the
  stats, and a dot on the Stats rail link for one not yet seen. Kids profiles
  earn only finishing and exploring achievements.

## 0.97.0 — the Stats page on Android, phone and television

**Added**

- A Stats rail item between Genres and Settings and its page: this week, this
  month and all-time minutes, the last 30 days as bars, and the history.

## 0.96.1 — the core records, syncs and summarizes viewing stats

**Added**

- The core records watch time on its own position writes, syncs every device's
  rows and summarizes them over uniffi (`stats`).

**Changed**

- `set_progress` takes the local day as a new last argument.

## 0.96.0 — the web's Stats page

**Added**

- Viewing stats: both state engines count watch time between a title's
  position writes (state schema v11 on the web, v7 on the core) and sync it as
  two optional per-profile keys, `titleStats` and `dayStats`, of the
  `#mlib-state` documents. `SYNC_FORMAT` is unchanged; older readers drop the
  keys. Totals are sums of per-device rows, so a merge cannot double-count.
- A Stats rail item and page on the web: week, month and all-time minutes, the
  last 30 days, history (Started, Finished, Watched again). The pure rules and
  shared fixtures arrived first, on this branch's own 0.95.3, which is not
  the 0.95.3 released from main.
## 0.95.4 — the Android core fake fails a sync for a library it never stored

**Fixed**

- `FakeCore.syncState` answered every library with a clean, empty round,
  where the real core answers a library this device never stored with a
  failed one — read from the local list of stored libraries, before any
  connection, and as an outcome rather than a throw. The fake now answers
  the same, and `CoreContract` pins it against both cores. No test relied
  on the old answer: every one that syncs supplies its own `syncState`.
- `WatchSyncTest` now covers a round that pulled rows but failed to send:
  the core keeps and counts those imports "so the caller can reload", and
  the test fails if `WatchSync` ever skips the reload on a failed round.
  The existing pulled-rows case now also reports the send a real round
  makes after importing. Test code only; no app change.

## 0.95.3 — the Android core fake keeps preferences the way the real core does

**Fixed**

- `FakeCore` answered `preferences()` with nothing and `setPreference()`
  with `false`, so `PlayerPreferencesTest` carried its own in-memory core
  instead — one no contract checked, which accepted a choice for a profile
  nobody created where the real core's foreign key refuses it. The fake now
  keeps preferences by the real rules (trimmed and capped scope, name and
  value; a blank scope or name refused; a blank value forgets; a value for
  an unknown profile refused; a profile's removal takes its preferences),
  `CoreContract` pins them against both the fake and the real core on the
  tablet, and the test's own core is gone. Test code only; no app change.

## 0.95.2 — second channel release, for the playback check

No code changes: published so the TV box has an update waiting while a
title plays, to confirm it neither downloads nor installs until playback
stops.

## 0.95.1 — first Android release published to the library channel

No code changes: the first build sent with `scripts/release-android.sh`, so
the TV box's 0.95.0 release build has something newer to update itself to.

## 0.95.0 — television release builds keep themselves current from the library channel

**Added**

- Release builds of the Android app on televisions (Android TV, Google TV)
  check the channel's pinned `#mlib-app` release when the app comes to the
  front (at most hourly, never while something plays), download it verified
  by size and sha256 into `cacheDir/updates`, and install it over themselves
  through PackageInstaller when the app goes to the background; Android 12+
  shows no prompt. The permission is granted once by adb at TV setup
  (`adb shell appops set com.mediagram.android REQUEST_INSTALL_PACKAGES allow`).
  Phones and tablets never update themselves (Google Play Protect blocks
  app-driven updates from our signing key) and keep getting new versions by
  adb; debug and benchmark builds never update themselves either.
- Settings › System shows an "Updates" row wherever the updater runs.
- `mediagram publish-app` sends the APK pinned with a `#mlib-app v=1` caption
  (version, code, bytes, sha256, published_at) and unpins the previous
  release; index pins are untouched.
- `scripts/release-android.sh` builds the minified arm64 and armv7 release
  APK, checks it carries the release-key certificate, and runs `publish-app`.

**Changed**

- Android `versionCode` is derived from `versionName`
  (major x 1,000,000 + minor x 1,000 + patch), so the two cannot disagree.

## 0.92.2 — Android rows show what arrives in front of them

**Fixed**

- A row still at its start now shows its new start when a catalog refresh
  puts something in front: the tablet's Series "New episodes" row kept
  opening on Bones while Seinfeld and Boston Legal, newer, sat scrolled off
  to its left. A keyed `LazyRow` holds on to the item it showed first;
  `rememberRowState` (`ui-common`) moves the row back to its start instead,
  as the web player's rebuilt row does, and leaves a row the viewer has
  scrolled into where it is. Applied to the phone's New episodes, Popular,
  category, Latest/Recently added, film, documentary and Continue rows, and
  to the television's department and Continue rows.

## 0.92.0 — lesson subtitles move into bundles; subtitles backfill from folders or the channel

**Added**

- `mediagram subtitles move-inline` gives each lesson whose subtitles sat
  inline in the index a `#mlib-subs` bundle (track numbers unchanged) and
  removes the rows only after the bundle, read back from the channel, matches
  what was sent. Three mismatches stop the run with an error; one publish at
  the end.
- `mediagram subtitles backfill` now sends:
  - from folders, matching files to uploaded sets by size (a name-and-duration
    match is sent only when named with `--accept-fallback`);
  - with `--channel`, reading the German and English text tracks from the
    uploaded copy through a loopback server on the same Telegram session —
    MP4 first, `--mkv` for the rest, `--limit N` per run — and remembering
    sets that have none (`subs-none:<set>`);
  - `--redo <set>` replaces a bundle; the old message is deleted only after
    the index naming the new one is published (with `--no-push` its id is
    listed instead).
- Runs hold the upload lock, pace sends 2 s apart, publish every 100 sets and
  at the end, finish the set in hand on Ctrl-C (a second Ctrl-C quits), and
  stop with an error after five failures in a row; `--dry-run` writes nothing.
  ffmpeg and ffprobe run in their own process group, so Ctrl-C never cuts an
  extraction short.
- A channel read that ends early is a failure — never a short bundle and
  never a `subs-none` mark: ffmpeg exits 0 on a cut HTTP read, so channel
  extraction runs with `-xerror` and every wanted track must come out. An
  oversized track (over 4 MiB) is left out rather than failed, since every
  run would find it oversized again.

## 0.91.0 — the uploader attaches German and English subtitles when a set completes

**Added**

- At set completion (after the last part is up, before `--delete-source`), the
  uploader reads a film's, episode's or lesson's German and English text
  subtitles and sends them as one `#mlib-subs` bundle per set, recorded in
  `subtitle_files`/`subtitle_tracks` with a publish owed. Tracks come from the
  file the person named, read in one ffmpeg pass into a private temp folder:
  embedded `subrip`, `ass`/`ssa`, `mov_text`, `webvtt` and `text` streams, and
  sidecars named `<video>[ ._-]<language and flag words>.vtt|.srt`
  (`de|deu|ger|german|deutsch`, `en|eng|english|englisch`; `forced`, `sdh`,
  `cc`, `hi`; any other word means another video's file; `.vtt` beats `.srt`).
  Non-UTF-8 SRT sidecars and embedded tracks are read as Windows-1252 — ffmpeg
  otherwise drops every line with an umlaut and still exits 0.
- Forced is the flag, a `forced`/`erzwungen` title, or an embedded track with
  no forced flag or forced title (and not SDH) of at most 120 cues an hour
  (measured on 78 tracks: signs-only German runs 1–86, full tracks 442 and up)
  or a quarter of its language's densest track. SDH is the flag or a title
  naming SDH, CC, hearing or hörgeschädigt. Picture subtitles are skipped and
  counted. A failure costs only the bundle, never the upload.
- `subtitles::attach` takes a file or a URL, so the backfill can read an
  uploaded copy through a loopback server; URL reads time out after 60 s of
  silence and are never retried per track; sidecars are found for files only.
- Planning records the file the person named (`orig:<set>`), because
  completion deletes the remux and forgets the source. The key outlives
  completion until extraction is done, and `--delete-source` keeps the file
  while it is recorded, so a second process can never delete it mid-read.
- `remove` deletes a set's bundle message along with its parts.

**Changed**

- A lesson's `.vtt` no longer lands in `assets` at planning; it travels in the
  bundle like every other subtitle. Summaries are unchanged. Readers older
  than 0.86.0 (web) / 0.87.0 (Android) do not see bundled subtitles, so
  install this uploader only once every player is on those.

## 0.90.0 — Android: a CC button, the remote's captions key, and a Profile section in Settings

**Added**

- A CC button in the phone/tablet transport row and the TV tool row turns
  regular subtitles on and off, and the remote's captions key does the same
  from any player state, showing the controls briefly. The button appears only
  for a title with a regular subtitle track; the key does nothing (and
  remembers nothing) on a title without one. Forced lines keep following their
  own rule.
- Settings has a Profile section showing who is watching and that profile's
  default subtitle language (Off, German, English) — the same preference the
  web player's Profile panel writes, and since 0.89.0 synced between devices.
  Disabled until a profile is chosen.

**Changed**

- The player settings' size, backing and sync rows show for any title with a
  subtitle track that can show, forced-only titles included; the language rows
  still need a regular track.
- The phone transport row wraps instead of squeezing its last buttons to
  nothing on a narrow portrait screen.

## 0.89.0 — subtitle language and cue style follow the profile to every device

**Added**

- Subtitle language and cue style (size, backing, offset) now follow the
  profile to every device through the existing watch-state sync: an optional
  `preferences` list on each profile in the `#mlib-state` document, merged
  newest-write-wins per scope and name with the device-id tie-break, on both
  the web player and the Rust core. Audio, speed and framing stay per device.
  No format bump: older builds drop the key and keep syncing positions. A
  local write is stamped no earlier than the row it replaces, and the web
  player now runs a debounced sync round after a preference write.
- A preference row whose time is fractional or past 2^53 is dropped on read:
  beyond that a local write's `stored + 1` stamp stops advancing and the
  peer's row would win every tie.

## 0.88.4 — the Settings subtitle choice looks like the rest of the page

**Fixed**

- Settings › Profile › Subtitles was a bare browser `<select>` beside the
  page's pill controls. It now shares the season picker's pill style
  (`title-page.css`).

**Changed**

- `PREVIEW_SUBTITLES` takes several set ids, comma-separated, and
  `forced:<id>` lists only the forced track, so the whole subtitle
  walkthrough — the rule across two episodes of one show, a forced-only film —
  runs in one preview.

## 0.88.3 — the web player serves subtitle bundles

**Fixed**

- The player now builds its `SubtitleBundles` store at startup and hands it to
  the router, the catalog follower and the preload route (`src/index.ts`).
  Until now a title whose subtitles live in a bundle listed its tracks but
  answered no `.vtt`: phase 03 had built the store but could not wire it.
  Titles held in full have their bundles reconciled to disk at startup and
  after every catalog swap, so they keep their subtitles offline. The store is
  built even with the cache off; only the held copies need it.

## 0.88.2 — the web player stops fetching the same bytes over and over

What the viewer saw: `Sleeping for Ns on flood wait (Caused by
upload.GetFile)` in bursts of four whenever a title started or seeked — every
download slot hitting Telegram's limit at once. The limit counts requests,
not bytes, and every request asked for 512 KiB when Telegram allows 1 MiB.

**Changed**

- The cache chunk, and so the request a miss makes, is 1 MiB
  (`cache/key.ts`). The same bytes now cost half the `upload.getFile` calls;
  the series preload's one request a second now fetches 8 Mbit/s, not 4. The
  chunk size is part of the cache path, so the old `524288/` chunks are never
  read again; startup deletes any chunk-size directory but the current one
  (`retireOtherChunkSizes`), rather than leave it counted against the budget
  — 52 GB of 64 GB here.
- A fetch run stays 4 MiB (`MAX_RUN_BYTES`, now 4 chunks), so a cold seek
  waits for no more bytes than before.
- The channel index downloads in 1 MiB requests too.

**Fixed**

- The same chunks were fetched from Telegram many times over. ffmpeg opens a
  new range request for every seek while it finds its way around a file, and
  each one that landed on a chunk not yet fetched started its own fetch of it:
  one 4 MiB run went out 47 times in 45 seconds, all of them queued for the
  four download slots and drawing the flood waits that slowed the next. A read
  now waits for a fetch already bringing its chunks (`cache/in-flight-chunks.ts`).
  The same reproduction afterwards: 136 MiB fetched, 136 MiB cached, one flood
  wait. The series preload's own fetches are not shared — they are paced and
  yield to every viewer's read, so no viewer is left waiting on one.
- Telegram refuses any request that crosses a 1 MiB boundary of the file,
  whatever its size (`LIMIT_INVALID`; checked against the live API). Reads
  without a cache started on a 4 KiB boundary and broke on their second
  request after most seeks. Every read now starts on a 1 MiB boundary
  (`range.ts`'s `ALIGN`), which keeps each request inside one block.

The Android app still asks for 512 KiB: grammers 0.10 caps `chunk_size` there,
so matching this means a `upload.getFile` loop of the app's own.

## 0.88.1 — TV subtitle tests follow the track-based, off-by-default rule

**Fixed**

- `:ui-tv:testDebugUnitTest`'s three subtitle-cue tests now mock
  `SubtitleTrackSource.load(setId, track: Int)` and set up a forced track in
  the file's own audio language, or a regular track chosen by hand, rather
  than assuming the retired "first language wins" default. The two
  compatibility shims kept only for those tests —
  `SubtitleTrackSource`'s deprecated `load(setId, lang: String)` overload and
  `MediaSet.subtitleLanguages` — are removed now that nothing calls either.

## 0.88.0 — Measure existing titles before any subtitle backfill

**Added**

- `mediagram subtitles backfill <folder>... --dry-run`: matches local video
  files against every complete `movie`/`ep`/`docu` set in the local index by
  exact byte size first (a size shared by more than one set is ambiguous,
  never sent); an `.mp4` whose size names no set falls back to its parsed
  show/SxxEyy or title/year plus a duration within 2 s, only when that names
  exactly one set, and is listed apart from the matches — a later phase
  sends it only with an explicit per-file opt-in. Two files naming the same
  set are both rejected as a conflict. A header `ffprobe` of each matched or
  fallback source then counts its de/en text and picture-only subtitle
  tracks; the command prints a TSV per file plus totals by kind/container,
  and lists the de/en-subtitled sets no file matched. Nothing is extracted
  or sent — `--dry-run` is required until a later phase adds sending.
- `reports/uploaded-stream-headers.sh` gains a `subs` mode alongside `audio`:
  the same loopback-`serve` header probe, over every complete set whose
  `slang` lists de or en, counting the uploaded copy's own de/en text and
  picture-only subtitle tracks. Paired with the dry run above, a title's two
  rows say whether it already has subtitles in the channel, could gain them
  from a local source, or neither.

## 0.87.0 — Android: subtitle tracks, bundle cache, and the shared playback rule

**Added**

- `mediagram-core` reads a set's subtitle tracks (`catalog_subtitles`): a
  bundle's own `subtitle_tracks` row once uploaded, else its legacy inline
  `assets` rows in language order until then — a reader never needs to know
  which. `SetSummary` carries the tracks plus `alang`/`slang`, and
  `Core::subtitle_text(setId, track)` answers one track's WebVTT, `None` for
  legacy text and for a bundle it has to fetch first.
- The on-device bundle cache: one small file per set
  (`<data_dir>/subtitles/<sha>.json.gz`), sha-checked and written through a
  `.tmp` + fsync + rename, size-capped at 64 MiB rather than pruned on
  install so a pre-v13 push never wipes bundles already held. A corrupt or
  missing cache entry is refetched; two callers wanting the same bundle at
  once share one fetch through a per-sha lock.
  `Core::holdSubtitles(setId)` warms one set's bundle — wired into
  `CacheDataSourceWriter.write()`, so both series and film preloads take a
  title's subtitles along with its video. `Core::holdCourseSubtitles(setId)`
  warms a lesson's own bundle plus up to ten that follow it in its course's
  own order, sequentially — never the whole course, which can run to
  hundreds of lessons.
- Android ports the web's playback rule exactly (`SubtitleChoice.kt`,
  checked against the same shared fixture,
  `web/test/fixtures/subtitles/choice-cases.json`): subtitles off by
  default, a forced track in the playing audio's language shown regardless
  — even with subtitles off — a per-show remembered choice over a
  per-profile default, and a toggle-on rule that prefers this session's
  last regular track, then the profile default, then a track in the audio
  language, then the first one. The audio language follows the playing
  track when `AudioChoiceController` knows it, falling back to the set's
  own `alang` until it does.
- The Subtitles fact on a film's detail page and a show's summary
  (`slang`) now describes the file's own embedded tracks, distinct from
  the tracks a viewer can pick in the player (`subtitles`) — the same split
  the web player already drew.

**Changed**

- `crates/mediagram-core/src/api/channel/install.rs`'s capped download loop
  moved into `download.rs`, shared now by the index snapshot and a subtitle
  bundle fetch — one place enforcing a byte cap on anything downloaded
  through `iter_download`.
- `Core::set_text` answers only `"summary"` now; a subtitle track's text
  comes from `Core::subtitle_text`, keyed by position rather than language.

## 0.86.0 — Web player: subtitle files, the playback rule, picker, profile setting

**Added**

- The web player reads v13 subtitle tracks and fetches/caches their bundles
  through the existing Telegram fetch path: a bounded in-memory map keyed by
  `sha256` (so a title's forced and regular tracks share one fetch), backed
  by a disk store beside the chunk cache that is written only for a held or
  preloaded title. Every byte is verified — `sha256` shape and size checked
  before a fetch, the fetched bytes hashed before anything is written,
  decompression capped through `node:zlib` rather than `Bun.gunzipSync`,
  which has no such limit. A held title's next catalog swap reconciles its
  bundle back onto disk without ever deleting one; nothing is lost if a
  channel is pushed without the v13 tables.
- The default + toggle rule from a single shared fixture
  (`web/test/fixtures/subtitles/choice-cases.json`, the same one phone and TV
  are proved against): subtitles off by default; a forced track shows
  automatically in the audio's own language whenever no regular track is
  showing, including while regular subtitles are switched off — 'c' and the
  picker's Off row only ever touch the regular track. A per-show remembered
  choice, then a per-profile preferred language, decide what shows passively;
  'c' turns a regular track on to the last chosen this session, else the
  profile preference, else the audio language, else the first regular track.
- The subtitle picker offers Off plus the regular tracks only, never a
  forced one, and disappears entirely for a forced-only title — its style
  trigger (size, backing, sync offset) stays, since a forced track can still
  show. Settings → Profile gained a Subtitles row (Off/German/English),
  disabled with a "choose a profile first" hint until one is.
- New `/api/sets/:id/subtitles/:n.vtt`, keyed by a track's position in the
  catalog's own list rather than by language — several tracks per language
  are now possible. An index without the v13 tables still serves its legacy
  inline `assets` rows through the same route.

**Fixed**

- The film page's "Audio languages" and "Subtitles" facts were silently
  empty on every title: `languages()` required an actual array and was
  handed the index's raw JSON-string column. Parsed before use now, the way
  the show summary already did.

**Changed**

- `transport.js`'s subtitle picker, 'c' toggle and menu-building moved to
  `playback/subtitle-picker.js`; the rule itself is pure and lives in
  `playback/subtitle-choice.js`.

## 0.84.0 — Index v13: subtitle bundle tables, merge, publish guard

## 0.85.0 — Index v13: subtitle bundle tables, merge, publish guard

**Added**

- `library.db` schema v13: `subtitle_files` and `subtitle_tracks`, additive —
  every deployed reader keeps working against a v13 index, the web player's
  `EXPECTED_SCHEMA` included. A merge takes a channel bundle whose
  `uploaded_at` is newer than the local one, tracks replaced whole rather
  than filled row by row, since the two uploaders never split one set's
  bundle between them; a bundled set keeps no inline `assets` rows, and the
  channel-side exclusion stops a stale inline row from being refilled once a
  set is bundled.
- The subtitle bundle format itself (`mlib_spec::subtitle_bundle`): gzip'd
  JSON, one document per set, decoded through capped reads at every stage —
  compressed size, decompressed size, each track's cue text — before any of
  it is trusted, with its own channel caption (`#mlib-subs`) that collides
  with neither a part's marker nor an index snapshot's.
- A push or pull now refuses a channel index whose caption names a schema
  newer than the build understands, `--force` included; a pull that finds
  the channel missing its subtitle tables while the local index still holds
  rows records a publish owed and republishes to restore them, printing why.
- `ChannelRemote::send_index` is now `send_document`, taking bytes rather
  than a path: one send path for the index snapshot and, once the uploader
  writes them, a subtitle bundle — no second way to post a small document to
  the channel.

**Changed**

- `crates/mlib-spec/src/schema.rs` moved v7–v9 into `schema_versions.rs`
  alongside v1–v6, keeping both files under the crate's line limit as v13
  landed; a new version still lands in `schema.rs` itself, and the next one
  to force a move takes the oldest group there, not the newest.

## 0.84.8 — Faststart remux keeps every stream mp4 can hold

**Fixed**

- The faststart remux (`ensure_faststart`) no longer relies on ffmpeg's
  default stream selection, which kept one video and one audio stream and
  dropped every subtitle. The map is now built from a probe: every video
  stream that is not an attached picture, every audio stream, and one
  `-map` per subtitle stream whose codec the mp4 muxer accepts
  (`mov_text`, `dvd_subtitle`). A source whose mapped remux the muxer
  refuses — a stream that today's blanket copy would have silently
  dropped — falls back once to today's unmapped arguments rather than
  failing the whole upload.
- Added a one-off, read-only audit script
  (`plans/260930-0303-subtitles-for-films-and-series/reports/uploaded-stream-headers.sh`)
  that probes already-uploaded mp4 sets over a loopback `mediagram serve`
  and reports which lost an audio language under the old remux.

## 0.84.7 — the uploader refuses a channel index newer than it understands

**Added**

- `pull-index` and `push-index` (`--force` included) now refuse when the
  channel's own index caption names a `library.db` schema newer than this
  build's, naming both versions and asking for a reinstall rather than
  silently dropping rows it cannot read or overwriting them with an older
  copy.
- The index caption now carries the uploader's own version (`"uploader"`),
  so a push can be checked from the *other* machine's own pin without
  running a command there.
- `resume`, `verify --all` and merge-conflict resolution skip a set whose
  `kind` this build cannot decode — from a newer uploader, not corruption —
  and print one line naming the kind and how many were skipped, rather than
  aborting the whole run. Naming a set explicitly still fails on it.
- `rescan` and merge-conflict resolution now count and print captions whose
  `#mlib v=N` is newer than this build reads, instead of only a debug log.
- A set already finished on another machine under a caption version this
  build cannot read is refused rather than adopted or resent: `resume`
  now says to finish it on the machine that started it.

## 0.84.6 — Android TV: keep-alive withdrawn; stable card focus across reorders kept

**Changed**

- Home goes back to unmounting under a pushed frame and rebuilding on
  return, as it was before 0.84.1 — `TvHomeLayer`, `coveredLayer`,
  `heldWhile`, `LocalLibraryCovered`, `rememberArrivalReady`, the `arrived`
  re-arm-on-covered latch, the `PinnableContainer` pin from 0.84.5, and the
  generic bar-recovery rule (`barRequested`/`requestBarFocus`) are all
  removed. User decision after five box rounds (0.84.1–0.84.5) each fixing
  a different Compose mechanism that landed the remote on the bar's first
  pill instead of the stop a pushed frame was opened from — frame removal,
  a startup refresh resetting a just-granted arrival, a modifier's own
  presence toggling, a lazy layout deactivating a focused card's own slot,
  and, last, the cold-start first title-page return still losing focus with
  neither of those four fixes catching it. Full account in
  `docs/system-architecture.md` § Television differs and
  `plans/260929-0215-tv-web-look-chrome-home-departments/phase-04-tv-home-kept-alive-measure-docs.md`.

**Kept**

- Every card on this surface (Home's bands, a department's rows, a wall's
  own grid, cast/similar/search rows, the departments bar's own pills, a
  franchise row, and static choice leaves) still carries its own stable,
  always-attached `FocusRequester`, from 0.84.4 — a real bug independent of
  keep-alive: a reorder or refresh moving which card an arrival names would
  detach and reset the focused card's own modifier chain either way.

## 0.84.5 — Android TV: a lazy layout deactivating the just-focused card, and a generic recovery rule

**Fixed**

- Found on the box after 0.84.4: the cold-start repro (launch, Down to the
  first Recently Added poster, OK, Back) lost focus to the Home pill again,
  1/1, once the previous round's safety net was gone. Stack: a
  `SubcomposeLayout` — Home's own `LazyColumn` — deactivating a lazy item's
  slot out of frame, in a runnable it posts to run after the current one,
  even though the slot held the node arrival focus had just landed in;
  `FocusTargetNode.onReset` only clears when the node it is on holds focus.
  Fixed by pinning a band's own slot, through `PinnableContainer`, for as
  long as focus sits inside it — the same guarantee a lazy layout's own
  pinned items lean on internally, asked for here the public way instead.
  Not reproducible in Robolectric — its lazy-layout machinery never runs the
  out-of-frame executor a real device's own `Choreographer` does.

**Added**

- A generic rule at `TvLibraryChrome`, catching the shape all three bugs
  found so far this round share (frame removal, a modifier's own presence
  toggling, and this one) rather than a fourth: the bar gaining focus with
  nothing behind it that names this app's own reason to put it there — no
  directional/OK/Back key event, and no deliberate `requestFocus()` call
  onto one of the bar's own requesters (a `BackHandler`, or a sentinel in
  `TvCatalogNav.kt` restoring the search icon or ⋮) — sends the remote back
  to content instead of leaving it on whatever the bar's first pill happens
  to be. Verified in Robolectric with a forced `FocusManager.clearFocus`
  after an arrival with no key input, and confirmed a real Up-press still
  lands on and stays on the bar. Known gap, left for the box to confirm:
  the rule races the search-icon/⋮ sentinel's own restore after Back closes
  a frame that covers the chrome, in a way three different Robolectric
  settling strategies could not resolve either direction — the three tests
  for it are `@Ignore`d with the investigation left in place at
  `TvLibraryChrome.kt`.

## 0.84.4 — Android TV: the root cause of a reordered card losing focus

**Fixed**

- Found on the box: playing the second Continue card, then Back, lost focus
  to the Home pill every time — the reorder that plays it moves it to the
  row's own front, and every row on this surface (Home's bands, a
  department's rows, a wall's own grid, cast/similar/search rows, the
  departments bar's own pills, a franchise row, a settings choice, a
  profile tile, an accent swatch) attached its arrival `FocusRequester` to
  whichever card an index currently named and omitted it from every other
  one — so the moment that index moved, the modifier that had been on the
  focused card's own chain vanished from under it. Compose detaches and
  rebuilds a focus target's whole chain when a modifier structurally
  appears or disappears ahead of it, clearing whatever it held; the root's
  own focus search then lands on the first focusable it finds, the bar.
  Fixed at the source, everywhere this pattern appeared: every card carries
  its own stable requester now, always attached, so only which requester
  a caller passes changes, never whether one is there at all — replaces the
  three-frame safety net 0.84.3 added, which patched the symptom for one
  path and is no longer needed once every path's own root cause is fixed.
  Not reproducible in Robolectric — confirmed by hand-reverting the fix and
  re-running the test written for it, which stayed green either way, the
  same limitation already found and reported for the two regressions before
  it.

## 0.84.3 — Android TV: a safety net for the first return after a cold-start refresh

**Added**

- Found on the box after 0.84.2: on the very first return from a title page
  after a cold start — but not on later ones — focus still landed on the
  Home pill, 2/2. A startup refresh completing while the title page covered
  Home released a changed catalogue the moment Back uncovered it
  (`heldWhile`), and the resulting lazy-layout churn cleared the arrival
  grant's own request a few frames after it had already landed, past
  `TvRecentBand`'s own row. Not reproduced in Robolectric despite trying the
  box's own shape (a Recently Added list gaining an item, with and without a
  cover appearing for the first time alongside it) — Home's own arrival
  grant now asks again once, a few frames later, if nothing in it still
  holds focus by then: far sooner than a viewer could press anything to
  explain the loss honestly, so it never answers for a deliberate move to
  the bar instead.

## 0.84.2 — Android TV: a title-page return landed on the Home pill, not the poster

**Fixed**

- Found on the box, 3/3: Back from a title page (or a season, a collection,
  a franchise…) opened from Home returned focus to the departments bar's
  Home pill rather than the poster that opened it, Home left scrolled to
  Features/Continue with the poster's own row off screen. Root cause: the
  frame that removes the pushed frame from the composition is also the
  frame Android notices that frame's own focused view just detached, clears
  focus on the whole `AndroidComposeView`, and re-grants it to the first
  focusable it finds — ahead of whatever Home's own arrival effect asked
  for a moment earlier in that same frame. Fixed generically, not per
  effect: `rememberArrivalReady` (`TvArrivalFocus.kt`) delays "safe to take
  arrival focus" by one frame past a pushed frame's own removal, so a real
  return's request is the last one standing rather than the one the reset
  undoes.
- A second, separate bug the same fix uncovered: the catalogue's own root
  was reading and writing its restore keys at whatever depth was currently
  on top of the position stack, rather than always at its own fixed depth
  — harmless before this phase, since the catalogue only ever existed at
  the top; wrong now that it stays composed under every pushed frame. A
  return from two or three frames deep (an episode played from a show
  opened from Home) restored correctly regardless, by coincidence, until
  the arrival fix above made the correct depth matter.

## 0.84.1 — Android TV: Home kept alive under a pushed frame

**Added**

- The television catalogue's own root — the rail, the departments bar and
  whichever tab is chosen — stays composed and laid out under a title, the
  player, Search, Latest, Genres, Settings/System, a list or the menu page,
  rather than being torn down and rebuilt every time one of them opens and
  every time Back leaves it: hidden (not drawn, not focusable, its semantics
  cleared) and inert (its own `BackHandler`s off, the cover's rotation
  paused, its `CatalogUiState` frozen at the moment it was covered) while
  covered, so a return is a focus restore rather than a full
  compose/measure/place (`ui-tv/.../TvHomeLayer.kt`). Tab switches are still
  a rebuild, by design: a new tab must not inherit the old one's scroll.
- Every arrival effect this touches now re-runs on that same return, not
  only once per mount, which no longer happens: `TvWall`, the two kept
  tabs' own empty state, and the Collections/lists row all gained the key
  they were missing; Home's own arrival grant now re-arms itself the moment
  a pushed frame covers it, rather than staying spent from the first time
  the app ever opened it; and the chrome's own six sentinel restores
  (Search, ⋮, Latest, Genres, Settings, System) now wait out being covered
  before consuming their own restore key, so a Back from any of them still
  finds it there instead of a key already forgotten the instant that frame
  opened.
- DESIGN.md now documents the television's own chrome (rail, departments
  bar, cover remote, focus treatment) as its own section rather than only
  in passing beside the Pill and Artwork entries, and both the Wall Rule
  and its Don't now say plainly that Home's own curated bands and a
  department's own rows were never the shelf wall they describe.

## 0.84.0 — Android TV: department pages in the web player's own layout

**Added**

- Every television department pill — Movies, Series, Tutorials, Anime,
  Documentaries, Collections — now opens the web player's own department
  hero (kicker, huge title, a figures line, lead art fading in from the
  right, a pull-quote top-right) above the web's own rows, in place of the
  old, department-specific headers. The hero is fixed at 360dp (the web's
  own fluid clamp would fill most of a 540dp screen) and is never a focus
  stop itself — Down from the pill lands on the first row's own first stop.
- Anime and Documentaries stop being plain poster walls: Anime draws a hero,
  Continue watching, then every show and film as one wall with "Series" and
  "Films" headings sections (a show's plate opens the show; a film's opens
  the title page, never plays directly — a locked, deliberate difference
  from the web's own direct play). Documentaries draws a hero, Continue
  watching, one row per hand-set category (a folder opens, a single plays),
  Recently added, one row per folder ("All N →" when it holds more), and
  Standalone documentaries — every plate here plays on OK, the same as the
  web and the tablet.
- Movies' front page is a `LazyColumn` now, not a `Column` with
  `verticalScroll`; Documentaries' is too. Every department row is keyed by
  its own id, and each page carries its own `focusRestorer` so the rail's
  Right returns to the plate it left without outranking an explicit restore
  key naming a row further down.
- `heroArtOf`, the department hero's own spelled figures line
  (`moviesLineOf`/`showsLineOf`/`animeLineOf`/`documentariesLineOf`/
  `collectionsLineOf`) and `resumeCardsOf` moved to `feature:catalog`,
  shared by the tablet and the television rather than each keeping its own
  copy — the tablet's own Movies, Series/Tutorials, Anime and Collections
  hero lines now spell a count of twenty or fewer the way the web's
  `countOf` and Documentaries' own line already did ("four courses", not
  "4 courses").
- The departments bar's own bar-over-hero blend, until now Home's alone,
  now reads whichever department tab is showing too, through the same
  fixed hero height every department page draws.

**Removed**

- The two written-down differences from the web player — Anime and
  Documentaries drawing as plain poster walls with no category rows — no
  longer exist to record.

## 0.83.0 — Android TV: Home in the web player's own magazine layout

**Added**

- The television's start page draws the web player's own magazine layout
  instead of a plain row of "Latest films": a cover story under the
  departments bar (bleeding full-width, the bar reading translucent-to-opaque
  from its own scroll position), three feature cards, Continue watching
  beside a pull-quote, Recently added beside This month, then Latest series
  and Latest courses. Watch now plays straight away; + My List reaches
  `CatalogViewModel.setWatchlisted`, new on this surface; a focused dot in the
  cover's own pager shows its film. Home is now a `LazyColumn` of these
  sections rather than a `Column` that composed every row at once, with a
  cache window generous enough (six sections at most) to keep them all
  composed almost all the time on a 540dp screen.
- `CoverBlend` and the cover's own scrim moved to `ui-common` so the
  television's bar-over-cover bleed reads the same live measurement the
  tablet's own hero pages already do, and `spelledCountOf`/`FeatureKind`'s
  label mapping moved to `feature:catalog` so both surfaces spell a series'
  episode/season count and a feature's own label the same way.
- The open rail's own "Continue watching" row is no longer missing its
  count: its label pushed the number past the row's own clipped edge for
  want of a `weight(1f)` "My List" (a shorter label) never needed.

**Fixed**

- The television's own resume cards (Continue, Next up) now carry the
  offline badge held titles already show everywhere else on this surface —
  dropped by mistake when Continue moved from a plain row into this band,
  which would have been a silent loss of something a viewer relied on before
  pressing Watch now.
- Back from the player lands on the title just played, now first in Continue
  watching, and the row scrolls to show it. Home's cards were matched by
  position rather than by title, so focus stayed in the old slot and fell on
  whichever title had moved into it; the four home rows now key their cards
  by title.
- A title two bands carry at once (a new upload that is also trending) comes
  back to the band it was opened from, not the first one down the page.
- Home's own return focus is no longer turned into the bar's pill after the
  remote has passed through the bar. The chrome restored focus over bar and
  page together, and that restore always found the bar; the rail's Right now
  goes back to the side it was left from, the bar's pill or the page.
- Entering the cover from the rows below brings the whole cover into view, so
  its heading no longer sits under the bar.

## 0.82.3 — a runtime that rounds up to the hour no longer reads "2h 60m"

**Fixed**

- `humanDuration` rounded only the minutes left over after the whole hours,
  so a film of 2h 59m 40s printed as "2h 60m" and a 59m 50s one as "60m".
  It now rounds the whole runtime to minutes first and splits that, giving
  "3h" and "1h". The web player's `format.js` and Android's `TitleFacts.kt`
  had the same code, so the fix is in both; the TV cover on the box is where
  it was seen.
- The Android Movies department cut its "hours of film" total down to the
  whole hour while the web player rounds it, so the two could disagree by
  one. Android now rounds too.

## 0.82.2 — a backdrop kept only in the index's artwork table now shows on Android too

**Fixed**

- Android's backdrop resolution checked disk and disk alone, unlike a poster
  or a season poster, which already materialise from the index's `artwork`
  table on a miss. A title whose backdrop an uploader supplied only into
  that table — no packaged or fetched file on disk — showed its backdrop on
  the web player and not in the Android app; 12 titles on the current index
  were affected. Backdrop resolution now materialises the same way a poster
  does, closing the gap.

## 0.82.1 — the TV chrome could close the app before the remote landed anywhere

**Fixed**

- The new chrome's Back chain (0.82.0) only caught Back once the remote had
  genuinely reached content, the bar or the rail — every fresh mount of the
  chrome (arriving on Home, or returning to it from a title, the player, any
  pushed page) has a real gap, at least one frame wide, between that mount
  and arrival focus actually landing: none of the three regions has focus
  yet, the same shape a viewer genuinely resting on the rail leaves. A Back
  arriving in that gap fell through and closed the app uninvited. Fixed by
  tracking the rail's own focus alongside content's and the bar's, and
  catching (not redirecting) a Back while none of the three has settled yet.

## 0.82.0 — Android TV: the library wears the web player's chrome

**Added**

- The television's masthead is gone; in its place, the web player's own
  chrome: a left rail (icons alone until the remote reaches it, opening then
  to My List, Continue watching, Latest, Genres, Settings, System and the
  library's own tally) beside a departments bar across the top (Home, the
  shelves, Collections, search, the viewer's avatar, ⋮). Pressing a
  department pill swaps the page but keeps the remote on the pill — Down is
  what steps it into the page — and Back walks out one region at a time:
  content to the selected pill (or, on a kept wall, the rail's own active
  row), the bar to the rail, the rail unhandled so a further Back closes the
  app. The ⋮ menu keeps only the phone's three Android-only rows (Update
  library, TMDB key…, Start over) plus Preloads; every pushed page (a title,
  Search, Latest, Genres, Settings, System…) still fills the whole screen,
  rail included — a deliberate difference from the tablet, which keeps its
  rail beside a pushed frame (`docs/system-architecture.md` § Television
  differs). `TvFocus` gained two more shapes beside its square plate — fully
  round for a pill, and Settings' own rounded corner for a rail row — both
  cut from the same constant as the border drawn on top of them.
- `ChromeCounts` and the `RailItem` enum moved to `feature:catalog` and
  `ui-common` respectively so the television's own rail and departments bar
  can read the same counts and rows the tablet's rail already does, without
  either surface owning the other's copy.

## 0.81.1 — an upload-lock test that failed on a busy run

**Fixed**

- The upload-lock tests asserted that a lock is free the instant its holder
  drops it. It is, unless a test running beside them starts a child process
  (the `prepare` tests run ffmpeg) at that moment: the child holds a copy of
  every open descriptor until its `exec` closes them, and `flock` stays held
  through that copy for a moment. The two affected checks now wait up to a
  second for the release. Nothing in the lock itself changed; it had failed a
  pre-push run once.

## 0.81.0 — Android: Tutorials and Documentaries grouped by category

**Added**

- The Android app draws the same category rows the web player does: the core
  attaches `category` to every `SetSummary` (`mlib_spec::category_key`
  against the index's `categories` table, `None` for a film, an episode, an
  unfiled unit, or an index older than v12), carried through to
  `MediaSet.category`. On the phone and tablet, the Tutorials and
  Documentaries department pages gain one row per category after the
  Continue row — same alphabetical, natural-order rule as the web
  (`categoryRowsOf`, `android/feature/catalog/src/main/kotlin/Categories.kt`),
  held to the same shared fixture (`web/test/fixtures/categories/rows.json`).
  On television, the Tutorials front page gets the same rows between Continue
  and Popular/New; Documentaries stays the plain wall it already was — its
  own front page never became a rows page in the first place, the same
  reason Anime is a wall there.

## 0.80.0 — Tutorials and Documentaries grouped by category

**Added**

- The web player draws the categories 0.79.0's uploader can now set:
  `/api/sets` and `/api/search` carry `category: string | null` on every
  row (`null` for a film, an episode, or an unfiled unit, and for every
  row of an index older than v12). Once at least one course, documentary
  collection or standalone documentary in a department is filed, its page
  gains one strip per category, right after Continue — alphabetical,
  natural order, with an "Other" strip last for whatever is not yet
  filed. Nothing is filed yet: every existing library keeps rendering
  exactly as before, since a department with nothing categorised draws no
  rows at all. The rule (`categoryRows`, `web/public/lib/categories.js`)
  and its key derivation (`web/src/catalog/categories.ts`, a TypeScript
  twin of `mlib_spec::category_key`) are both held to fixtures the Android
  port will run too.

## 0.79.0 — courses and documentaries can be given a category

**Added**

- Schema v12: a `categories` table holding a hand-set label on a course, a
  documentary collection or a standalone documentary, keyed the way a
  unit's custom artwork already is (`department`,
  `title_art_key(show ?? title)`), so a category survives a re-upload or a
  resume and follows a course uploaded from two folders under one title.
  `add-course`/`add-docu --category "<name>"` file a unit at upload time;
  `edit <set-id> --category "<name>"`/`--clear-category` correct it
  afterwards, index-only. A name is trimmed, collapsed, refused empty or
  "Other" in any case, and a case-insensitive match of a spelling already
  used elsewhere in the same department adopts that spelling instead.
  `pull-index`/`push-index` merge the table last-writer-wins between two
  uploading machines. Uploader-only: no reader draws from the table yet.

## 0.78.0 — an Anime department on phone, tablet and television

**Added**

- Android draws the Anime department the web player already shipped in
  0.77.0: `shelvesOf` pulls anime out ahead of Movies and Series the same
  way it already pulls out Documentaries, and the shelf sits between Series
  and Documentaries — a hero, Continue watching, every anime series and
  every anime film. The tab hides at zero; genre pages, search, person
  pages, franchises, Similar, Continue, Next up and autoplay all still find
  an anime title where it actually is, and a kids profile that cannot see
  any of it gets no Anime tab. On television it draws as a plain poster
  wall, the same as Documentaries, rather than the Series-style department
  page phone and tablet get — a show-only page there would drop every anime
  film.

## 0.77.1 — Android: the core knows which titles are anime, not yet reachable from the UI

**Added**

- `mediagram_core::shows::is_anime`, a line-for-line port of the web
  player's own rule, held to the same fixture cases the web's own tests run
  (`shared_anime_fixtures.rs`). `SetSummary.anime` carries the verdict across
  the UniFFI boundary — computed in `store::editorial::enrich` from the
  index's genres and `original_language`, this device's own fetched sidecar
  as a fallback, and the index's `anime_overrides` — and lands on
  `MediaSet.anime`. An index older than schema v11 lists everything
  `anime = false`, same as it always has. Android-only: the web player
  already shipped this in 0.77.0.

## 0.77.0 — an Anime department for Japanese animation

**Added**

- The web player: Japanese animation (TMDB genre "Animation" plus original
  language `ja`, or a `mediagram edit --anime` override) leaves Movies and
  Series for its own Anime department — a hero, Continue watching, every
  anime series and every anime film, the same shape Documentaries already
  has. The nav tab hides at zero (no upload command points at an empty one);
  `#/anime` itself still renders the empty state. Genre pages, search, person
  pages, franchises, Similar, Continue and Next up all still find an anime
  title where it actually is; the home page's editorial picks stay
  Movies-only, the same rule Documentaries already followed. An index older
  than schema v11 shows no anime at all — Movies and Series read exactly as
  they did before this release.

## 0.76.0 — titles carry their original language, and anime can be set by hand

**Added**

- Schema v11: `shows.original_language`, TMDB's code for a title (`ja`, `en`,
  …), backfilled by `mediagram metadata` from the cached TMDB payload — no key,
  no network needed for a library `add` already resolved. A new
  `anime_overrides` table holds a hand-set decision that a title is, or is
  not, anime, kept apart from `shows` because that table's writer replaces a
  row whole on every `metadata` run. `mediagram edit <set-id> --anime
  yes|no|auto` sets or clears an override, keyed to the TMDB title so it
  covers every episode of a series, including ones uploaded later;
  index-only, `--dry-run` supported, refuses a set with no TMDB id or a
  course. `pull-index`/`push-index` merge both: `original_language` rides the
  existing NULL-fill, and overrides take whichever machine's `set_at` is
  newer. Readers on v10 or older simply do not see the column or table yet;
  no reader behaviour changes in this release.

## 0.75.5 — the television's episode rows play too

**Fixed**

- On the television, an episode or lesson row plays, on a show, a season and
  a course alike, as it does on the phone since 0.75.3 and on the web
  (`lessonRow`); it used to open a title page first. Back from the player
  lands on the row that was played. The television's own Continue rows still
  open a title page — `TvHome` documents that difference, and its Series and
  Tutorials pages keep to it.

## 0.75.4 — the app opens in the theme you chose

**Fixed**

- The window Android draws before the app's first frame follows the theme
  chosen in Settings › Appearance, not the device's own mode. 0.75.3 made it
  follow the device, so Dark chosen on a light-mode device opened on a light
  flash; the choice is now handed to the system as the app's own night mode
  (`UiModeManager.setApplicationNightMode`, API 31+), at startup and on every
  change. Checked on the tablet (light system, Dark chosen): the cold start
  opens dark. Below API 31 the window still follows the device.

## 0.75.3 — the phone after review, ported onto the redesign

The phone review's fixes from 2026-09-26, never committed until now, carried
onto the pages the home and department redesign rebuilt since. Fixes the
redesign had already made (Collections scrolling as one list, the department
hero's height, the Similar tab's empty sentence, no department highlighted for
Continue or My List) were left as they are.

**Fixed**

- The app opens in the right colours. The cold-start window is light on a
  light system and dark on a dark one (`window_ground`, per night mode), and
  always dark on the television, which is dark whatever the system says. The
  status and navigation bar icons follow the theme actually chosen in
  Settings › Appearance, live, not a guess made before Compose started.
- An actor's portrait no longer falls back to initials when its card scrolls
  away and back: a found portrait is remembered for the session.
- Every episode and lesson row plays, on a series page, a course page and a
  season page alike, as the web's `lessonRow` does; none opens a title page
  first.
- A Continue card on the Series or Tutorials page plays, like every other
  resume card; it used to open the title page.
- Popular and New rows appear on Series only, never on Tutorials, however
  many courses there are: the web's own `series && shows.length > ROW` gate,
  now in the shared model, so the television follows it too.
- A title opened from another title's Similar row opens on Overview, and
  going back finds the first one still on the tab and scroll it was left on.
  Each screen keeps its own saved state, dropped once it is popped.
- A series page opens on the season the resume point is in even when watch
  state arrives after the first frame, which it usually does.
- Film and documentary rows on the department pages mark what has been
  watched; they drew every card as unwatched.
- A title page's tagline is drawn over its art in the web's `--on-image`
  with a text shadow, readable in Light, and only on a wide window, as the web
  hides `.spread-quote` below 900px. The art is capped at 40% of the screen's
  height, so a landscape phone keeps the title and tabs above the fold.

**Tests**

- Accent colours are checked at 4.5:1 against the containers they sit on
  (`Sunk`, `LightSunk`, `LightPage`), not only the page.

## 0.75.2 — the cache server's set status, in files of its own

**Internal**

- `GET /v1/sets/{id}` and the store query behind it move into
  `http/set_status.rs` and `store/set_status.rs`. The 0.73.0 change that
  added them left `http.rs` at 224 lines and `store.rs` at 223, over the
  200-line limit `code_standards` holds every source file to, which failed
  the pre-push check. Nothing the route answers changed.

## 0.75.1 — `PreloadService` teardown, made robust

**Fixed**

- `PreloadService.onDestroy` now mirrors `onTimeout`'s own
  `pauseForTimeLimit()` call whenever the service is torn down while a
  preload is still genuinely outstanding (`FilmPreloading.hasWork` true at
  that moment) — previously only `onTimeout` (Android's own dataSync
  ceiling firing) did this; any *other* reason the service stops mid-work
  left the affected film reverting to a bare "Preload · N% held" pill
  instead of the already-designed "Paused — background limit" + Resume
  treatment. Guarded on `hasWork` itself so the service's own ordinary
  self-stop (once the queue empties) stays a no-op, not a blind call.

**Investigated, not a bug**

- A tablet device-verification pass had flagged `PreloadService` being
  torn down roughly 55–75s after every start, logged by
  `ActivityManager` as "Stop FGS timeout" preceded by a MIUI-only
  "does not have any types" warning — read at the time as a possible
  platform/targetSdk issue. Root-caused this pass: `dumpsys activity
  services` shows the service's own foreground type correctly held as
  `dataSync` (`types=0x00000001`) for its entire life; AOSP's
  `ActiveServices.maybeStopFgsTimeoutLocked` (the function behind that log
  line) is called from the *ordinary* `stopService`/`stopForeground`
  paths, not from the abuse-prevention timer itself (`onFgsTimeout`) — it
  is bookkeeping cleanup for a stop that already happened, not evidence
  one was forced. A clean, isolated repro on the same tablet confirmed the
  timing lines up exactly with the film's own completion (`FilmPreload:
  ... held` logged 119ms before "Stop FGS timeout"), i.e. this service's
  own `hasWork.filter { !it }.collect { stopSelf() }` stopping itself
  normally once the queue emptied. The MIUI "no types" warning does not
  reflect the real, correctly-typed service record either. No manifest or
  targetSdk change made. `PreloadService.onDestroy`'s new guard above
  stands regardless, as defense against whatever *does* someday stop this
  service outside the two paths it already expects.

## 0.75.0 — Android: the preload queue, made visible

**Added**

- `FilmPreloader` exposes its own queue as one ordered list — the running
  film first (its live held bytes and pause reason, if paused), then every
  waiting film in FIFO order — without touching any of the engine's own
  rules. A queued film's own label on the film page now names what it is
  actually waiting on instead of the bare "Queued": "Queued · after Der
  Pate, 36%" when nothing but the running film precedes it, "Queued · 2
  ahead" otherwise. Phone and TV alike; a tap still cancels.
- A Preloads page (phone/tablet and TV), reachable from the overflow menu
  (phone) or the TV menu once anything is running or queued — "Preloads ·
  n", present only then. Three sections, empty ones hidden: Preloading
  (title, bar, "x of y · n%", its own pause reason, Cancel), Queued (in
  FIFO order, Cancel), On this device (films fully held — the catalogue's
  own held set, Remove). Each row opens its film. An idle, empty page says
  nothing is preloading rather than showing three empty headings.
  Android-only, the web has no film preload.
- `NeedsSpace` now names the live cache budget beside what the film needs
  — "Needs 30 GB · budget is 8.0 GB · Try again" — the same figure the
  engine's own `fits` rule reads, so the reason a 30 GB film cannot start
  is visible without opening Settings first. A test walk against a real
  8 GB TV box budget is what asked for this.
- A film Android's own background time limit paused stays listed on the
  Preloads page — under Preloading if it was the one actually writing,
  under Queued otherwise — named "Paused — background limit" with one
  Resume action, rather than disappearing along with the rest of the
  queue the time limit clears. The engine reports these apart from the
  queue itself (`FilmPreloader.timeLimitPaused`), since pausing for the
  time limit empties the queue by design; the menu's own count includes
  them.
- A kids profile's Preloads page, menu count, and a queued film's own
  "Queued · after …" label all read only what that profile's own catalogue
  can resolve — a grown-up's own preload never surfaces there, the same
  reason it never appears on a kids profile's shelves.

## 0.74.0 — Android: Preload reaches the film page

**Added**

- A Preload control beside Play on a film's own page, phone/tablet and TV
  alike — the first thing on either screen that drives `FilmPreloader`
  (0.70.1). Films only (`Kind.MOVIE`, with a real size on record; a show's
  episodes already preload two at a time on their own): "Preload · 5.8 GB"
  idle, "Preload · 36% held" once playback alone has already put some of it
  in the cache, "Queued" waiting behind another film, "Preloading" with a
  thin full-width bar underneath on both surfaces ("2.1 of 5.8 GB · 36%",
  tap or OK to cancel), "Paused while playing" / "Waiting for Wi-Fi" / a
  background-limit pause (tap or OK resumes it) with the bar left standing
  through all three, "Preloaded ✓" with "Remove preload" in the phone's ⋯
  menu or a second TV plate, and "Needs 5.8 GB · Try again" — retryable
  the moment the cache budget is raised, not a dead end — beside a "Raise
  the cache budget" link straight into Settings › Storage. Reachable for a
  kids profile the same as Play is — it is not a household mark the way the
  editor's-choice pin beside it is. The phone's own pill row wraps
  (`FlowRow`) rather than crowding My List and ⋯ off a narrow phone once a
  Preload pill joins them.
- A quiet "Home server: x of y GB" line under the control once a paired LAN
  cache server actually holds some of the film (`GET /v1/sets/{id}`,
  0.70.0) — polled every 5s while the page is open and the film is
  actually writing, once on open otherwise, and not at all with the LAN
  cache off, no server paired, or an older server that has never heard of
  the route.
- `MenuScreen.Storage`: Settings opened straight to its Storage section,
  the same direct-section shape `System` already had — what "Raise the
  cache budget" actually opens.
- Android-only, by the same decision `FilmPreloader` itself shipped under:
  the web player has no film preload and gets none.

## 0.73.1 — Android: a film preload engine, not yet reachable from the UI

**Added**

- `FilmPreloader` (`:core:playback`): takes one film at a time into the
  device cache through the same strict `CacheWriter` writer `SeriesPreloader`
  already uses, now sharing one writer instance behind a `DownloadLane` so
  the two never write at once. `CacheWriter.cache()` blocks its own
  dedicated thread (`CacheDataSourceWriter`'s own dispatcher, not the
  preloader's worker), and is interrupted by cancelling the coroutine that
  called it — cancel, remove, and Android's own foreground-service time
  limit each cancel that one film's own `Job`, which reaches a running
  write, a metered-network wait, and a retry backoff alike. Per-film state
  (`Idle`/`Queued`/`Running`/`Paused`/`Done`/`NeedsSpace`/`Failed`) reads
  its held bytes from the cache on every observation, not once and
  remembered — a film already part-held from playback starts at its real
  percentage, a cancelled or removed one settles back to one just as
  honestly, and a `Done` film that eviction later took back stops claiming
  to be held. Pauses while a title is open in the player — playing,
  buffering, or paused by the viewer, any film, not only this one — and
  resumes once the player closes; pauses on a metered network instead of
  failing, checked both before a write starts and while one is running. A
  film's own whole size, not what else the cache happens to hold, decides
  whether it fits the budget (the LRU cache evicts older content to make
  room; only the title open in the player, if a different film, is
  reserved); a write failure backs off and retries a bounded number of
  times rather than giving up outright, since the core has no way to tell
  Kotlin a Telegram `FLOOD_WAIT` apart from any other transient fault.
- `PreloadService`: a `dataSync` foreground service that keeps a queued
  preload running while the app is backgrounded, started the moment
  anything is enqueued and stopping itself once the queue empties (staying
  foreground through a pause, since a `dataSync` service cannot be
  restarted from the background). Android's own 6h/24h ceiling for the
  type surfaces as a resumable pause rather than a crash. No
  `POST_NOTIFICATIONS` — the service runs regardless; its own notification
  is simply never shown, and a film page's progress bar is where a viewer
  actually watches it.
- `ActivePlayback`: feeds the engine what the player has open from a
  main-thread listener only — a film's own worker never touches the real
  `ExoPlayer` directly, which media3 does not allow off its own thread —
  and starts listening lazily, the first time a film is actually
  preloaded, so the catalogue merely being open never forces the app's
  player to build.
- Nothing in the app calls any of this yet: no button, no bar, no route
  into `FilmPreloader.enqueue`. The engine exists on its own; the film page
  that drives it is a later change.

## 0.73.0 — Cache server: a per-film status route

**Added**

- `GET /v1/sets/{id}` on `mediagram_cache`: `{"total","chunks_held","bytes_held"}`
  for one set, `total` null when the server holds no recorded total for it. A
  question, not a read — it touches no chunk's mtime and moves nothing in the
  LRU order, so polling it cannot itself change what eviction picks next.
  Answered straight from the in-memory index (already keyed per chunk) rather
  than a directory scan, since the index already makes a per-set tally cheap.
  Android's `LanChunkProtocol.setStatus` reads it the same lenient way
  `status()` already reads `/v1/status`: `null` for a 404 (a malformed id,
  or an older server without the route at all), a network error, or a
  malformed body — an id the server simply holds nothing of is a 200 with
  zeros instead.

## 0.72.1 — the department hero, in the web player's own layout

**Changed**

- Movies, Series, Tutorials, Documentaries, Collections and a franchise's
  own page open the way the web player's own department pages do: a lead
  title's art fading into the page from the left under the departments bar,
  an uppercase Fraunces name set as large as the cover's own, a Geist
  eyebrow, a Newsreader facts line, and that lead's own tagline as a
  pull-quote — top-right on a wide window, matching the web's own 900px
  breakpoint exactly (hidden below it, not kept visible, on a phone). The
  hero itself is never a tap target, on any of the six; only the quote's
  own credit opens the lead it names, the same as the web. Replaces the
  smaller, hard-edged hero these pages drew before, and the separate
  pull-quote block Movies, Series and Tutorials each drew under it — the
  quote is part of the hero now, so `PullQuote` is gone. A department's own
  name — one line always, "Documentaries" included — shrinks to fit its
  column rather than wrapping or truncating; a franchise's own name, never
  chosen to fit the way a department's is, wraps across up to three lines
  instead.
- A department tab bleeds its own hero under the departments bar the same
  way Home's cover does — the bar starts translucent and settles solid as
  the tab's own list scrolls the hero's bottom edge past the bar's, driven
  by that tab's own scroll position rather than Home's. A department with
  no lead art (or Solid artwork mode) keeps its bar solid from the top, the
  same as before.
- Every screen's own page ground moves from `colorScheme.surface`
  (`#151517` dark, `#FBF8F2` light) to `colorScheme.background` (`#0D0D0E`
  dark, `#F4F0E8` light) — the web's own `--paper`, which `surface` sat one
  step above. A department hero's own art already faded toward
  `background`; the page around it had not caught up to drawing on that
  colour until now, which is what the hard edge at the art's own bottom
  was.
- Collections gained the same kind of hero the four departments already
  have, crediting a franchise's own lead film, and its own franchise row
  and lists now scroll together as one page rather than a fixed column
  that could clip "＋ New list" off the bottom of a phone screen.

## 0.72.0 — Documentaries get their own place on Android

**Added**

- `Kind.DOCUMENTARY` for the index's own `docu` kind, which the Android app
  had been filing under Movies since documentaries themselves shipped
  (0.63.0) — a library's film count and film wall previously folded
  documentaries in with films for exactly that reason. A documentary plays
  and resumes like a film; a folder of them groups by show the way a
  course groups by folder, and anything uploaded on its own stays a plain,
  standalone title, the same split the web's own `groupDocumentaries` makes.
- A Documentaries tab, between Series and Tutorials, the same place the web
  player's own bar has carried it since 0.63.0 — the one department pill
  that is never hidden, reading "0" rather than dropping out of the bar for
  a library that holds none yet.
- The Documentaries department page: a hero ("Only in your library" ·
  "Documentaries" · a spelled count, not itself a link), Continue watching,
  Recently added, one row per folder ("All N" on the row's own heading opens
  it, the same course-style page a folder of lessons already opens), and a
  Standalone documentaries row for the rest — a Compose port of the web's
  own `renderDocumentariesDept`. Every plate here plays on tap rather than
  opening a title page, matching the web: nothing about a documentary comes
  from a provider, so there is no synopsis or cast worth a stop before
  playing it. An empty Documentaries library — every library, until someone
  uploads one — shows the same upload hint the web's own empty state does,
  on the phone and on television alike.
- Documentaries in search, between Series and Tutorials in the filter pills,
  the same order the web's own pills carry them in. A search hit had quietly
  stopped finding any documentary once `docu` moved off `Kind.MOVIE` here;
  it has its own group again now, playing on tap the same as an episode or
  a lesson row does.

**Changed**

- A film's Similar row and franchise link (`filmsOf`) now read the Movies
  shelf alone rather than every shelf's own films — what keeps a
  documentary, shelved on its own now, from turning up as "similar" to an
  unrelated film.
- A documentary folder gets its own collection key, distinct from a course
  or a show that happens to share its name — the two no longer resolve to
  whichever shelf is searched first.
- `runFor` (what a title not opened from a list or a search result plays
  into) now finds the collection that actually holds the set, not just the
  first one with a matching name — a documentary in a folder plays on
  through the rest of the folder, the same as a lesson or an episode does,
  and two collections that happen to share a name (a course and a
  documentary folder, or two shows) no longer hand a title the wrong one's
  run.
- The chosen tab is kept by its name rather than its plain position for the
  rest of a session — a profile switch, or a rail tap made while the
  library is still loading, now lands on the same tab regardless of
  whether a department shifted everything after it by one in between.

## 0.71.1 — the Android home tab, in the web player's own layout

**Changed**

- The phone/tablet home tab is rebuilt to the web player's own magazine
  front section, section for section: a cover story rotating through the
  day's featured films under the chrome (uppercase Fraunces headline at the
  web's own weight and optical size, eyebrow, deck, meta line, and three
  pills — Watch now plays the film, + My List toggles and reads back "✓ My
  List", Details opens its page), then the three editorial features (a
  wide lead plus two beside it on a tablet, one column on a phone; the
  second card alone keeps its own case rather than running uppercase),
  Continue Watching beside a pull-quote in one band, Recently Added
  (posters alone, no caption) beside This month's own numbered column,
  Latest series with its own captions, and Latest courses as a plain list
  — an index rather than posters, since a course carries no artwork of its
  own. Replaces the plain poster grid the tab drew before.
- A collection's own caption ("21 episodes · three seasons") now spells
  counts under twenty-one as words the way the web's own captions do,
  rather than printing every count as a figure.
- The tab's own scroll position is what the departments bar reads to
  decide how solid to draw itself over the cover, in place of a
  viewport-percentage heuristic an earlier build used, which stopped
  tracking the cover's real height once the cover stopped being a fixed
  aspect ratio.
- Fraunces across the whole app — not just the phone/tablet catalogue — now
  draws through static, pre-instanced font files rather than the variable
  font's own axis settings, which a device was found to ignore outright
  away from the font's own heaviest default instance. The shared design
  system carries this, so the television's own shelf headings and title
  names change weight the same way the phone's do.

## 0.71.0 — Android library chrome, in the web player's own layout

**Changed**

- The phone/tablet library's Material top app bar and serif tab row are
  gone. A tablet held wide gets the web's own left rail — wordmark, My List
  and Continue watching with their counts, Latest, Genres, Settings, System,
  then the library's own tally — beside a Geist pill bar (Home, Movies,
  Series, Tutorials, Collections, each with a count) laid over the Home
  tab's content; the bar starts translucent over the cover and settles
  toward solid as Home scrolls, and sits flush and solid above every other
  department. A phone or a narrower tablet gets the web's own ≤900px shape
  instead: the wordmark and icon-only rail row, a scrolling department pill
  row, then a search field and the avatar — the whole header hiding on
  scroll down and returning on scroll up, since a fixed three-row header
  would eat a phone screen the way the web's own static one never has to.
- The overflow ⋮ beside the departments bar now holds only the three
  actions the web has no counterpart for at all — Update library, TMDB
  key…, Start over — since every other destination it used to carry (My
  List, Continue watching, Latest, Genres, Settings, System) now has its
  own control right there in the rail or the header. A pushed page (a
  title, a genre, Latest opened this way) keeps its own back bar; on a
  tablet held wide the rail joins beside it and its own ⋮ trims to the
  same three actions, since the rail beside it now carries the rest — a
  phone or narrower tablet keeps the full menu there, with no rail to
  carry them instead. Settings and System still render without either, on
  every width.
- The rail's icons are the web's own line-drawn marks, traced into vector
  drawables at the same 1.4 stroke rather than redrawn from a different
  icon set.

## 0.70.2 — the one watch-state fake follows the core's rules

**Internal**

- `FakeCore`'s progress, watched marks, watchlist, Kids, editor's choice and
  collection stubs (`core:testing`) are no longer no-ops: a new
  `FakeWatchState` (its own file, so `FakeCore.kt` keeps its line budget)
  keeps them per profile, against a test-controlled clock that defaults to a
  plain monotonic count. `CoreContract` gains one case per rule — progress
  upsert and its non-negative clamp, newest-first order, finishing always
  re-stamping and clearing the position even on a repeat mark, taking a mark
  back leaving the position alone, the watchlist and Kids both idempotent
  and tombstoned, Kids shared across every profile, the editor's choice
  keeping one live pick, a list's rename/delete/membership refused off its
  owning profile — run against the fake now (`core:testing`'s unit test) and
  compiled for the real core's device run (`core:rust`'s `androidTest`) next
  phase. `WatchStateRepositoryTest` (`core:data`) drops its own hand-written
  `StateCore` for this one fake; two of its nine cases now check the actual
  written state on a second profile instead of a call-log string, which is a
  stronger proof of the same claim they always made.
- No behaviour change: this is test infrastructure only.

## 0.70.1 — a watched test that failed on a fast run

**Fixed**

- The web's "a finished show says when" test allowed the re-marked stamp one
  millisecond past the clock, but the test before it marks and un-marks the
  same title, and each of those clamps a millisecond forward — so when all
  three land in one millisecond the stamp is two ahead. The bound is now two,
  with the reason beside it; nothing in the state rules changed. It had
  started failing the pre-push run intermittently.

## 0.70.0 — more than one upload at a time

**Added**

- `upload_slots` (config, default 1): how many uploads may run at once
  across processes. Telegram limits upload speed per connection, not per
  account: measured with one film uploading beside the normal queue, two
  uploads together moved 26–31 MB/s against 12–13 MB/s for one, with no
  flood waits. Each slot is its own lock file; slot 0 is the historic
  `upload.lock`, so an older binary still running shares it and the total
  never exceeds the setting (`upload/lock.rs`).

**Fixed**

- Opening the Telegram session no longer fails when another process has the
  session file locked for a moment ("database is locked"); it waits and
  tries again, for up to about a minute (`telegram/client.rs`).
- `mediagram status` showed one upload with two running: both wrote their
  progress to one shared file, overwriting each other, and the first to
  finish deleted it. Each upload now keeps `upload-progress-<set>.json`,
  `status` reads them all (and the old shared file an older process may
  still write), and counts uploads as running while any slot is held.
- A part upload that Telegram rate-limits at the transport level (`bad
  status (negative length -429)`) is now waited out — 30 s, doubling to ten
  minutes, on a budget of its own — instead of being retried within three
  seconds and ending the whole run. Two seasons had stopped on it the hour
  two upload slots first ran (`upload/transport.rs`, `telegram/retry.rs`).

## 0.69.4 — closing the Settings/System redesign

**Fixed**

- Switching Settings sections on phone and tablet (Telegram → Appearance →
  Storage, and so on) could open the new section already scrolled to
  wherever the previous one had been left, instead of at its own top — the
  same bug the television's own Settings pane had already been fixed for.
  Each section now keeps its own scroll position again.

**Changed**

- `DESIGN.md` and `docs/system-architecture.md` now record the shipped
  Settings/System redesign: the Artwork picker's four modes, Settings'
  two-pane index and its lack of a top app bar, and where television's own
  Appearance answers fewer of the four questions than phone and tablet do.
  No other behaviour changed in this release.

## 0.69.3 — the television's Settings, in the same look

**Changed**

- The television gets the same Settings/System index the phone and tablet
  already have: a focusable column on the left — Telegram, Storage,
  Appearance, System, each with its own one-line status — beside the open
  section on the right, at ten-foot type and overscan-safe spacing. Moving
  the remote along the column only shows a section; Right or OK actually
  steps into it, onto its own first control. Left inside a row of controls —
  the accent swatches, the artwork cards — moves along that row first, only
  reaching the index once nothing is left of it; Up or Down at a section's
  own top or bottom edge stays put rather than falling through to the index
  above or below. Back from anywhere inside still comes straight back to the
  row that opened it, and each section opens scrolled to its own top. The
  overflow menu's System shortcut still opens straight to that row.
- Appearance gained the same Artwork question (Default/Blurred/Artwork/Solid)
  the phone and tablet ask, beside the accent swatches already there; a
  television still never asks Theme, since it stays dark regardless. The
  chosen accent and the chosen artwork now wear a light ring even when the
  remote is elsewhere — before, only a screen reader could tell which one
  was current.
- System re-reads every two seconds while its section stays the one shown,
  matching the web player's own poll and the phone/tablet's own release.
- The old single-scroll Settings and System screens, and their plain
  label/value rows, are gone — replaced by the same ledger look (a quiet
  label, its value, a soft rule beneath) the phone and tablet already draw.
- On the tablet, Settings' columns get room to breathe: the index is 280dp
  rather than the mockups' 320dp, the page margins are sized to the
  ≈1160dp a tablet actually has, and a section's columns sit side by side
  only while each keeps at least 320dp — the rest move to a second row
  instead of every column squeezing narrower.
- Storage's home cache server block reads the server's own `GET /v1/status`:
  what it holds against its budget ("1.0 MB of 10 MB (10%)") and how many
  chunks, on every surface that shows the block — the television's own copy
  included, which before this only ever read the connection line.

## 0.69.2 — Android Settings and System, in the web player's look

**Changed**

- Settings and System are one screen now, not two: on a phone, an index of
  four rows — Telegram, Storage, Appearance, System, each with its own
  one-line status — opens the one asked for, with Back returning to the
  index; on a tablet held wide, the index sits beside the open page the
  whole time, Telegram selected by default. The overflow menu's System
  shortcut still opens System directly, and Back from it leaves straight
  back to what was on screen, since it was asked for directly rather than
  found through the index.
- Every row and page now draws in the web player's own settings look: a
  huge uppercase title over a small tracked-caps line, quiet ledgers for
  facts, outlined pills for actions, and one soft-cornered "ledger" table in
  place of the plain label/value rows Settings and System used before.
- Appearance gained the web's Artwork picker (Default/Blurred/Artwork/Solid)
  — modelled and rendered since 0.69.1, now with a way to choose it — beside
  round accent swatches and theme cards drawn the same way.
- System re-reads every two seconds while its page stays open, the same
  interval the web player's own status panel polls at, rather than only
  once per visit.
- Television's own Settings/System follows in a later release.

## 0.69.1 — Android pages follow the web's Artwork setting

**Added**

- The web's fourth Appearance question — Artwork: Default, Blurred, Artwork,
  Solid — now has an Android answer, held per device beside theme and accent
  in the same preferences file. Blurred softens a hero's picture to colour
  and light (a real blur from API 31; a tiny decode upscaled by the crop
  below it, everywhere older); Solid drops the picture and its tagline quote
  from the title spread and a department's own hero, leaving the words on a
  plain page; the magazine home's cover story keeps its picture in every
  mode but Blurred, matching the web's own `.cover-stage`, which Solid never
  touches either. Artwork reads the same as Default on Android by design:
  the web's Artwork rule only changes its wide two-column spread, which
  Android does not have yet — the phone's spread is already the web's own
  narrow layout at every width, so there is nothing for the rule to move.
- Not yet: a way to choose it. The model, persistence and every hero's
  rendering are in place; the Settings picker that writes the choice is
  separate, later work.

## 0.69.0 — the Android app takes the web player's colours and type

**Changed**

- Every screen's dark palette is now the web player's own dark theme
  (`styles/theme.css`'s `:root`), not a warmer near-black tuned separately
  for the phone: pages read darker and cooler than before, most visibly on
  the catalog wall's ground and app bar. Two web tones without an Android
  equivalent, sidebar and rule-soft, are now carried too, for a settings rail
  still to come.
- Counts, labels, captions and other interface text — everywhere that isn't a
  title or a whole sentence — is now set in Geist, the web's own interface
  face, in place of Newsreader. Sentence-length text (loading, empty and
  failure messages) stays in Newsreader.
- A control — a button, an input, a dialog — now takes a 6dp corner, the
  web's own radius, through Material's `Shapes`. A plate is deliberately not
  a control and keeps its square corner.
- Not changed yet: the Settings and System screens' own layout, the ported
  Artwork setting, and plate corners, which stay square while the rest of
  the system moves to a soft 6dp — each is separate, later work.

## 0.68.15 — the web player loads into the page it is loading

**Fixed**

- Opening the web player showed "Loading your library…" over a row of grey
  poster boxes — the shelf layout the home page no longer has — and then the
  magazine home replaced it: one layout, then another. The loading state is
  now shaped like the cover story the first page opens with (the same dark,
  full-bleed block, the words where its title will be), and the page starts
  with the cover's dark-glass masthead, so the home fades in over the block it
  was waiting in. The first draw clears the cover look for any other page.
  The masthead also gets the cover's opening colours when its scroll timeline
  is idle, which on the one-screen loading state left black links on the dark
  cover.

## 0.68.14 — the Android catalog asks its core once per question

**Changed**

- The catalog listing (`Core::list_sets`) now carries every set's poster,
  backdrop and (for an episode) season poster already resolved to a path on
  disk — matching what the web player's `/api/sets` has always carried for a
  poster and a season poster. A backdrop is a known, pre-existing exception:
  it resolves disk-only on Android, where the web also counts one the
  `artwork` table alone carries (daBOB/mediagram#1 tracks closing that gap).
  `CatalogRepository` used to make one further `posterPath` crossing per set
  per artwork field to get there (poster, backdrop, and per season plate on
  the TV surface); it now reads the resolved fields straight off the record.
  A title's credits, a person page and search-by-name results carry their
  portraits the same way, so a cast list or a page of people costs the one
  crossing that fetched it, not one more per name.
- A new `Core::media_set(setId)` answers one set by id directly
  (`catalog::playable_set`'s own indexed lookup), replacing
  `CatalogRepository.mediaSet`'s former "list everything, then find it" —
  the player's five `mediaSet` calls opening one episode measured
  107–251 ms each against a ~1,160-set catalog before this; the id is now
  looked up without listing, enriching or crossing the boundary for every
  other set in the library.
- On a listing, the index's `artwork` table (custom poster/backdrop bytes,
  see §10.1) is read once into the set of keys it actually holds, rather
  than queried per set with a miss — a key the table does not hold is never
  queried about at all, not just once a snapshot with no table at all is
  ruled out.

**Internal**

- `Core::poster_path`, the per-key FFI method, is gone — nothing on the
  Kotlin side still resolves a key to a path itself, now that a listing and
  a one-set lookup both carry resolved artwork and credits carry resolved
  portraits. The internal resolver it wrapped survives as `store::poster_path`,
  still used for a single portrait fetch download; a title's credits, a
  person page and search-by-name results resolve their portraits through
  the batched resolver instead (`store::resolve_with`, reusing the
  connection and the artwork-key set that call already read), rather than
  reopening the index per name the way `poster_path` would on a miss.
- `SetSummary.backdrop_key`/credit records' `portrait_key` are gone,
  replaced by `backdrop_path`/`portrait_path` carrying the resolved value
  directly — nothing needed the raw key once nothing resolves it separately.
- TV season plates and the mobile `CollectionScreen` no longer take a
  `posterPath` lookup of their own; `SeasonPlate.posterPath` reads straight
  off the episodes the core already resolved it for.
- Artwork materialised from the `artwork` table is now written beside its
  final name and renamed into place, not written to the name directly — a
  listing and a `mediaSet` call can now resolve the same key at the same
  time, each against its own connection, and a reader must never see a
  half-written file at the name it is about to open.

## 0.68.13 — the Android app speaks to its core through the generated interface

**Internal**

- `CoreClient` and `DefaultCoreClient` (android/core/data) are gone. Every
  ViewModel and repository above `core:data` now reaches the generated core
  through its own `CoreInterface` directly; the 7 call sites where it uses
  unsigned integers (`refreshLibrary`, `refreshCatalog`, `totalSize`, `read`,
  `fetchMissing`, `person`, `fetchPortrait`) convert at the call site instead
  of through a wrapper. The close fence — retire local state, then release
  the native handle, once, with a failed retirement left open for retry —
  moved into `CoreProvider`, which already owned every production close;
  `StoredCoreProvider` is now generic over a type that is both `CoreInterface`
  and `AutoCloseable`, since the generated interface itself has no `close()`.
- A new module, `core:testing`, holds the one fake of `CoreInterface` every
  other module's tests now build on (`FakeCore`, plus `FakeCoreProvider` and
  two same-shaped aliases, `ResolvedCoreProvider`/`CatalogCoreProvider`),
  replacing six separate per-module fakes and five `mockk<CoreClient>`
  doubles. `FakeCore` keeps the real contract where tests touch it — `NotFound`
  reading past a set's end or an unknown set, the same for `totalSize`,
  `revokeSession("0")` refused — checked by a shared contract suite run once
  against the fake (`core:testing`'s own unit test) and once against the real
  generated `Core` on a device (`core:rust`'s `androidTest`, next to
  `CoreLoadsTest`).
- No behaviour change: this is the seam the app talks to the core through,
  not what either side of it does.

## 0.68.12 — Android preloads what the web player preloads

**Fixed**

- The Android player preloaded episodes the web player never would. Played
  from a hand-built list or the Kids wall, it walked the list and took the
  next two episodes it found; and it walked past anything that was not an
  episode to find two. The web player — the reference — preloads only while
  an episode plays through its own show, only the next two positions, and
  only those that are episodes, because the preload fetches in the
  background from a flood-limited account. Android now keeps the same rule
  (`PlayerViewModelPreload.kt`, after `playsNext`).
- Behind it: an up-next switch stored a show's own run on the player frame
  as if the viewer had picked it by hand, so "played from a list" could not
  be told apart after the first episode. `LibraryPositions.replacePlayer` now
  keeps a run only when the frame already had one.

## 0.68.11 — what plays next and what is preloaded are one answer

**Internal**

- The web player's choice of what plays after a title, and of what the
  server is asked to preload while it plays, moved out of `app.js` into
  `web/public/lib/playback/plays-next.js` as one pure function, `playsNext`.
  The rule it keeps is unchanged: the next two series episodes, and nothing
  for a lesson, a documentary or a hand-built list, because the preload
  fetches in the background from a flood-limited account. The browser half of
  that rule had no test before; `web/test/plays-next.test.ts` now pins it,
  beside the "next" it must always agree with. `app.js` is 30 lines shorter,
  and its line ceiling came down with it.

## 0.68.10 — the web player's addresses have one home

**Internal**

- The hash address format — `parse`, `href`, `go` — moved into one module,
  `web/public/lib/address.js` (+ `address.d.ts`). `drawRoute` (`app.js`) now
  dispatches on a typed address instead of splitting `location.hash` by hand,
  and every card, crumb, pager and `location.hash =` that used to build a
  `#/…` string itself (about 47 sites across 16 files) now calls `href` or
  `go`. The three separate show-openers in `app.js` are one function,
  `openShow`, now. The format itself is unchanged — every address the app
  accepted before still parses to the same page, an already-bookmarked link
  still opens where it always did — this only gives the format one owner.
  `docs/web-player.md`'s address table was also wrong in three places (search
  took a query string rather than a path segment; a list's own route was
  listed twice, once wrongly; a season's route was described as an episode's)
  and missing four real pages (`#/documentaries`, `#/continue`, `#/watchlist`,
  `#/system`); corrected, and `web/test/address.test.ts` now parses every row
  in it, so the table cannot drift from the code again. Its two Movies rows
  were wrong too: `#/movies` is the department's front page, and the paged
  shelf is always numbered from `#/movies/page/1`.
- One address opens something different: a section named after a built-in
  object property (`#/constructor`, `#/toString`) used to pass for a real
  section and then fail to draw, leaving a blank page. It now opens Movies,
  as every other unknown section does.
- `pager.js` lost `parsePage`, which `address.js` now does alone, and the
  `section` argument its links never used.

## 0.68.9 — a conversion no longer pulls the whole film at once

**Fixed**

- A title converted in copy mode ran ffmpeg at 7.4x real time, reading its
  input through the player's own stream route as fast as the Telegram link
  allowed: about 700 `upload.getFile` requests in 73s on one measured title,
  enough to trip 9 flood waits. Playback itself was unaffected — the decoder
  stayed far ahead — but a seek meanwhile queued behind those flood-slept
  requests, and the conversion fetched the whole film for a viewer who might
  stop after ten minutes. Conversions now read at up to twice real time after
  a 30-second burst (so start-up and a seek's restart are as quick as
  before), on ffmpeg 6.1 or newer; an older ffmpeg is detected once at
  startup and left unpaced rather than have every conversion fail on an
  option it does not recognise.

## 0.68.8 — the System page counts real reconnects only

**Fixed**

- The System page's Reconnects counter climbed by about one every 9 seconds
  while the player sat idle, with nothing actually reconnecting — the main
  connection's socket never closed or redialed the whole time. Cause:
  `teleproto`'s keepalive loop treats any gap since the last pong of 5s or
  more as "just woke from sleep" and announces a fresh connection on success,
  but the loop itself only ever pings every 9s, so that gap was always past
  the threshold and the "woke from sleep" branch fired on every ordinary
  ping. Patched the vendored dependency (`web/patches/teleproto@1.229.0.patch`)
  to raise that threshold above the ping interval, so it only fires on an
  actual gap — a suspended laptop, a backgrounded tab. No connection was ever
  dropped; only the count was wrong.

## 0.68.7 — an uploaded title arrives with its cast

**Fixed**

- Uploading a film or a show recorded its description but not its cast or
  a film's franchise; those came only with the next `mediagram metadata` or
  `sync-index`, so titles uploaded since the last run showed no Cast tab
  (nine series added one evening, for instance). The upload now records
  cast and franchise with the description, once per title, the way
  `metadata` does; a provider that will not answer costs the tab, never the
  upload. Titles already uploaded without cast get it from the next
  `mediagram metadata`.

## 0.68.6 — finishing a title is one write, on the web and on Android

Architecture review candidate E.

**Fixed**

- Finishing a title (the credits rolling, or "mark watched") was two writes:
  the position deleted, then the completion recorded. A second write that
  never landed — a dropped request on the web, the Android app killed in
  between — left the position gone with no completion, and the completion is
  the only thing that beats another device's older copy of that position, so
  the next sync brought the finished title back onto Continue. Marking a
  title watched now clears its position in the same transaction, on the web
  player's server as in the Android core (which gains an explicit
  transaction), and both players send the one write. Taking the mark back
  still leaves a position alone.

## 0.68.5 — the web player's library session has one home

Architecture review candidate D.

**Internal**

- The catalog a profile sees, and keeping it current, moved out of
  `web/public/app.js` into `web/public/lib/library-session.js`: the fetch and
  diff of `/api/sets`, the kids filter, grouping into shelves, the coalesced
  `/api/events` stream (open only while the tab is visible), and holding a
  redraw back while the player or a list picker is open. `app.js` keeps
  rendering, DOM counts, the colophon and routing, reached through the new
  module's `onData`/`onRedraw` notifications and a `hold()`/`release()` pair
  the player and list editing each take one of. The ordering bugs of the last
  week (coalescing, the wait-until-close redraw, one event stream per visible
  tab) all lived in `app.js`'s shared state between unrelated concerns, which
  is what this module now contains on its own. A few behaviours did change
  along the way: becoming visible again now also refreshes the editor's
  choice, not just positions; a title finishing while it plays updates its
  shelf's count at once instead of waiting for the shelf itself; and startup
  no longer asks `/api/player` twice. A `drawn()` on the module settles a
  redraw the player or list-editing hold owed the moment `app.js` draws it
  anyway, so a hold releasing afterwards does not draw the page a second time.
  Behind a small browser port (`library-session-port.js`), so the module's own
  tests (`web/test/library-session.test.ts`) run without a DOM.

## 0.68.4 — course lessons named with an outline number

**Fixed**

- `add-course` read `6.10 – Deep stage` as number 6, so every lesson in a
  chapter named `6.1`, `6.2`, … `6.10` collided, was renumbered in text order
  (`6.1, 6.10, 6.2`) and kept "10 –" in its title. A dotted prefix now counts
  by its last segment (lesson 10, titled "Deep stage"), and a dash or colon
  after the number is dropped from the title (`media/file_names.rs`).
  Courses already uploaded under such names (Wall Street Story) have their
  lessons stored under the old numbers: re-running `add-course` on them
  would compute different identities, so re-running is not safe for them.
- `add-show` filed a double episode (`S09E19E20`) as E19 alone, so `status`
  reported E20 missing. It passes each episode's number explicitly, and an
  explicit number dropped the range the file name gave. A number equal to
  the name's own start episode now keeps the range
  (`metadata/episode_value.rs`).
- `prepare --mp4` failed on every file carrying a Blu-ray (PGS) or DVD
  subtitle: an mp4 holds subtitles only as text, and ffmpeg refused the
  whole file ("Error opening output files: Invalid argument"). Picture-based
  subtitle tracks are now left out of an mp4; text ones are still converted
  (`media/prepare/plan.rs`).
- `prepare` rejected a correct result as `MissingLanguage("eng")` when the
  video track was tagged `eng` beside German-only audio: the languages the
  result must keep were read from every kept track, picture included. They
  now come from the kept audio alone (`PreparePlan::expected_audio_languages`).
- `prepare --mp4` also failed on a Matroska attachment — an embedded font,
  or a release's `.nfo` — or a data stream: an mp4 cannot carry them and
  ffmpeg refused the whole file. They are now left out of an mp4 too;
  chapters are not streams and still travel (`media/prepare/plan.rs`).

## 0.68.3 — a set completes in one transaction

Architecture review candidate C. Review:
`plans/reports/code-reviewer-260927-0345-set-lifecycle-review-report.md`.

**Fixed**

- Completing a set was three writes with three failure policies: the set
  marked complete, then its remux forgotten (a failure ignored), then its
  source forgotten (a failure fatal, after the set was already complete).
  It is now one transaction that also records the publish the set is owed,
  so a set is either pending with a source to resume from, or complete with
  nothing left behind and a publish owed; a failed completion leaves it
  pending and resumable.
- `rescan` completing a set it finds whole in the channel now forgets the
  source an interrupted upload recorded, so no stale path stays in the
  index (and in every snapshot pushed from it).
- A set removed while it was uploading is no longer reported "added" when
  its last part lands, and its source file is not deleted as if it were.
- A remux that cannot be deleted is named in the warning, to delete by hand:
  it sits beside the original when no temp directory is configured.

**Internal**

- `index::lifecycle` is the one module that spells a set's `source:` and
  `tmp:` meta keys (spellings unchanged, and pinned by a test, since
  existing indexes resume through them); planning, uploading, rescan, the
  upload session and `remove` go through it. `run_set` reports whether the
  set completed, so the upload session no longer re-reads the status, and
  the `finish_from` pass-through is gone.

## 0.68.2 — the series preload stops tripping Telegram's flood limit

What the viewer saw: `[INFO] Sleeping for Ns on flood wait (Caused by
upload.GetFile)` every few seconds while a show or tutorial episode played,
with the background preload of the next two episodes as the likely cause of
occasional stalls (`plans/reports/debugger-260927-0310-web-getfile-flood-wait-report.md`).
The preload's whole-episode download shared the same 4-slot `DownloadGate`
as playback but held a slot continuously and issued requests back-to-back,
fast enough on its own to trip the limit even with no other reader active.

What changed: the gate now has a `background` lane (`telegram/download-gate.ts`)
that never starts fresh, and never holds a freed slot, while any foreground
read (playback, readahead, the audio-track probe, transcode) is running or
queued — the series preload (`cache/series-preload.ts`) runs in that lane
(`telegram/part-fetch.ts`'s `backgroundFetcher`). The preload also paces
itself to one 512 KiB request per second, regardless of the gate, so it
cannot flood Telegram by itself either.

## 0.68.1 — a completed set is owed a publish the moment it lands

Review follow-up to 0.68.0
(`plans/reports/code-reviewer-260927-0325-upload-session-review-report.md`);
the entries below under 0.68.0 describe the result.

## 0.68.0 — one upload session behind every uploading command

Plan: `plans/260927-0302-upload-session-module/` (architecture review
candidate A). Terms: `CONTEXT.md` (Upload session).

**Changed**

- `add-show`, `add-course`, `add-docu`, `finish-set` (behind `add`) and
  `resume` run through one upload session. Each item takes the upload lock
  on its own and re-reads what the index holds once it has it, so a file
  added meanwhile goes between two episodes, and a background `add` whose
  set `resume` finished first finds it complete (and deletes its file when
  asked) instead of failing.
- A failure while sending, after the transport's own retries, stops the
  session: the rest of the walk is not tried (each would fail the same way),
  what completed is published, and the command says how many items were not
  reached. A missing or changed source still blocks only its own set.
- An item counts as uploaded only when its set completed; `add-docu` on a
  single file no longer publishes when its set did not complete.
- A session publishes once at its end, over the connection it uploaded with.
  Every set it completes is owed a publish the moment it lands, recorded in
  the local index, so a walk interrupted after that (Ctrl-C mid-show) still
  leaves the publish owed, and the next session pays it even with nothing of
  its own to upload. It skips the publish while another upload is running
  (one pin instead of two); the debt stays until a publish settles it.
- Output: `resume` says "set X added" for each set it completes, as every
  other upload does, and "already finished by another upload" for one it
  finds done; `finish-set` says "set X added" only once the set completed;
  `add-docu FILE` names the file before planning it rather than the resolved
  title after. `resume` also fails when a set could not be resumed at all.

**Internal**

- `upload/session/` replaces `Uploader`, `finish_with`, `finish_one` and
  `upload::resume::pending`; `course::upload` is the one walk `add-course`
  and `add-docu` share. Tests: `tests/upload_session.rs`, and the source
  checks in `tests/upload_finish.rs`, through the session with a fake link.

## 0.67.1 — re-running `add-docu` on a folder no longer uploads it again

**Fixed**

- `add-docu` on a folder records its episodes as `docu` but looked them up as
  course lessons (`tut`), so a re-run never saw what it had uploaded and sent
  the whole collection again as duplicate sets. The lookup now takes the kind
  the walk records (`set_lookup::lesson_status`).

## 0.67.0 — one module pulls and publishes the channel index

Plan: `plans/260927-0146-channel-index-module/` (architecture review
candidate B). Terms: `CONTEXT.md`.

**Changed (breaking)**

- `push-index` always pulls the channel index first, as every publish after
  an upload already did. `--merge` is gone (it is the default now), and so is
  `--check`: `pull-index --dry-run` reports what the channel holds that this
  index lacks, naming the first few sets. `--force` still replaces the channel index without pulling.

**Fixed**

- The uploader chose "the channel index" differently from the players: it
  read only the pins, so a snapshot an interrupted publish left unpinned was
  invisible to it though every player showed it, and it took a member's
  pinned post and a snapshot dated in the future at face value. It now reads
  the pins and the marker search like the players, and chooses with the rule
  that now lives in `mlib-spec` (`index_caption::newest`), which core
  delegates to and the web player's fixtures check.
- A publish landing from another machine while this one was under way was
  refused; it is now pulled in and the publish goes ahead. One that keeps
  landing (three times) fails the publish, sending nothing.
- Two background uploads on one machine could publish at the same moment.
  Publishing now takes a `publish.lock` beside `upload.lock`.
- A publish made two Telegram connections and downloaded the channel index
  twice. It now makes one, and downloads nothing when the channel index is
  the one this machine last pulled or published. A pull that fails, or that
  leaves a conflict unresolved, does not count, so the next publish pulls
  again.

**Internal**

- `crates/mediagram/src/channel_index/` replaces `commands/pull_index`'s
  helpers and `telegram/{index_publish,index_guard,download_index,unpin}`.
  The channel sits behind a `ChannelRemote` port with a Telegram adapter and
  an in-memory one; `tests/channel_index.rs` covers the round trip, including
  the refused and the silently ignored unpin.

## 0.66.4 — transcoding fits pictures larger than UHD

**Fixed**

- A title larger than UHD (screen recordings at sizes like 4784x2464) could
  not be converted for the browser: VAAPI refuses anything over 4096 wide,
  and a frame that size is past every H.264 level. The transcode now scales
  such a picture down to fit inside 3840x2160, keeping its shape, before
  encoding (and before the VAAPI upload); anything smaller passes through
  untouched (`web/src/transcode/args.ts`).

## 0.66.1 — the web player turns its pages cleanly

**Fixed**

- Going to another page from partway down one could blank the screen for a
  moment before the new page snapped in. The page turn was a view transition,
  and the scroll offset moving under it (clamped to a shorter page) drew its
  snapshots out of place. It also lifted the page above the masthead and rail
  while it ran. The new page now rises in on a plain CSS animation of `main`
  (`web/public/lib/page-turn.js`).
- A page gone to opened at the offset of the page left, clamped to its
  length. It now opens at its top, and Back or Forward returns to where the
  page was left.
- A vertical stripe crossed the backdrop of a film page and a department
  front page while its art faded in: the art's entrance zoom reached past its
  box, outside the paper gradient over it. The art box now clips it.

## 0.66.0 — the editorial departments on Android, phone and television

Pays what 0.62.0 left "Owed to Android". Plan:
`plans/260926-1330-android-editorial-departments-parity/`; TV review and device walk:
`plans/reports/code-reviewer-260926-1835-tv-editorial-parity-review-report.md`.

**Added**

- Core read API for credits, people, franchises and lazily fetched portraits (a person's
  portrait is fetched the first time a Cast row or person page shows them, then cached).
- Phone and television: department pages (Movies, Series, Tutorials) with a hero, film pages
  with Overview/Cast/Similar/Details tabs and "Part of <franchise>", series pages with
  Episodes/About/Cast/Similar, person and franchise pages, Collections with franchises and
  lists, grouped search (Films, Series, Tutorials, People, Collections, with filters), Latest
  and Genres. The television's Home takes the web's magazine layout.
- Settings › Appearance: theme and the seven accents on the phone; the television stays dark
  and takes the accent only (user decision).

**Fixed (television, found in review and on the real box)**

- Every build crashed at launch: a view model declared in `ui-tv`, which runs no Hilt
  processor. The television now uses `feature:catalog`'s own.
- Back returns to what was opened — a search result below the fold, a show, person or
  collection from search, a cast member or similar title (with its tab) — and department
  pages no longer take the remote from Search or the Menu.
- Rows show every title instead of six; the cast row scrolls; Home, Movies and Series arrive
  on their hero inside the overscan margin; tab labels stay readable when the row has focus;
  Collections insets its lists once; long rows compose lazily for the box's CPU.

## 0.65.1 — sync-index no longer stops before its push

**Fixed**

- `sync-index` failed at step 4 with "…library.before-channel-merge-….db
  already exists; not overwriting a backup". It pulls twice in one process,
  usually within one minute, and both pulls chose the same backup name. A
  second backup now takes the next free `-2`, `-3`… name, and the first is
  still never overwritten (`pull_index/backup_path.rs`).

## 0.65.0 — sync-index, merge-first publishing, progress lines

**Changed**

- Every publish after an upload (`add`, `add-show`, `add-course`, `add-docu`,
  `resume`) now pulls the channel's index in first, then pushes
  (`pull_index::merge_and_publish`). Before, a push from the other machine
  in the meantime got the publish refused ("the channel's index holds N set(s)
  this index does not"), which is why uploads ran on one machine at a time. Two
  machines on 0.65+ can now upload at once. A push landing between the pull and
  the publish is still refused by the guard, never dropped. Keep the two
  machines on different folders, since nothing detects the same file uploaded
  on both.
- The refusal message now suggests `push-index --merge` first, then `--force`.
  The hint after a failed publish says `sync-index`.

**Added**

- `mediagram sync-index` pulls the channel's index, runs `metadata` and
  `posters`, then pushes, so one command does what took four. Takes `metadata`'s
  `--refresh-older-than`. A failed artwork fetch is reported and does not stop
  the push, since that art never leaves this machine.
- `metadata` and `posters` keep a progress line on the terminal: count,
  percentage and time left (`describing 451/908 (50%) · eta 2m10s`,
  `fetching 5200/8718 (60%) · eta 2s`). They draw nothing when piped. The line
  is `term::count_line`, shared by both.

## Unreleased — 0.64.0

The television surface, from `feat/android-tv-ui`.

**Added**

- The Android TV surface: `:ui-tv` renders the same `feature:*` ViewModels as the
  phone with `androidx.tv:tv-material` — setup and sign-in, "Who's watching?", the
  masthead and Home shelves, walls, title/series/season pages, the full-screen player
  on remote keys, search and genres, and the Menu's System, Settings, TMDB key and
  Start over. Back always lands on the row or card that opened a screen. See
  `docs/system-architecture.md`, "The television surface".
- TV Settings: the cache location picker ("Where"). A USB drive shows up only when
  set up as *removable* storage; Android never lists adopted (internal-format)
  storage in `getExternalCacheDirs`. Checked on the real box with a 512 GB stick.
- TV Settings: the home cache server block — status, on/off, and the address and
  pairing token each asked as a text question. `lanCacheStatusLine` moved into
  `feature:system` so the phone and the television share one sentence.
- TV Settings: active sessions, as the phone has them — this device marked, a
  second press on another session's row to revoke it.
- TV TMDB key: a blank answer is ignored rather than clearing the stored key
  (the keyboard's action key also just closes the keyboard on a remote);
  "Clear stored key" is its own row behind a confirmation.
- A `benchmark` build type: minified and not debuggable like a release, but
  debug-signed so it installs over a signed-in debug install. A debug build felt
  slow on the TV box.

## 0.63.0 — documentaries and custom artwork

Schema v10, the `artwork` table, `mediagram artwork`, `add-docu`, and the new
`Kind::Docu`. **Web:** a Documentaries department in the top bar (between
Series and Tutorials), artwork-table images served before TMDB files, tutorial
cards with their own art, and a Documentaries group in search. Unnumbered lesson
and part names sort naturally (`Teil 2` before `Teil 10`).

**Schema**

- Schema version 10 (`mlib-spec`): new `artwork` table `(key, mime, bytes)`,
  additive over v9. `READABLE_SCHEMAS=[6,7,8,9,10]`; `OLDEST_READABLE_SCHEMA`
  stays 6. Readers tolerate v9 and earlier (the table is simply absent). A
  channel merge carries missing keys the same way it already carries missing
  credits and franchises (`index::merge_artwork`).

**Added**

- **`Kind::Docu`**: a documentary recorded from a TV station, never looked up
  at any provider (a TMDB film with the documentary genre stays `Movie`). A
  standalone file is titled from its name, exactly like a movie; a file inside
  a collection folder is grouped and numbered exactly like a course lesson
  (`Kind::Tut`), sharing `add-course`'s walk, identity and dry-run machinery.
  Gains its own display code (`C02E03`), `edit --kind` target, and label.
- **`mediagram add-docu <file|dir>`**: a file uploads one documentary; a
  folder uploads a collection (e.g. "Terra X"), grouped by the folder name the
  way `add-course` groups by course title. Summaries and subtitles via the
  same sidecars a course lesson reads. `poster.*`/`backdrop.*` at a
  collection's root become its artwork. Flags: `--title`, `--cid`,
  `--dry-run`, `--no-push`, `--no-remux`, `--variant`.
- **`mediagram artwork <set-id|title> --poster <file> --backdrop <file>
  [--clear]`**: custom poster/backdrop bytes, stored in the index and
  overriding TMDB's own art when both exist. Resolves to the existing
  `tmdb-…` key when the target has a provider id, else `title-{slug}` (the
  same slug `add-course` derives a default collection id from). 1 MB cap per
  image; the uploader asks for a resize rather than storing a larger one.
  `add-show` and `add-course` pick up `poster.*`/`backdrop.*` from a folder's
  root the same way.
- `mlib_spec::package::title_art_key`: the one place a title-with-no-provider-id
  art key is derived, called by the uploader and by `mediagram-core`'s
  `poster_key_for`. `poster_key_is_valid` accepts `title-{slug}[-bg]` beside
  the existing `tmdb-…` shapes.
- Android/core `poster_path` checks the `artwork` table before falling back to
  a TMDB fetch, writing a hit into the artwork directory once.

## Unreleased — 0.62.1

- **Navigation**: every link appears once. The departments (Home, Movies, Series,
  Tutorials, Collections) live only in the top bar, which now carries the Collections
  count; the side rail keeps the viewer's shelves and utilities (My List, Continue
  watching, Latest, Genres, Settings, System).

## Unreleased — 0.62.0

Pending merge on feat/editorial-departments branch: web player department pages,
feature pages with tabs, Cast via schema v9 credits, franchises, search paging
and grouping, Settings shell, dark-first theme system.

**Schema**

- Schema version 9 (`mlib-spec`): `shows` gains `collection_id`, `collection_name`,
  `series_type`; new `credits` table (908 titles, 11,706 cast/crew/creator rows,
  portraits at 185px); new `franchises` table (202 franchises, 363 shows grouped).
  `READABLE_SCHEMAS=[6,7,8,9]`; `OLDEST_READABLE_SCHEMA` stays 6. Readers tolerate
  v8 (tables and columns optional). Both uploaders and all Android installs must
  run ≥0.62.0 before any v9 push/export; installed Android builds refuse v9 packages.

**Added**

- **Department pages** (`#/movies`, `#/series`, `#/tutorials`): headline, tagline,
  genre pills, and shelves grouped by popularity/newness. Movies shelf paged
  (`#/movies/page/N`; page 1 is plain `#/movies`), showing 48 films. Series and
  Tutorials list every held show/course at the foot. No Popular/New rows when
  count ≤ 12.
- **Feature pages** (film, series): full-bleed TMDB backdrop, tagline over
  artwork, title, runtime/network, rating, description. Tabbed interface:
  Overview (description), Cast (credits), Similar (recommendations), Details
  (metadata). Tabs keep selection and focus on every redraw. Cast tab only when
  credits exist (schema v9 required).
- **Cast** from v9 credits: cast tab lists top 12 by billing order with character
  names, circular 185px TMDB portraits (`tmdb-person-<id>.jpg`). People search
  returns cast and crew, circular cards, filtered to titles the profile can see,
  link only when the person has a detail page.
- **Person pages** (`#/person/<id>`): portrait, name, filmography split into
  Films and Shows, both filtered to profile visibility.
- **Franchises** (via `belongs_to_collection`): Collections page lists TMDB
  franchises (≥2 films held) and user-created lists. Franchise detail shows
  large cards per film with watch state. Link renders only when franchise has ≥1
  viewable film.
- **Search** grouped by type (Movies, Series, Episodes, Lessons, People,
  Collections), with type filter pills in memory (not in hash).
- **Settings page** (`#/settings`, in the rail for everyone): Appearance (theme
  Dark/Light/Auto, 7 accents, artwork mode) and Profile (who is watching, Switch
  profile); plus **Library & Telegram**, the 0.61.0 admin-gated settings page as a
  tab, shown only when `/api/settings` answers (own network). Its separate rail link
  is gone. Appearance is stored per browser, not per profile.
- **Theme system**: dark-first with light variant via `data-theme` (Dark/Light/Auto)
  set by `lib/appearance-boot.js` before first paint. Accent system: 7 contrast-tested
  swatches (coral, blue, violet, teal, green, amber, rose), each pair 4.5:1 in both
  themes. Artwork-backed blocks (cover, features) read the same in both via fixed
  on-image colours.
- **Pager**: Movies shelf and department pages use sequential page links
  (`1 · 2 · 3`); back, reload and shared links land on the page.

**Changed**

- The home page now reads as a magazine front: rotating cover story on a TMDB
  backdrop, three single-title features (Editor's choice, Trending, Staff pick),
  Continue beside a pull-quote, Recently added beside "This month". Every label
  and line comes from catalog; none invented. Layout is fixed height, never
  horizontal overflow at 375/768/1024/1440px.
- Rail changed: utilities (My List, Continue, Latest, Genres, Settings) stay
  full-height; departments (Home, Movies, Series, Tutorials, Collections) moved
  to a two-row header on phone (no bottom tab bar).
- Series seasons: `<select>` replaces season poster wall; season URLs
  (`#/series/<show>/<division.title>`) unchanged.
- Film button keeps "Resume from 2:20"; series button reads Resume / Continue /
  Play SxEy.
- Settings (Appearance/Profile/admin tabs) merged with legacy settings-menu
  (Telegram/cache tabs admin-only; conflict resolved as single page).
- Versions bumped: `Cargo.toml` (workspace root), `web/package.json`, `android/app/build.gradle.kts`.

**Fixed**

- High: kids profile no longer sees credits for adult-film leads in people
  search; people search returns only titles visible to the profile, counted
  server-side; person page empty state shown instead of name/portrait when no
  visible films.
- High: franchise link renders only when ≥1 film of that franchise is in the
  viewable library (not dead-end on single-film franchises or kids-filtered
  franchises).
- High: title-page tabs now keep selection and focus across redraws triggered by
  watch-state changes (My List toggle, pin, SSE position update).
- Medium: `credits::upsert` now transactional (DELETE + INSERTs in one
  `unchecked_transaction`, fixing potential partial-credits on interrupted
  metadata run).
- Medium: async pages (person, franchise detail) cache by id and render
  synchronously when cached, eliminating blank-page and scroll-reset on redraw.

**Owed to Android**

Department pages, feature pages with Cast, person pages, franchise pages, and
search grouping. Plan: `plans/260926-1330-android-editorial-departments-parity/`.
Android must accept v9 before any push. Existing Android builds refuse v9
packages until updated to ≥0.62.0.

**Older versions 0.61–0.58:** merged into `main` on 2026-09-26. See
[`project-changelog-2026-09-24-to-26.md`](project-changelog-2026-09-24-to-26.md).

## 0.57.0

**Added**

- The Android app gets the web player's magazine home page (Surface Parity):
  - a cover story on TMDB backdrops, with a pause control;
  - Editor's choice, Trending on TMDB and Staff pick features;
  - a Continue Watching strip of landscape cards with progress bars
    (Continue and Next up merged, as on the web);
  - a pull-quote tagline and a Recently Added row.

  Title pages open on a backdrop band, with "Make editor's choice" (hidden on
  kids profiles). The picks are a Kotlin port of `editorial-picks.js`, pinned to
  the web by shared JSON fixtures with exact seeded outputs
  (`web/test/fixtures/editorial-picks/`). Verified on a tablet: 855 backdrops
  fetched on the device.
- The phone fetches backdrops too: w780 on phones, w1280 on tablets
  (`resolve_backdrops` now takes a width). `SetSummary` carries `backdrop_key`,
  `tagline`, `rating` and `popularity`.
- The editor's choice syncs through the phone's watch state as well
  (`editorsChoice` in the core's sync record, same one-pick rule as the web).
  Shared watch-state fixtures cover it.

**Known difference:** a pinned episode's feature card opens its title page on
Android, where the web opens the show. This is written down in the code.

## 0.56.0

**Added**

- `push-index` and every command that publishes (`add`, `add-show`, `add-course`,
  `resume`, `finish-set`) first read the channel's newest index. They refuse
  when it holds sets this index lacks, since pushing would remove them from
  every player and phone. `push-index --force` replaces it anyway;
  `push-index --check` runs only the check and sends nothing. On 2026-09-25 the
  check reported 265 sets this machine lacks.
- `mediagram pull-index [--dry-run]` merges the channel's newest index into
  this machine's, so either uploading machine can publish the whole library.
  The rules:
  - It adds channel-only complete sets, but only those whose part messages
    still exist in the channel (a removed set is not brought back).
  - It re-reads sets that differ between the machines from their Telegram
    captions, in part order.
  - It adds missing shows and fills empty show fields; text is filled only
    from a row in the same language.
  - It never touches machine-local `meta`.
  - It is one transaction, preceded by a backup that is never overwritten:
    `library.before-channel-merge-<time>-<pid>.db`.

  `push-index --merge` pulls first, then pushes. Its guard then allows the
  sets the merge proved removed. The guard now counts only complete sets, as
  another machine's unfinished uploads are no titles.
- `mediagram metadata --refresh-older-than <DAYS>` asks TMDB again for cached
  answers older than that, so popularity (Trending), ratings and taglines stop
  being frozen at first lookup. A refresh that cannot reach TMDB keeps the old
  answer. It needs a TMDB key; without the flag the cache keeps everything, as
  before.
- `bun run preview` in `web/` (`scripts/preview.ts`): the real player pages over
  copies of this machine's channel snapshot and watch state, with real
  artwork and no Telegram, for UI work without touching the running player.
- A line-limit check for `web/` (`test/code-standards.test.ts`): new files stay
  under 200 lines, and the 28 files already over it are capped at their
  current size, so they can shrink but not grow.
- `bun run typecheck` (`tsc --noEmit`) in `web/`; TypeScript is a dev
  dependency. The one standing error (a JSON fixture typed as plain strings) is
  fixed.

**Changed**

- Package readers accept any index schema at or above the oldest they read,
  not only those listed. Schema changes only add optional columns; a breaking
  change moves `format`, which stays exact (`docs/mlib-package-v1.md` §7).
  Installed players and phones no longer stop updating at each bump.
- The home pull-quote prefers taglines of 90 characters or fewer.

**Fixed**

- Redrawing the page already showing no longer flickers. That happens when
  another device's watch state arrives, a pin changes, or the catalog
  refreshes. `lib/redraw.js` hands each loaded image to the new node showing
  the same picture, and marks the page `settled` so entrance animations don't
  replay; a turn of the cover still fades. Measured on the preview: 21 of 21
  images kept, no new poster requests.
- The phone's description store no longer records a lower schema when an
  older app opens a file a newer one wrote. After a downgrade and upgrade, the
  newer app used to replay a migration and fail on every open.

**Changed** (docs)

- The web player's architecture moved to `docs/web-player.md`;
  `system-architecture.md` §7 points there and is 528 lines. Changelog entries
  from 2026-09-14 to 2026-09-23 moved to two archive files. Every doc is now
  within the 800-line budget.

**Removed**

- A React Doctor CI workflow and `doctor` script under `web/` (not a React app;
  `web/.github` is never read by GitHub). `web/.claude/` and `skills-lock.json`
  are ignored.

## 0.55.6

**Changed**

- The artwork path `/api/posters/<key>.jpg` is built in one place,
  `artworkUrl` in `lib/catalog/plate.js`. Seven hand-written copies across the
  plate, film page, series header, title band, Featured reel and home modules
  now call it. Module-internal constants and helpers in `editorial-picks.js` and
  `home-features.js` are no longer exported, and the dead `.page-title` selector
  is gone. No visible change.

## 0.55.5

**Fixed**

- Images flickered just after opening the player at its bare address. Startup
  assigned `location.hash = "#/home"`, which fired `hashchange`, so the page was
  built twice about 100 ms apart. The second build replaced every image inside
  a view-transition cross-fade. The address is now set with
  `history.replaceState`, so the page is drawn once and the bare address leaves
  no history entry.

## 0.55.4

**Changed**

- Cleanup of the home page code, with no visible change:
  - The poster rows are a `strip` option of the shelf grids, and Recently Added
    passes `captions: false` instead of hiding captions with CSS.
  - The override block at the end of `home.css` is merged into the rules it
    overrode.
  - The feature facts line that was built and then hidden is gone.
  - The shared `.sr-only`, `initialOf`, and `--progress`, `--tint-active` and
    `--icon-search` tokens replace inline copies.
  - The poster rows hold 8 cards; wide cards and lists keep 6.

## 0.55.3

**Changed**

- The home cover story grows again: 650px at a 1024px-tall window
  (`--cover-height` clamp(620px, 63.5vh, 705px)).

## 0.55.2

**Changed**

- The home page's cover story is 20% taller (`--cover-height` clamp(528px, 54vh,
  600px), 84vh on a phone); the rail beside it follows.

## 0.55.1

**Changed**

- The web home page follows the magazine reference more closely. The cover is
  one band (about 45% of the viewport) beside a rail that runs only as deep as
  the cover; everything below takes the full width. The rail carries line
  icons and Home. Search is a magnifier that opens into a field, and the
  profile is an initial in a circle. Continue Watching cards carry their title,
  episode and progress bar over the picture. Recently Added is one row of eight
  posters. Section headings are sans; feature and cover standfirsts are upright
  serif. The cover's eyebrow reads "Featured today", which is true: its films
  are chosen by the day.

## 0.55.0

**Added**

- The web player redesigned as a digital entertainment magazine, dark-first
  with a light "paper" variant. Home is a front section: a rotating cover
  story on the film's TMDB backdrop (its title, tagline, year, rating), three
  single-title features (Editor's choice, Trending on TMDB, Staff pick),
  Continue watching beside a pull-quote of a real tagline, then Recently added
  beside a numbered "This month". A library rail holds the reader's own
  shelves; a sticky department bar holds Home, Movies, Series and Tutorials,
  dark glass over the cover that settles as it scrolls away. Film and series
  pages open on a backdrop band with a serif title, italic tagline and a
  drop-capped overview. Every route, element id and label the page code
  reads is unchanged. Newsreader Italic is newly self-hosted.
- "Make editor's choice" on film and series pages: one household pick that
  leads the home features, synced between devices like the Kids mark
  (`/api/editors-choice`, watch-state schema v8). Kids profiles are not
  offered it: the pick is the household's. Unpinning means no pick, even after
  a merge brought another device's. Android does not have it yet; see the
  plan's parity note.
- Backdrops: `mediagram posters` now also fetches each film's and series'
  wide TMDB artwork at w1280, stored beside its poster as `<key>-bg.jpg`
  (`tmdb-movie-550-bg`), from the details payload already cached, so it makes
  no new metadata requests. Catalog rows carry `backdrop`. The export package
  and the phone's on-device fetch leave backdrops out, by using the
  posters-only `resolve_posters`: the package has a 64 MB cap, and the phone
  has no hero to show one in yet. They serve the web player's magazine home
  (`plans/260925-2014-web-player-magazine-redesign`).
- Index schema **v8**: `shows.popularity`, TMDB's popularity as of the cached
  payload, which ranks the "Trending on TMDB" pick. It is optional to
  every reader, as `certification` is. `mediagram metadata` backfills it from
  the cache. Web catalog rows also carry `tagline`, `rating` and `popularity`.

**Fixed**

- The home page's empty-library check read `length` off the row totals, an
  object, so a library with nothing in it never showed its empty state.
- Package readers accepted only the oldest and the newest index schema, not
  those between. `SUPPORTED_SCHEMA` (Rust) and the web's `supportedSchema`
  were `[oldest, current]` checked by membership, so the v8 bump would have
  refused every v7 package. Both now use the full range,
  `mlib_spec::schema::READABLE_SCHEMAS` and `READABLE_SCHEMAS` in
  `web/src/catalog.ts`, and a test holds each to it.
- The player's startup poster count no longer counts backdrops, which sit in
  the same directory.

## 0.54.0

**Added**

- The Android web-parity work, merged: search and genre pages, audio and
  subtitle choice, the player's settings sheet, up next and queues,
  fullscreen gestures, picture-in-picture and a media session, series
  preload with offline badges, notes, profile removal, List/Grid shelves and
  "Mark finished" on the phone. Detail is in the dated entries for
  2026-09-24 and 2026-09-25 below. A state file an earlier pre-release build
  left at version 3 without the `kids` column gains it on open.
- "Mark finished" on the web player's Continue shelf. Each title there has a
  quiet word beside it that does what reaching the credits does: the resume
  position goes and the title counts as watched, synced like any other watch
  state. For a film finished on another device, or one given up on, that
  would otherwise sit on the shelf until played to the end. The player's own
  end-of-title path now calls the same function. The phone's Continue wall
  has it too, under each title (see the Android web-parity entries below).

**Changed**

- Redesigned the web library with a desktop sidebar, a separate search/profile
  toolbar, compact Continue and Next up cards, wider poster shelves, and
  responsive phone layouts. Interface text uses self-hosted Geist; the
  Mediagram wordmark is preserved. Light/dark appearance follows the device.
  Search now has a persistent label and keyboard users can skip to the library.
  Film actions appear before long descriptions. Routes, library data, saved
  List/Grid choices, and playback behavior remain the same.

**Removed**

- The Kids shelf, on the web and the phone. A kids profile shows the same
  titles, so the shelf only repeated it. The player's "Kids" mark stays on
  grown-up profiles, for letting an unrated title through.

**Fixed**

- A film or show page with no poster no longer shows a navy gradient block on
  the paper; it takes the flat sunk paper every other missing poster uses. The
  title page's Play button and the profile picker's Create button now turn
  paper-coloured on hover instead of pure white. All three were colours from
  outside the player's palette.

## 0.43.0

**Added**

- Kids profiles. Tick "Kids profile" when creating a profile, on the web or
  the phone, and that profile sees only titles rated FSK 12 or under plus
  unrated titles marked for Kids by hand — on every shelf, in search, in
  Featured and in Play next. The flag syncs between devices and cannot be
  switched off by a sync. It is a filter, not a lock. The web header's
  profile name now opens "Who's watching?" to switch profile without a reload.
  On a kids profile, the player (web and phone) doesn't offer the "Kids"
  mark, so a child can't approve titles for themselves.

- A Featured reel on the web player's Movies shelf. The Featured button opens
  a dark, full-window run of up to twelve films this profile has not watched,
  shuffled: each poster drifts slowly over a blurred copy of itself, fades into
  the next after seven seconds, and carries its title, year, genres, score and
  tagline. Play and Details act on the film shown; arrows, the dots, Space
  (pause), Esc and the back button steer it. Reduced motion gets still posters.
  Android gained its own reel once the phone fetched posters.

- The web player's Movies shelf is paged, 48 films at a time, with a row of
  page links under the grid. The page is in the address (`#/movies/page/3`),
  so back, reload and shared links return to it; `#/movies` is still page one.
  The Android Movies shelf pages the same way.

**Fixed**

- Catalog updates validate downloaded libraries before publication and reject
  stale concurrent completions. Failed state uploads retain successfully imported
  changes and leave retries possible.
- Upload resumption continues past unavailable source files, and repeated series
  imports direct pending episodes to `resume` instead of creating duplicate work.
- Browser playback and library navigation discard obsolete asynchronous replies,
  release cancelled playback resources, and follow the displayed episode order.
- Web storage and filesystem failures retain their causes; HLS session deletion
  follows the same browser-origin checks as other writes. Login diagnostics no
  longer redirect unrelated output during authentication.
- Android sync, refresh, login and playback operations now respect their owning
  lifetimes and report persistence failures without discarding retry state.
- Browser profile and list controls report failed saves, and shelf choices remain
  usable when browser storage is blocked. Switching audio no longer treats a
  partial conversion's duration as a completed title.
- Delayed transcode cleanup preserves replacement sessions. Audio probes are
  cancelled and reaped before the web server finishes shutting down.
- Upload surveys report files whose compatibility could not be checked; cancelled
  or failed CLI conversions stop their child processes.
- Web setup hides password input on Bun terminals while restoring normal echo
  for subsequent prompts. Failed profile discovery offers a retry, and the
  subtitle shortcut restores the selected language after toggling it off.
- Failed browser profile-state reads now offer retry before showing shelves and
  preserve the previously loaded profile. Disk measurements retain the last
  successful total when a scan fails, and sync counts newly imported profiles.
- Android account resets close local state before deleting it, and queued work
  from the old core cannot recreate the database. Profile choices reject stale
  completions; System diagnostics keep previous readings and offer retry.
- Malformed MP4 box sizes produce an error without overflowing the parser.
  Sign-out holds the session lock through stored-key removal, and private core
  diagnostics retain their nested causes while public errors stay sanitized.
- Web shutdown drains speculative cache reads before disconnecting Telegram.
  Failed metadata reads preserve stored identity and migration state, and a
  rejected browser Play request offers retry without disturbing newer playback.
- Android application changes restore watch-state ownership before reporting
  success, with a separate retry when reconciliation fails. Cache settings show
  recoverable failures even before the first reading; login, catalog and playback
  failures use controlled text while retaining their diagnostic causes.
  Initial provisioning refuses to overwrite an identity that is already installed.
- Session revocation and update listeners are bound to the connection that
  created them, so cleanup of a replaced login no longer disturbs its successor.
  Abandoned downloads stop polling Telegram once their reader closes.
- The channel index rejects provider identifiers outside SQLite bounds and keeps
  metadata unchanged on rejected writes; schema read and migration failures are
  reported instead of hidden. Upload plans with oversized part counts are refused
  before allocation, and mixed-case IMDb prefixes are normalized.
- CLI commands settle their work before disconnecting Telegram, and course
  imports report unreadable metadata sidecars instead of treating them as absent.
- Web thumbnail requests read only from disk and never fall back to Telegram.
  Cache inventory errors other than a missing file are surfaced, and idle
  transcode cleanup rechecks each session before stopping it, so a reused one
  survives. Concurrent audio probes for one title are coalesced, and cancelled
  Telegram reads finish before their stream closes.
- Library update hints keep arriving after a callback throws a value that cannot
  be printed.
## 2026-09-25

**Fixed**

- Android player: an up-next switch (autoplay or "Play now") now moves
  `LibraryPositions` before it reopens anything, through a new
  `LibraryPositions.replacePlayer(id, run)` and `UpNextController`'s own
  `pendingSwitch`, rather than calling back into the ViewModel directly.
  Previously a rotation or process restore right after the switch reopened
  the episode that had just finished, since the saved frame still named it.
  The autoplay gate also now treats a stopped loader with anything at all
  buffered as ready (`PlayerHandle.isLoading()`), on top of the ported 60s
  threshold: media3's default load control caps how far it will ever buffer
  ahead well under that figure, so a high-bitrate file previously waited
  out the full 45s patience ceiling every time. The gate is cancelled the
  moment playback starts by hand, matching the web's own
  `stopWaitingToStart`, so a poll landing after a viewer paused again can no
  longer call `play()` over it; the screen also now stays on through the
  countdown and the gate wait, both of which used to read as "not playing".
  Kids "Marked by hand" tiles play into that marked-by-hand run itself, the
  way `app.js`'s own `play(set, byHand)` does, in place of opening the
  tile's own title or show — its "Play all" button is removed to match, a
  deliberate difference recorded in phase 07's own notes since the web has
  none there either. The run a title opened with is now recomputed once the
  catalog finishes loading after it (a cold resume or process death could
  otherwise leave a title with no next at all), a seek while paused updates
  the up-next card the same way the web's `timeupdate` does, and a save
  landing against a title switched to less than a second in is now dropped
  rather than putting an unwatched episode onto Continue at 0s. The up-next
  card is measured clear of the transport bar and the system's own bottom
  inset the same way `SubtitleLayer` clears the picture, rather than a fixed
  padding that clipped under three-button navigation.
- Android player: after the picture-in-picture window's own ✕ pauses and
  drops the media session (`pauseForPipDismissal`, a user decision — the
  title stays open), pressing play again on that same still-open title
  never runs through `open()`, so nothing had restarted the session,
  foreground state or notification — a viewer could keep listening with
  the screen off and no lock-screen controls, unprotected from the process
  being killed for having no foreground service. `PlayerViewModel.onPlayingChanged`
  now restarts `PlaybackServiceController` on every transition into
  playing, matching a real player's own `onIsPlayingChanged(true)` —
  idempotent, since starting an already-running service is a no-op, so
  the ordinary case (opening a title) is unaffected.

**Added**

- Android profile removal, List/Grid shelves and "Mark finished", closing
  the web-parity plan. "Who's watching?" gains "Remove a profile…": tap a
  name, then the web's own confirmation; the profile and everything it
  watched go, and one another device still names comes back with the next
  sync, as on the web. Films and Series gain the web's List/Grid toggle,
  remembered per device (posters stay the phone's default); a shelf of
  courses is always a list, as on the web. The film shelf now carries the
  "offline" badge too. Continue gains "Mark finished" under each title,
  through the same `markFinished` the player's end-of-title path now calls.
  The deliberate differences left after parity are listed in the Android
  system-menu spec, §9.
- Android notes panel, matching the web player's: a title with a summary
  gets a "Notes" button in the player's top bar, and a lesson's notes open by
  themselves as the web's do. The markdown is parsed by a Kotlin port of
  `markdown.js` in `:core:model` (`model.markdown`), held to the web's by a
  shared fixture, `web/test/fixtures/markdown/cases.json`, that both
  `bun test` and `MarkdownFixtureTest` run; the fixture pins today's quirks
  too, such as emphasis not nesting inside emphasis. Links keep the web's
  scheme allow-list; of the allowed ones only `http`, `https` and `mailto`
  open (in another app), since `#` and `/` point into the web player's own
  page. The column sits beside the picture on a landscape window and below
  it in portrait; on a phone held sideways it is a sheet over the right of
  the picture instead, a deliberate difference since the web never runs in
  a window that short. A panel belongs to its title and resets on an
  up-next switch. `scripts/check.sh` now runs `:core:model:test`, which
  `testDebugUnitTest` never reached.
- Player framing — Fit, Fill, 16:9 and 4:3 — on both surfaces, matching
  what `framing.js`'s own comments always intended: 16:9/4:3 crop the
  picture into a centred window of that shape, never stretch it to fill
  one. The web's first cut didn't: `video { width: 100%; height: 100% }`
  in `style.css` makes CSS ignore `aspect-ratio` entirely, so "16:9"/"4:3"
  rendered identically to "fill" (a bug, not a decision) until
  `framing.js`'s new `framingBox` sized the video element itself to a
  `fitWithin`-computed window and `transport.js` applied it, kept in sync
  across a resize. Android's `core:playback` `Framing.kt` (`frame`, ported
  from the same `framing.js`) does the equivalent: a named ratio's window
  fits within the screen and the picture covers *that*, never the screen
  directly — `Video()`'s overlay slot (subtitles, and the up-next card
  while the transport bar is hidden) sizes to the window rather than to
  the picture's own box, which may be bigger. Fit and Fill are unchanged
  on both surfaces. On Android, a settings-sheet section and per-show
  memory alongside speed and subtitle style; a pinch over the picture sets
  Fill or Fit directly, the Android idiom standing in for the web's `z`
  key, which this app has no keyboard for.
- The player is now immersive: the system bars hide while it is on screen
  (`ImmersiveEffect`, `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`) and re-hide on
  resume and on this window regaining focus — covering both a return from
  the background and the settings sheet (its own dialog window) closing —
  restored only when the player itself is left. There is no fullscreen
  button, unlike the web — a phone's player is already fullscreen the
  moment it opens.
- A double tap over the picture seeks — left third back, right third
  forward, by whatever the transport buttons' own increment already is —
  and the middle toggles play/pause through the same buffering-aware
  `Util.handlePlayPauseButtonAction` the transport button uses, so it works
  mid-rebuffer too. A brief "-10 s"/"+10 s" label accumulates across
  repeated double-taps on the same side. A single tap still toggles the
  transport bar, delayed by the double-tap timeout; the gestures live in a
  new `PlayerGestureLayer` over the whole screen rather than carved to the
  video box, since the transport bar and sheet already consume their own
  taps first. A pinch consumes its own pointers so it never also reads as
  a tap, a double-tap, or a seek, and never fires from a finger already
  claimed by the scrubber. `Video()` measures its own size with
  `BoxWithConstraints` rather than after the fact, so neither the first
  frame nor the first frame after a rotation ever draws full-bleed before
  framing applies. The up-next card, previously clamped only to the
  transport bar, now clamps to the visible picture itself while the bar is
  hidden — otherwise, under a letterboxing framing, its scrim and buttons
  hung in the black band below the picture.
- Picture-in-picture and lock-screen controls. A button beside the back
  arrow and, on API 31+, `setAutoEnterEnabled` shrink the player into a
  floating window on the home gesture, while actually playing or
  buffering with the intent to (`playWhenReady`) — below 31, the same
  gesture is caught by hand in `MainActivity.onUserLeaveHint` through a
  small `PipEntryPoint` slot, since `ui-mobile` cannot import
  `MainActivity` the other way around. Neither runs on a device lacking
  `FEATURE_PICTURE_IN_PICTURE` (Android Go, some OEM builds), which
  `packageManager.hasSystemFeature` now gates before any of it. The
  window opens at the video's own aspect, clamped to what
  `PictureInPictureParams` accepts (1:2.39..2.39:1, built as exact
  fractions rather than a decimal that rounded past the true minimum),
  always `Fit` regardless of the show's own remembered framing (a
  remembered 4:3/16:9 crop otherwise letterboxed a second time inside a
  window already shaped to the video), and carries three `RemoteAction`s
  — skip back, play/pause, skip forward — answered by a
  `BroadcastReceiver` acting on the same `Player` the transport bar's own
  buttons use; leaving the player screen disarms auto-enter on the
  activity again, so the catalog itself never shrinks into a leftover
  window. Every other overlay (transport bar, marks, settings sheet,
  up-next card) is hidden while in the window; the up-next countdown keeps
  running regardless, since it lives in the ViewModel, not in the hidden
  composable. **Closing the window pauses and saves, keeping the title
  open** (a user decision) — reopening the app finds it exactly where it
  was, not back at the catalog — told apart from expanding back to full
  screen by whether the activity's own lifecycle has already dropped to
  `CREATED` by the time the system reports leaving picture-in-picture.
  `MainActivity` now declares `android:configChanges` for screen size and
  orientation, so neither a rotation nor a picture-in-picture resize
  recreates the activity any more — the existing stop-on-dispose path is
  now also guarded by `isInPictureInPictureMode`, in case an OEM still
  tears the activity down mid-window. A new `:feature:player`
  `MediaSessionService` (`PlaybackService`) wraps the app's singleton
  player in a `MediaSession`, added to the service (not merely built —
  the session notification, foreground state and lock-screen controls all
  depend on that) with a session activity so tapping the notification
  reopens the app; started and stopped alongside `PlayerViewModel.open`/
  `stop`. No notification-permission prompt: media-session notifications
  are exempt from `POST_NOTIFICATIONS` on API 33+ by Android's own
  documented behavior, so there was nothing to ask for. Playback still
  pauses only on actually leaving the player screen, never on the screen
  locking or on entering picture-in-picture, per the user decision the
  plan records. `MediaMetadata` (title line, poster) rides on the current
  `MediaItem`, updated through `Player.replaceMediaItem` and remembered
  so a cold start or a `retry()` reload reapplies it rather than losing it
  silently, so the lock screen and notification never show a stale or
  blank title. The button, its auto-enter and the lack of a web-side
  equivalent (the web has only the `p` key) are recorded as deliberate,
  phone-only touch equivalents, not owed to the web.
- Android series preload and offline badges, matching the web's own
  `series-preload.ts`/`held.ts`: opening an episode asks a new
  `:core:playback` `SeriesPreloader` for the next two episodes of the
  run `UpNextController` already resolves — the same `nextInQueue`, so
  nothing computes "next" a second way. One worker, one download at a
  time, over a media3 `CacheWriter` on the same `cacheDataSourceFactory`
  playback itself reads through (`CacheDataSourceWriter`), on its own
  dedicated thread and its own `PlaybackCounters` so a quiet preload never
  shows up as the playing title's own reads. A candidate is skipped when
  Wi-Fi is not the active network (`ConnectivityManager.isActiveNetworkMetered`)
  or when holding it would push held-plus-current-plus-candidate past 75%
  of the cache budget (`fitsInPreloadBudget`) — phone-only limits the web
  never needed, recorded as deliberate differences. `HeldSets` answers
  whether a set is fully on disk from the cache alone
  (`Cache.isCached(key, 0, totalBytes)`, no Telegram asked); `CatalogViewModel`
  scans it once when the shelves are built and folds in every
  `SeriesPreloader.heldEvents` id after, without a full rescan. An
  `offline` badge (`OfflineBadge.kt`, the web's own wording) now shows on
  Continue, Next up, Watchlist and Kids cards, on search rows, on
  Collections-tab list rows, and on the episode and lesson rows of a season
  or course (`course-view.js`'s `lessonRow`) — the place a viewer looks for
  what the preload took; the player's stats overlay says `cached`
  in place of an ahead-seconds reading once the open title is held,
  matching `preload-readout.js`.

## Earlier

- 0.58.0 to 0.61.0: [`project-changelog-2026-09-24-to-26.md`](project-changelog-2026-09-24-to-26.md)
- 2026-09-24: [`project-changelog-2026-09-24.md`](project-changelog-2026-09-24.md)
- 2026-09-19 to 2026-09-23: [`project-changelog-2026-09-19-to-23.md`](project-changelog-2026-09-19-to-23.md)
- 2026-09-14 to 2026-09-18: [`project-changelog-2026-09-14-to-18.md`](project-changelog-2026-09-14-to-18.md)
