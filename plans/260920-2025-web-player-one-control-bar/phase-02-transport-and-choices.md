# Phase 02 — The transport, and the four choices beside it

**Priority:** second
**Status:** done

## Context

- [plan.md](plan.md), [phase-01](phase-01-one-timeline-either-way.md)
- `docs/superpowers/specs/2026-09-20-android-player-transport-controls-design.md`
  §3 — the shape this matches
- `web/public/lib/player.js` — `attachSubtitles`, `offerAudioTracks`,
  `refreshEnds`, the `.rail` listeners

## Overview

Build what the native bar does, beside the slider phase 01 made: play/pause,
skip ten either way, volume, speed, subtitles, fullscreen. The native bar is
still there and still works; nothing is taken away until phase 03. That makes
this phase additive and separately verifiable, and it means a bug found here
is a bug in a control nobody depends on yet.

## Key insight

Two of these are not new decisions. `attachSubtitles` already builds real
`<track>` elements and `offerAudioTracks` already owns a `<select>` — so
subtitles becoming ours is not a rebuild, it is moving a choice out of
Chromium's `⋮` to sit beside the sibling choice it should always have been
next to.

Ten seconds either way is not a preference. The phone shipped ten, after
`PlayerFactory.kt` had to set both increments because media3's defaults are
five back and fifteen forward — "a bar labelled 10 either way that moves 5 and
15 is a lie told by omission". The web player has no such default to fight,
but it owes the same number.

## Requirements

- Play/pause reflects `video.paused` and survives being changed from anywhere
  else (the keyboard in phase 03, `autoplay.js`, `up-next.js`).
- Skip ±10s clamps at nought and at the runtime, and moves film time, not
  encode time — so while converting it goes through the same restart path a
  drag does.
- Volume: a mute toggle and a slider, remembered per device in
  `localStorage`. Per device, not in watch state: a position belongs to the
  title, a volume belongs to the room.
- Speed: 0.75 / 1 / 1.25 / 1.5 / 1.75 / 2, client-side `playbackRate` for both
  playback modes. `ratechange` already refreshes `ends`.
- Subtitles: "Off" plus one option per attached track, driven by
  `textTracks[i].mode`.
- Fullscreen: toggles on the dialog, tracks `fullscreenchange`.
- Every control carries a real label; the slider reports a time, not a number.

## Architecture

```
web/public/lib/transport.js     buttons, their state, their labels
web/public/lib/volume-store.js  read/write the remembered volume
```

`player.js` is 848 lines against a 200-line guideline; none of this goes in
it. It keeps `openPlayer` and the lifecycle and imports the rest.

The skip buttons call the same seek entry point the slider's `change` uses, so
"where does a seek go while converting" is answered in one place.

## Related code files

**Create**
- `web/public/lib/transport.js`
- `web/public/lib/volume-store.js`
- `web/test/transport.test.ts`, `web/test/volume-store.test.ts`

**Modify**
- `web/public/index.html` — the bar's three rows
- `web/public/style.css` — the bar, and its collapse under 720px
- `web/public/lib/player.js` — wire it, and route skips through the seek path

## Implementation steps

1. `volume-store.js` first: `localStorage` throws in some contexts and the
   read must survive it.
2. `transport.js` exposing `mountTransport({ video, onSeekTo })`, returning a
   `refresh()` the player calls on `play`, `pause`, `ratechange`,
   `volumechange`, `loadedmetadata` and on opening a title.
3. Markup: row 1 readouts and library actions, row 2 the slider, row 3
   transport centred with the pickers left and fullscreen right.
4. Subtitles picker, built where `attachSubtitles` attaches.
5. Speed and volume.
6. Fullscreen, and its label following `document.fullscreenElement`.
7. Collapse rules for 720px and 560px: the readouts go first, then the
   pickers wrap, then the skip buttons — play/pause and the slider are the
   last things standing.

## Todo

- [ ] `volume-store.js` survives a throwing `localStorage`
- [ ] `transport.js` + tests for the pure parts (labels, clamping, the
      enabled/disabled rules)
- [ ] play/pause, correct after every route that changes playback
- [ ] skip ±10 clamped, and routed through the converting seek path
- [ ] volume + mute, remembered
- [ ] speed, with `ends` still truthful
- [ ] subtitles beside audio
- [ ] fullscreen
- [ ] labels and `aria-valuetext` on the slider
- [ ] narrow layouts at 720px and 560px
- [ ] `./scripts/check.sh`

## Success criteria

- Every native control except picture-in-picture has a working counterpart.
- Pressing play in our bar and in the native bar do the same thing, and each
  updates the other.
- A 2.39:1 film at 1.5x still reports the right finishing time.
- At 560px nothing overlaps and nothing is unreachable.

## Security

Nothing new is exposed. The subtitle picker names languages the catalog
already sends; no `chat_id`, `message_id` or `doc_id` reaches the page, and
this phase adds no route.

## Risks

| Risk | Mitigation |
|---|---|
| Two bars can disagree about paused state | Both read `video`; neither holds its own copy |
| `localStorage` blocked | Wrapped; the player falls back to full volume |
| The bar grows past what a phone can hold | The collapse order is decided above, not discovered later |

## Next

Phase 03 removes the native bar and picks up the keyboard it took with it.
