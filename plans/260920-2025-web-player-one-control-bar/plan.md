# The web player gets one control bar

The picture has two sets of controls stacked at the bottom: Chromium's, from
`<video controls>`, and this player's own rail floating above it. A viewer
asked which is which and could not tell. This replaces both with one bar the
player owns.

## Why this, and not the other way round

Three things collide today, and none of them is a styling accident:

- **The scrubber is ours, the play button is theirs**, sixty pixels apart, in
  two typefaces. While converting, our slider is the only true timeline and
  the native one is hidden — so the bar a viewer reaches for is split in half
  by which part of it happens to be truthful.
- **They rest on different clocks.** `dialog.resting` fades our rail after
  2.6s; Chromium fades its bar on its own rules. One is up, or the other, or
  both.
- **Audio is ours, subtitles are theirs.** The same kind of choice, a
  `<select>` in our rail and a menu behind Chromium's `⋮`.

The Android player answered this in
`docs/superpowers/specs/2026-09-20-android-player-transport-controls-design.md`
§2 by owning transport outright, and shipped it. Under **Surface Parity** in
`CLAUDE.md` the web player is normally the reference — here it is the surface
that is behind, and the phone's bar is the shape to match.

## What it costs

`<video controls>` is free volume, speed, captions, fullscreen, picture-in-
picture, keyboard and ARIA. Taking it away means owing all of it. Every phase
below therefore leaves the player no less capable than it found it, and the
native bar is removed only once everything it did has somewhere else to live.

Picture-in-picture is the one thing deliberately not rebuilt: Chromium still
offers it on the video's context menu, and a button for it would be the only
control on the bar that no one asked for.

## Phases

| | Phase | Status |
|---|---|---|
| 01 | [One timeline, whichever way it plays](phase-01-one-timeline-either-way.md) | done — `fab5913` |
| 02 | [The transport, and the four choices beside it](phase-02-transport-and-choices.md) | done — `cb4363a` |
| 03 | [Take the native bar away, keep the keyboard](phase-03-native-bar-away.md) | done |

## Dependencies

- 02 needs 01's `transport.js` module and its `seekModel` seam.
- 03 needs 02: it removes the only remaining route to volume and subtitles.
- Nothing here touches the server, the cache, or watch state.

## Verification

The stub harness, never the real player — it holds the account's MTProto auth
key. `scratchpad/stub-real.ts` serves the real routes over a fake `ByteSource`;
`stub-offline.ts` proves playback from cache with a throwing upstream.
