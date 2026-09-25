# Phase 05 audio track chooser — review fixes

Findings tally as relayed by the coordinator: 1 Critical, 2 High, 3 Medium,
4 Low, plus two device findings (a functional bug and a layout bug). Device
check on the tablet before this pass: Blade: Trinity's German and English
tracks both showed with correct labels, picking English switched the audio,
and `audio=en` was saved — then the two device findings below.

## C1 — the chooser died after the first title

`AudioChoiceController.handleTracksChanged` trusted every `onTracksChanged`
call as a real answer. `ExoPlayerImpl` fires a synthetic `Tracks.EMPTY`
clearing itself the moment `setMediaItem`/`prepare()` reset it for a new
item, strictly before the real probe of that item answers. `handleTracksChanged`
set `facts = emptyList()` for that event; `applyIfReady` read a list of one
or fewer tracks as final, set `settled = true`, and published an empty menu
— then the real tracks arrived and were silently ignored (`applyIfReady`'s
first line, `if (userChose || settled) return`). This hit every title after
the first, and any reopen of the same title: `PlayerViewModel.stop()`
already clears the session, so a reopened title is never `sameTitle` and
always goes through a real reload. This was device bug (a) — leaving the
player and reopening Blade: Trinity showed no Audio section at all.

**Fix.** `handleTracksChanged` now returns immediately for an event with no
groups at all (`tracks.groups.isEmpty()`), before touching `facts` or
`settled`. A genuinely audio-less file still settles correctly once its real
(non-empty) `Tracks` arrives with zero audio groups in it.

**Test.** `AudioChoiceControllerTest.aSyntheticEmptyTracksEventBetweenTitlesDoesNotSuppressTheRealOnesThatFollow`:
plays a film, stops, opens a show with a remembered language, sends
`Tracks.EMPTY`, then the show's real tracks — asserts the remembered
language still applies and the menu still builds. `RecordingPlayer.emitTracks`
already accepted any `Tracks`, `Tracks.EMPTY` included, so no fixture change
was needed to send it.

## H1 — a track the device cannot decode could be pinned; nothing remembered still pinned a guess

Two related gaps. `AudioTrackSelection.extractAudioFacts` never checked
`Tracks.Group.isTrackSupported`, so a DTS or TrueHD track (no FFmpeg
extension in this app) could end up remembered, offered, and pinned via
`TrackSelectionOverride` — which `DefaultTrackSelector` applies without
checking decodability, so a stock build would silently fail to play
whatever it landed on. Separately, `AudioOptions.defaultAudioTrack` invented
a "file's own default" (via `Format.selectionFlags`) and
`AudioChoiceController` pinned it whenever nothing was remembered — a guess
this app has no business making, when ExoPlayer's own selector already
accounts for device capability and would have picked something playable on
its own.

**Fix.** Unsupported tracks are excluded at extraction
(`extractAudioFacts`) rather than shown disabled — a track this build
cannot play at all is not a choice worth presenting, and excluding it means
the existing "one track is no menu" rule already covers a file where only
one stream survives filtering, with no second rule needed. `defaultAudioTrack`
is gone; `AudioTrackFacts.isDefault` is now `isSelected`
(`Tracks.Group.isTrackSelected`), and `audioOptions` marks a row selected
from the remembered language when it names one, else from `isSelected` —
ExoPlayer's own live pick, never a guess of this app's own.
`AudioChoiceController.applyIfReady` only calls `pinAudioTrack` when the
remembered language actually names a (supported) track; with nothing
remembered, nothing is pinned at all.

Folded into the same pass: `TrackSelectionOverride` was always built by
looking a group up fresh in `player.currentTracks`, not the `Tracks`
snapshot a caller's own facts were read from — a caller could, in
principle, land a pick on a group belonging to a different open. Now
`AudioTrackSelection.pinAudioTrack` takes the exact `Tracks` `facts` came
from (kept as `AudioChoiceController.lastTracks`) and re-checks the group
is still of type `TRACK_TYPE_AUDIO` before building anything.

**Tests.** `AudioOptionsTest`: `nothingRememberedLeavesExoPlayersOwnSelectionMarked`,
`aLanguageThisFileDroppedAlsoFallsBackToExoPlayersOwnSelection` (replacing
the old default-track tests). `AudioChoiceControllerTest`:
`nothingRememberedLeavesExoPlayersOwnSelectionAlone` (no override call at
all), `anUndecodableTrackIsNeverOfferedOrPinnedEvenWhenItsLanguageIsRemembered`
(a three-track fixture, German tagged DTS and `FORMAT_UNSUPPORTED_SUBTYPE`;
asserts it never appears in the menu and nothing is pinned even though its
language is what's remembered).

## H2 / device bug (b) — the sheet ran under the navigation bar

`PlayerSettingsSheet`'s `Column` had no scroll and no navigation-bar
padding; on the tablet, Blade: Trinity's Audio header landed on the
home/back row and its rows ran off the bottom edge.

**Fix.** The `Column` now scrolls (`verticalScroll(rememberScrollState())`)
and pads for the navigation bar (`navigationBarsPadding()`), so every row
is reachable above the system bar regardless of how many sections the
sheet holds or which way the phone is held.

## M1 — labels didn't match the web's

Two mismatches against `web/test/audio-chooser.test.ts` and
`language-label.js`, both because the phase spec itself had drifted from
the web reference it was supposed to port:

- **Codec.** `audioCodecName` upper-cased the MIME subtype
  (`audio/ac3` → `"AC3"`); the web shows ffprobe's own `codec_name`
  unchanged (`"ac3"`). Now maps media3 MIME subtypes to their ffprobe names
  (`ac3`, `eac3` — including the `-joc`/Atmos variant, `aac`, `mp3`/`mp1`/`mp2`,
  `dts` — DTS, DTS-HD and DTS Express all fold to it, ffprobe keeps the
  distinction in `profile` rather than `codec_name` — `truehd`, `pcm`),
  falling back to the lower-cased subtype for anything unmapped.
- **Language.** `audioLanguageLabel` used `Locale.getDefault()`; the web's
  `languageLabel` fixes `Intl.DisplayNames(["en"], …)` — always English,
  regardless of the browser's own locale. A German phone showing
  "Deutsch · stereo · aac" would have been a new inconsistency this app
  doesn't have anywhere else (`PlaybackStatRows`' own language line is
  already fixed English for the same reason). Now fixed `Locale.ENGLISH`.

The Requirements section of the phase file said "in the device language" —
wrong against the web reference it cited, corrected there with a pointer to
this finding.

**Tests.** `AudioOptionsTest.codecsAreNamedTheWayFfprobeNamesThem`,
`labelsAreEnglishRegardlessOfDeviceLocale`; existing label tests moved off
three-letter (`deu`/`eng`) tags onto two-letter ones — see M2.

## M2 — language-code length: keep the shortening, document it

`Format.language` already shortens ISO 639-2 (`deu`, `ger`) to ISO 639-1
(`de`) by the time it reaches this app (media3's own container parsing);
the web's ffprobe-backed prober keeps whichever length ffprobe reports and
stores it verbatim. The `THREE_LETTER_TO_TWO_LETTER` table added to make
this phase's own tests pass (which had used three-letter tags to mirror the
web's fixtures) was therefore normalising an input shape real `Format`
objects never actually have on this device — dead weight, and it also used
a deprecated `Locale` constructor.

**Fix.** The table is removed from `AudioOptions.kt`; nothing outside the
old tests reached it. Tests now use two-letter tags throughout, matching
what a real device hands this code. The length difference itself is kept
and recorded as a deliberate difference in the phase file (harmless today —
preferences are per device and unsynced — but a future sync of this table
must normalise both sides before comparing them).

## M3 — a pick with no language could overwrite a good saved choice

`AudioChoiceController.choose` persisted `option.language` unconditionally.
An untagged or `und` pick — a commentary track with no language metadata,
say — would have written `null`/`"und"` over whatever real language was
already on record for the show, the moment the viewer picked it, even
though the web only ever writes a real language (`player.js`'s change
handler) and the server maps `und` to nothing worth keeping either.

**Fix.** Added `isUsableAudioLanguage` (`core:playback`, pure): not blank,
not `und`. `choose` still applies the pick and shows it selected for the
session, but only calls `rememberLanguage` when the pick is usable.

**Test.** `AudioChoiceControllerTest.pickingAnUntaggedTrackAppliesButDoesNotOverwriteTheSavedChoice`:
a remembered `en` on record, picks an untagged commentary track, asserts it
shows selected but `preferences.remembered` stays empty. `AudioOptionsTest`:
`aRealLanguageIsUsable`, `blankAndUndAreNotUsable`.

## Low

- **L1.** `choose` claimed a pick the moment it was asked for, even if
  `pinAudioTrack` failed (the group already gone from under this open).
  Now `choose` returns early on a failed pin, before touching `userChose`
  or anything else — the menu is left exactly as it was rather than
  showing a row as selected that never actually applied.
- **L2.** `pinAudioTrack` (renamed from `applyAudioOverride`) now takes the
  `Tracks` a caller's facts came from directly, rather than re-deriving a
  group from `player.currentTracks`, and confirms the group found there is
  still `C.TRACK_TYPE_AUDIO` before building anything — see H1.
- **L3.** Resolved by M1: labels are fixed English now, so
  `AudioOptionsTest` needs no `Locale.setDefault` and no longer depends on
  the JVM's default locale for anything.
- **L4.** `AudioChoiceControllerTest.releaseDetachesTheListenerFromThePlayer`:
  constructs a controller directly, verifies `addListener` ran
  (`TestScope.backgroundScope` + `runCurrent()`, since the collector runs
  on a background-scope coroutine that `advanceUntilIdle()` alone did not
  drive far enough to reach it), then verifies `release()` calls
  `removeListener` on the same player.

## What was fixed vs. documented

Fixed in code: C1, H1, H2/(b), M1, M3, L1, L2, L4. Documented rather than
changed: M2 (the language-code length difference — a deliberate,
harmless-today difference from the web, recorded in the phase file's new
"Deliberate difference" section) and L3 (resolved as a side effect of M1,
nothing further to do).

## Unresolved questions

None.
