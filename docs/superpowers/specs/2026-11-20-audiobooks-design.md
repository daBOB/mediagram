# Audiobooks — design

2026-11-20. Status: approved by the user 2026-11-20 ("yes go on"). Plan:
`plans/261120-2227-audiobooks-section/plan.md`.
Builder check done; its two questions answered by the user.

## Intent

The user wants a new **Audiobooks** section holding the audiobooks in
`/media/DBI/HiDrive/HB`, each card showing the cover embedded in its file and
the section grouped by the file's genre tag. The household listens on the
phone with the screen locked (adults on the go) and on every other surface;
some books are for the kids (Bibi & Tina, NEINhorn, Vaiana).

## The user's decisions (2026-11-20, this conversation)

- "lets add a new section Audiobooks … use the embedded coverart and genre."
- "everything should handle mp3 native" — every surface (web, Android
  phone/tablet, TV) plays the MP3 as it is. No transcode, no remux, anywhere.
- Option 2 of three offered: the section on every surface, plus listening on
  the phone/tablet with the screen locked and play/pause on the lock screen.
  Speed, sleep timer and 30 s skips (option 3) are **not** in scope.
- "yes Kinderbücher for kids" — a book whose genre is `Kinderbücher` is
  visible to every kids profile without anyone marking it.
- Daily limit — "no": listening does not count against a kid's daily
  limit, and a book still opens after the limit is used up.
- iPhone — "yes": adults also listen on iPhones through the web player, so
  the web player must keep a book playing on a locked iPhone and show its
  title and cover on the lock screen.
- "update all apps": every app of the media-library line ships this —
  uploader (this machine and bytefortress), web player, Android app
  (phone/tablet/TV through the release channel).
- "keep track of audiobooks to continue later" — read as: a book resumes
  where it was left and shows in Continue rows. (Also read as "write this
  work down to resume it in a later session", which this document is.)

## The source material (measured 2026-11-20)

- 118 files, 16 GB, one MP3 per book, flat folder. File names end in
  ` [<shop id>].mp3`; the tag `title` is the proper title (colons intact).
- Every file: MPEG audio CBR 64 kbit/s, one 640×640 MJPEG cover stream
  (`attached_pic`), tags `title`, `artist` (author), `album` (= title),
  `composer` (narrators), `genre`. No chapter marks. Longest 26 h
  (Red Rising, 94 137 s).
- No file has a Xing/Info/VBRI header. Seeking and duration rest on the
  constant bitrate (ExoPlayer falls back to its constant-bitrate seeker when
  no header exists; browsers estimate by byte range).
- 13 genres: Sachbuch 31, Ratgeber 24, Geschichte 23, Religion & Spiritualität
  10, Kinderbücher 8, Romane 6, Biografien 5, Wirtschaft & Karriere 3,
  Sprachen 2, Science-Fiction 2, Klassiker 2, Jugendliteratur 1, Fantasy 1.
- 118 tag titles, no two slugging (`mlib_spec::slug`) to the same key; the
  longest is 200 characters.

## Goals

1. A `book` set kind, uploaded by a new `mediagram add-audiobook` command that
   reads the tags: title from `title`, category from `genre`, poster from the
   embedded cover.
2. An Audiobooks department on the web player, Android phone/tablet and TV,
   built like Documentaries: Continue listening, one row per genre, Recently
   added, every book.
3. Native MP3 playback everywhere, the cover shown where the picture would be.
4. Resume and Continue work for books as for every other set.
5. Kids profiles see the Kinderbücher books; nothing else of this section
   unless marked by hand as today.
6. Books do not count against, and are not blocked by, a kid's daily limit.
7. On an iPhone (web player in Safari) a book keeps playing with the screen
   locked, with title and cover on the lock screen.
8. The 118 books uploaded and the index published; every app updated.

## Non-goals

- Speed control, sleep timer, skip buttons, chapters (none in the files).
- Grouping series (Bibi & Tina 121/124/126, the six "Deutsche Geschichte"
  parts). The tags carry no series; each file is one card.
- Showing author or narrators. The tags have them; nothing asked for them.
  Easy to add later from the same tags.
- Backdrops. Covers are square, so they are posters only; the department
  hero falls back to its no-backdrop form.
- Other audio formats (m4b, m4a). Only `.mp3` is walked.
- New achievements. Listening still counts toward a title's own time
  (`stats_titles`). It does **not** count toward a day's total
  (`stats_days`), because that total is what the daily limit reads; so the
  per-day hours and streaks leave listening out. (Builder's call during
  planning, 2026-11-20: the day table has no set or kind to subtract by.)
- Playing on while browsing the catalog (leaving the player screen still
  stops playback, as for video).

## Anti-goals

- Any conversion of an MP3 on any surface.
- A book appearing on Movies, Documentaries or any other shelf.
- A cover cropped on a card or in the player.
- The web player and the Android app deciding any of this separately
  (CLAUDE.md § Surface Parity: the web player is the reference).

## Approaches considered

1. **New `book` kind, own department (chosen).** Mirrors how `docu` was
   added (commit 90546aba): standalone sets, category rows, `title-{slug}`
   artwork. Costs a change on every layer, which parity requires anyway.
2. **Upload as `docu` with a category.** Cheapest, but books would sit in
   Documentaries, share its section switch and kids rule, and need a
   container-based special case to tell them apart. Rejected.
3. **Decide "audiobook" from the container (`mp3`) with no new kind.** Every
   reader would need the same container rule and a kind would still be
   wrong. Rejected.

For listening, the user chose option 2 of: (1) section only, (2) section plus
screen-locked listening on phone/tablet, (3) a full audiobook player.

## Design

### Spec (`crates/mlib-spec`)

- `Kind::Book`, spelled `book` (`kind_spelling.rs`, `Kind::ALL`). Documented
  in `caption/mod.rs` and `docs/mlib-spec.md`: a standalone set like a
  standalone `docu` — `title` only, no `show`, `s`, `e`; `container` `mp3`;
  `acodec` `mp3`; no `vcodec`, `q`, `hdr`.
- `category_key`: `book` → a new department constant `AUDIOBOOKS`
  (`"audiobooks"`), item key `title_art_key(title)`. The shared fixture
  `web/test/fixtures/categories/keys.json` gains book cases.
- Older readers already tolerate an unknown kind: the uploader skips and
  keeps it (`index/sets_pending.rs`), the web keeps unknown kinds visible,
  Android files them as films until updated. No schema bump is expected; the
  builder confirms against `docs/mlib-spec.md`'s versioning rules.

### Uploader (`crates/mediagram`)

- `media/inspect.rs`: a stream with `disposition.attached_pic = 1` is cover
  art, never the video. A book then has `vcodec`/`width`/`height`/`quality`
  all `None`. (Also correct for any MP4 carrying a cover.)
- `mediagram add-audiobook <file|folder> [--dry-run] [--no-push]`: walks
  `*.mp3` (case-insensitive, recursive, sorted). Per file:
  - title = tag `title`, else the file stem without a trailing ` [digits]`;
  - category = tag `genre` (trimmed; none if absent), written through the
    existing categories table (`edit::category`) under the book's key;
  - poster = the `attached_pic` stream's bytes, stored with
    `index::artwork::put` under `title_art_key(title)` with its real mime
    (`image/jpeg` or `image/png`); a file without one gets no poster;
  - uploaded as one `Kind::Book` set through the shared upload `Session`,
    exactly as `add-docu` uploads a standalone documentary.
  - A re-run must not upload a book twice (the way `add-docu` on a folder
    skips what it already sent, commit 704a74ee).
  - `--dry-run` prints title, genre and whether a cover was found per file.
- The MP3 is uploaded byte for byte (`remux::ensure_faststart` already
  leaves non-MP4 files alone).
- Never deletes the source.

### Core (`crates/mediagram-core`, Android's reader)

- `dto/summary/poster_key.rs`: `book` keys its poster `title-{slug(title)}`,
  as a standalone `docu` does, so the artwork table resolves the cover.
- Category resolved through `category_key` like `tut`/`docu`.
- Nothing synthesises an FSK for a book (see Kids).

### Web player (the reference)

- Server: `response.ts` `contentType("mp3")` → `audio/mpeg`.
- `playable.js`: `mp3` is a direct-play container; a set with no video codec
  plays direct when its audio does (audio-only is not "unknown video").
  `docs/running-the-player.md`'s table is updated with it (a test holds the
  two together).
- `sections.js`: `audiobooks: { label: "Audiobooks", empty: "No audiobooks
  yet.", extent: "audiobook" }`, last in nav order. `section-access.js` maps
  `book` → `audiobooks`, so the Sections switches and household hiding work.
- `departments.js`: `book` sets are pulled out before `groupLibrary`, like
  `docu`; `library.js` never files them as films.
- Audiobooks department page (`department-pages.js`), modelled on
  Documentaries: hero, **Continue listening**, one row per genre via
  `categoryRows` ("Other" last), Recently added, every book.
- Cards and the player show the whole square cover, never cropped.
- Player: with no video track, the stage shows the cover in place of the
  picture; transport, resume and progress work unchanged.
- Locked iPhone: a book plays through an element iOS Safari keeps playing
  with the screen locked (an `<audio>` element, if a `<video>` is paused on
  lock — confirmed on a real iOS device), and `navigator.mediaSession`
  carries the title and the cover as artwork, with play/pause/seek actions.
- Search: books get their own "Audiobooks" group.
- Home: books appear in the Continue rail like any set with a position.
- Anything typed over the kinds (`state/achievements.ts` `TitleKind`, stats)
  accepts `book` without breaking; no new achievement.

### Kids

- Rule (both ports: web `age-rating.js` `forKidsProfile`, Android
  `AgeRating.kt` `forKidsProfile`): a `book` whose category is exactly
  `Kinderbücher` counts as safe for every kids limit. Every other book is
  unrated and follows today's hand-mark rule. No FSK badge is shown for it.

### Daily limit

- Both gates (web `daily-limit-gate.js` `mayOpen`, its Android port) let a
  `book` open after the limit is reached, and the server's count of a kids
  profile's time today (`web/src/state`, `/api/profiles/{id}/today`) leaves
  book positions out (books never add to `stats_days`, on the web server and
  in the Rust core Android uses). Every other kind counts exactly as now.
- No "time left" warning while a book plays, on either surface (user decision
  2026-11-20): listening is not screen time.

### Android phone/tablet and TV (parity with the web)

- `SetSummaryToMediaSet.kt`: `book` → a new `Kind.AUDIOBOOK`.
- `Sections.kt`: `Section("audiobooks", "Audiobooks")` last; `sectionKey()`
  maps it. `Department.AUDIOBOOKS` — the enum name must equal the section key
  or hiding the section silently fails.
- `Shelves.kt`, `Departments.kt`, `LibraryPages.kt`: an Audiobooks page model
  copied from `DocumentariesDepartment`; a phone screen and a TV page copied
  from the Documentaries ones; the compiler lists the exhaustive `when`s.
- Search groups (`SearchGroups.kt`, phone chips, `TvSearchGroups.kt`).
- Player: `ui-common/.../player/PlayerVideo.kt` draws the cover
  (`HeroArtwork`) when `videoSizeDp == null`, on both surfaces.
- Bug to fix: watch-together and cast-to-TV wait for the first video frame
  (`together/TogetherJoining.kt`), which an MP3 never sends, so a cast book
  never starts. Audio-only must count as ready.
- Screen-locked listening: `PlaybackService` (a `MediaSessionService`,
  `mediaPlayback` foreground type) already gives lock-screen and
  notification controls, and `ON_STOP` pauses only when presenting to a
  wireless display. Expected to work as is; verified on the device, fixed if
  not.

### Upload and release

- After the code lands: `mediagram add-audiobook /media/DBI/HiDrive/HB`, one
  upload slot, then `push-index`. About 16 GB.
- Order: the Android release and the web deploy go out **before** the `push-index` that first
  carries books, so an older app never files them under Movies.
- The 118 covers total 6.9 MB, taking the index's artwork from about 25 MB
  to 32 MB, under the 64 MB package ceiling (builder check, 2026-11-20).
- A new section is shown by default: profiles store the sections they hide,
  so no migration is needed.
- A book counts as finished only in its last minute (the existing rule), so
  a long book stays in Continue until really done.
- Every app updated ("update all apps"): uploader reinstalled here
  (`~/.cargo/bin/mediagram`) and on bytefortress, web player redeployed
  (this machine and funstation), Android released through
  `scripts/release-android.sh`. The Spieltisch line is not touched.
- Version: the media-library line gets a minor bump in every manifest
  (`Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts`).
- The other uploader (bytefortress) keeps `book` rows it cannot read; update
  it before it next uploads.

## Verification

- Rust: inspect ignores `attached_pic`; tag → title/genre/cover; category
  and poster keys for `book`; `add-audiobook --dry-run` on the real folder.
- Web: unit tests for `playable` (mp3, audio-only), departments, kids rule,
  category keys fixture; the page checked in the stub harness
  (`cd web && bun run preview`), never the live player.
- Android: unit tests for kind mapping, section key, kids rule, department
  grouping. Device walk on tablet `caad49da` (test profile): open a book,
  cover shows, seek, lock the screen and keep listening, pause from the lock
  screen, reopen and resume. TV: open a book, cover shows; cast one from the
  tablet and it starts. A kids profile sees the 8 Kinderbücher books only,
  and still opens one after its daily limit is used up.
- iOS: on a real iPhone or iPad in Safari, a book keeps playing with the
  screen locked, and the lock screen shows its title and cover.

## Left to the builder

- Exact card and stage visuals within the existing design system (whole
  cover, no crop).
- Where tag reading lives (extend `media/probe.rs` to read format tags).
- How `add-audiobook` recognises an already-uploaded book.
- Telegram caption length with 200-character titles: check the caption codec
  limit and shorten only if it is actually exceeded.
