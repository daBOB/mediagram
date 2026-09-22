# Phase 1: Audio a viewer can choose

**Status:** Not started

**Deliverable:** A track button in the player that lists the audio the file
actually carries and switches to the chosen one mid-playback, with the labels
derived by a pure, tested function. Nothing is remembered yet — phase 6 does
that.

## Context

- `android/feature/player/src/main/kotlin/PlayerViewModel.kt` — exposes the
  `Player`; the module documentation states that media3's state holders own
  transport and this follows it rather than routing a fifth control through
  `PlayerHandle`
- `android/ui-mobile/src/main/kotlin/PlayerControls.kt` — the bar, four buttons
  and a slider today; `PlayerControlParts.kt` holds the glyph button
- `android/ui-mobile/src/main/kotlin/PlaybackStatRows.kt:64` — already reads the
  decoded audio format's codec, channel count and language for the overlay. It
  is the proof the facts are reachable; it is a readout, not a chooser
- `web/public/lib/audio-chooser.js:19-114` — **the reference.** What a label
  says, in what order, and that a choice is remembered by *language* and never
  by ordinal
- `web/public/lib/language-label.js:17-37` — `und` falls back to caller text

## Key insight

**This is the one gap where the phone can beat the reference surface, and it is
cheap.** The web player cannot switch a `<video>`'s internal audio stream, so
`player.js:837-854` restarts the whole conversion — a re-encode of the film to
change language. ExoPlayer selects the track inside the decoder: a
`TrackSelectionParameters` override, no re-fetch, no re-encode, and the bytes
already buffered stay useful.

312 of 566 sets carry more than one audio language. None of them is reachable
on the phone today.

## What gets built

**`AudioOptions.kt`, pure, in `:core:playback`.** Takes media3 `Tracks` and
returns an ordered list of `AudioOption(id, label, language, selected)`. The
label follows `audio-chooser.js`: language name, then the track's own title if
it has one, then the channel layout as a word — mono, stereo, 5.1, 7.1 — then
the codec. A file with one audio track returns an empty list, because a chooser
offering one thing is a control that does nothing.

Language naming is `Locale.forLanguageTag(tag).getDisplayLanguage()`, the
counterpart of the web's `Intl.DisplayNames`; `und` and blank fall back to the
track title, then to "Track n". Pure Kotlin and JVM-testable, so no Robolectric.

**A button and a menu in `PlayerControls.kt`.** Shown only when
`AudioOptions` returns more than one. The bar is already five controls wide on a
phone, so this and phase 2's subtitle control share one menu button rather than
each taking a slot — decided here because phase 2 inherits it.

**The override.** `Player.trackSelectionParameters` with a
`TrackSelectionOverride` for the chosen group. Applied on the `Player` the
ViewModel exposes, not through `PlayerHandle`.

## Open questions

- **Where the menu lives.** A `DropdownMenu` over the video, or a bottom sheet.
  A sheet is easier to hit on a phone and is what a fullscreen player usually
  does; a dropdown matches `AppChrome`. Decide when the bar is in front of you.

## Success criteria

- On the phone on `adb`, a film with German and English audio switches language
  from the bar, mid-playback, with the picture never stopping and no refetch
  visible in the stats overlay's `reads` row.
- A single-audio film shows no audio entry at all.
- `AudioOptions` is tested for: two languages, one language, a track with no
  language tag, a track with a title and no language, 5.1 and stereo labels.
- `./scripts/check.sh` passes.

## Risks

- **Tracks are not known at `prepare()`.** They arrive with
  `onTracksChanged`. The menu has to recompose when they do rather than reading
  once on open.
- **A channel-count-to-word map is a guess for unusual layouts.** 6 is not
  always 5.1. Fall back to "N channels" rather than naming a layout wrongly.

## Todo

- [ ] `AudioOptions.kt` in `:core:playback`, pure, with its tests
- [ ] one menu button in `PlayerControls.kt`, shared with phase 2
- [ ] the override applied to the exposed `Player`
- [ ] a real device run on a two-language film
- [ ] `./scripts/check.sh`
