# Project changelog — 2026-09-24

Archived from [`project-changelog.md`](project-changelog.md) to keep it under 800 lines.

## 2026-09-24

**Added**

- Android player: a settings sheet (gear button in the transport bar) with
  a Speed section — the six steps `web/public/lib/transport.js` offers,
  remembered per show through `state.db`'s `preferences` table and
  reapplied on every open, never inherited from whichever title played
  last on the app's one singleton player. A title line at the top (show ·
  episode · title, the web's `titleLine` order) and an `ends HH:MM` label
  beside the transport bar's own clock, both dividing by the chosen speed.
  A failed set now shows a Retry button that re-opens at the last saved
  position — a phone-only touch equivalent of the web's seek-to-retry,
  recorded as deliberate rather than web debt. `episodeLabel` moved from
  `feature:catalog` to `core:model` so the player can use the same port
  without one feature module reaching into another.
- The catalog core now carries what the web player's `forBrowser` already
  attached to every row: `genres`, `subtitles` (languages, sorted), and
  `hasSummary`, joined into `list_sets` by poster key and by set id. Genres
  read the index's `shows.genres` first, then this device's own fetched
  sidecar for a title the index says nothing about — a deliberate difference
  from the web, which has no such sidecar and reads the index alone.
- `Core::set_text(setId, kind, lang)` reads a stored summary or subtitle
  track straight off the index, the same query the web player's
  `web/src/assets.ts` runs, ready for the subtitle and notes panels.
- `state.db` gains a `preferences` table, ported verbatim from the web's
  schema v5: what a viewer chose per show (audio language, subtitle
  language, playback speed), capped at 200 characters per string, unsynced
  and per profile.
- Profiles can now be deleted (`Core::delete_profile`), the same "remove
  only" the web offers — a device still holding the profile brings it back
  on its next sync round. The chosen-profile pointer clears itself on the
  next read if it named the profile removed.
- Android's `MediaSet` carries the same three new fields (`genres`,
  `subtitleLanguages`, `hasSummary`); no UI reads them yet.
- `Core::search(query)` ports the web player's search — `search/normalize.rs`,
  `search/rank.rs`, `search/excerpt.rs` — so the phone ranks a query against
  the index the same way the server does, summaries included, rather than a
  Kotlin filter over the in-memory set list that has no summary to search.
  A tie within a field is broken by `icu_collator`, ICU4X's pure-Rust German
  collator, so the order agrees with the web's `localeCompare("de")` rather
  than approximating it — bundled at compile time (`compiled_data`), no
  runtime data file. Folding the catalog costs enough (tens of milliseconds
  over the real library) that it is cached in `Core`, keyed by the catalog's
  resolved version directory, and only redone after a refresh actually
  changes it. `web/test/fixtures/search/cases.json` runs against both the
  web's `SearchIndex` and this port, so a case that would only pass one of
  them fails the build. Kotlin gets `CoreClient.search`; no screen reads it
  yet.
- Android search screen: a search icon in the library's top bar opens a
  field, debounced 200 ms like the web's own box, ranking through
  `Core::search` and joining each hit back onto its `MediaSet` for the row's
  facts — a flat list, no shelves, matching `search-view.js`. A film or
  show's genres are now links (`GenreLinks`) rather than a plain provider
  sentence, opening a genre page (films then series, headed only when both
  are there) ported from the web's `genreShelf`. `LibraryFlow.kt`'s
  navigation `when` moved to `LibraryFlowBranches.kt` to make room.
- The settings sheet's Audio section: rows built and labelled the way
  `web/public/lib/audio-chooser.js` builds them (language · title ·
  channels · codec, `Track N` for one the file names nothing), remembered
  per show the same way speed is, under the same `preferences` scope.
  Codec names match ffprobe's own (`ac3`, `eac3`, `dts`, `truehd`, …, the
  same names the web's probe already returns), and language names are
  always English, the same fixed locale the web and this app's stats
  overlay already use — never the device's own, which the phase this
  built from had said, wrongly against that same web reference. The
  language is matched, never the ordinal — a re-rip can reorder a file's
  streams — and a pick with no usable language (blank, or `und`) is
  honoured for the session but never saved over a real choice already on
  record, the same restraint `player.js`'s own change handler has. A track
  this build has no decoder for (DTS, TrueHD — there is no FFmpeg
  extension here) is left off the menu entirely and never pinned, even if
  it is what a viewer once chose; with nothing remembered, the row already
  marked is whichever one ExoPlayer's own selector picked, never a guess
  invented here. Only shown for more than one (decodable) track. The sheet
  itself now scrolls and pads for the navigation bar, so Audio's rows are
  reachable under three-button navigation in either orientation.
  Mechanically different from the web, deliberately: the web can only
  switch by asking the server to re-encode into a new response; ExoPlayer
  switches inside the decoder with a `TrackSelectionOverride`, no re-fetch,
  same decision either way. `AudioChoiceController` attaches its own
  listener straight to the app's singleton player rather than through
  `DefaultPlayerHandle`'s permanent one, and undoes that when its own
  `PlayerViewModel` is cleared — that permanent listener has exactly one
  caller today (playback state), and stays that way. Guards against a
  synthetic empty tracks report `ExoPlayerImpl` fires while reloading for
  a new title, which had been read as "this file has no audio" and
  silently dropped every real track that followed.
- The settings sheet's Subtitles and Subtitle style sections. Off is a
  remembered choice like any other (`transport.js`'s own rule: nothing
  remembered or a language the file has lost defaults to the first track;
  a remembered "off" stays off); size (Small/Normal/Large/Larger), backing
  (Shadow/Box/None) and a `+/-30s` timing offset (`0.1s` steps, a Reset
  button) are ported from `subtitle-panel.js`/`subtitle-style.js`, all
  remembered per show under the same `preferences` scope speed and audio
  already use. ExoPlayer never renders a subtitle: its text renderer is
  disabled outright (`PlayerFactory.buildPlayer`, once, not per open), and
  a title's VTT (already in the index via `Core::set_text`, phase 01) is
  parsed with media3's own `WebvttParser` — confirmed present in this app's
  1.10.1 media3-extractor dependency — into plain cues an app-owned
  `SubtitleLayer` draws inside the video's own rectangle, bottom-centred,
  lifted by exactly as much of the picture as the transport bar covers
  while it is shown (measured, not a fixed clearance: a guessed 96dp left
  cues on top of the bar on the tablet). An offset is applied fresh
  from each cue's parsed times on every read rather than accumulated onto
  a mutable cue (there is nothing here for a second nudge to drift away
  from, unlike the web's own `TextTrackCue` mutation). No position control,
  the same restraint and for the same reason `subtitle-style.js` records.
  `SubtitleChoiceController`'s language pick alone races two sources (a
  title's own languages, known as soon as the set resolves, and this
  profile's remembered choice, known once the preference round trip that
  follows it lands) and chooses nothing until both have answered: a
  default applied from the languages alone fetched a file the remembered
  choice then turned off, and could flash its cues on a show the viewer
  had switched off. A language picked by hand while that round trip is
  still in flight wins over whatever it answers late. Size and backing are
  remembered one value at a time, as the web does, so a size picked early
  never writes the default backing over a remembered one.
- Android player: up next and queues, ported from `up-next.js`/`autoplay.js`/
  `player.js`. In the last 30 seconds a card offers the next title with
  "Play now" and "Cancel"; once the title actually ends (never before — the
  bug the web fixed by separating the two) a ten-second countdown starts it
  unattended, waiting on the same buffer gate the web polls (a minute
  buffered ahead, or everything a short title has, or 45 seconds' patience,
  whichever comes first) so a metered link never starts into a stall. A
  standing "Play next" button in the transport bar stays even after a
  cancel, which is remembered per title for the app's own lifetime — the
  phone's equivalent of the web's in-memory `Set`, lost only on a process
  death the way a reload forgets it there. "Next" is a show's or course's
  own flattened order (`catalog.runFor`, crossing a season or folder
  boundary the same way `catalog.playOrder` already does) unless the title
  opened from a hand-built list or the Kids wall's "Marked by hand", which
  now also gets a "Play all" button next to Lists' own; both pass their run
  explicitly rather than have the player guess it. `PlayerHandle.open`
  gains a `playWhenReady` flag (default true) and a `play()`/
  `bufferedPositionMs()` pair for the gate to drive; `PlayerHandle.Listener`
  gains `onEnded()`.
