# Phase 03 — Subtitles that behave

**Status:** done, minus the position control

## Context

- [phase-02](phase-02-remembered-choices.md) — where the choices are kept
- `web/public/lib/player.js` — `attachSubtitles`
- `web/public/lib/transport.js` — `subtitleOptions`, the picker
- `web/src/routes.ts:474` — `/api/sets/<id>/subtitles/<lang>.vtt`

## The problem

206 sets have subtitles and the only thing a viewer can do is turn them off.
If a track runs half a second early it stays half a second early. If the text
is too small on a television across a room, it stays too small.

## The change

A small panel behind a button on the bar, not a menu of menus:

```
  Size      ────────●────      Sync   − 0.4s +
  Position  bottom ▾                  reset
  Backing   none · shadow · box
```

**Size, position and backing are `::cue`.** The browser already renders the
track; this styles what it renders. No re-fetch, no reload, no re-encode.

**Sync is not.** `::cue` cannot move a cue in time, and neither can a
`<track>` whose file is fixed. The offset is applied by rewriting the cue
times of the loaded `TextTrack` in place — the cues are objects with
`startTime` and `endTime`, and they can be moved.

That has one consequence worth writing down: the offset must be re-applied
whenever the track is re-loaded, because a fresh `<track>` arrives with the
file's original times. A conversion re-attaches its tracks, so this is not
rare.

All four are remembered per show by phase 02, because a badly-timed subtitle
file is badly timed for every episode of the series it came with.

## Files

**Modify** `web/public/index.html`, `web/public/style.css`,
`web/public/lib/player.js`, `web/public/lib/transport.js`

**Create** `web/public/lib/subtitle-style.js` + `web/test/subtitle-style.test.ts`

## Todo

- [ ] `cueStyle({size, position, backing})` → the rule text, pure and tested
- [ ] `shiftCues(track, seconds)`, and a test that a shift of nought is not a
      rebuild and a shift twice is not applied twice
- [ ] the panel, reachable by keyboard, closing the way the collection picker does
- [ ] re-applied after a conversion re-attaches the tracks
- [ ] remembered per show
- [ ] `./scripts/check.sh`

## Success criteria

- A subtitle that ran early is on time, and stays on time across a seek, a
  bitrate switch and an audio-language change.
- Size and position survive a reload and the next episode.
- Turning subtitles off and on again does not lose the offset.

## Risks

| Risk | Mitigation |
|---|---|
| Cue times drift by being shifted twice | The offset is held as an absolute, and applying it sets times from the original, not from the current |
| `::cue` support varies | Size and colour are broadly supported; position falls back to the browser's own placement rather than to nothing |


## What shipped, and what did not

Size, backing and sync shipped. **Position did not**, and the attempt is worth
recording so nobody spends the afternoon again.

Raising subtitles clear of the transport bar needs the cue box's *bottom*
anchored, which in WebVTT means `snapToLines: false` and a percentage `line`.
Chromium then stops wrapping: a 226-character cue rendered as a single line
off the right edge of the picture. Setting `size`, `position`, `positionAlign`
and `align` explicitly made no difference — tested, twice, with screenshots.

With `snapToLines: true` the text wraps, but a negative `line` anchors the
cue's *first* row and the rest grow downward, so a three-row cue covers the
bar anyway and a four-row one runs off the bottom edge.

Neither does what a control called "Position" would have promised, so the
browser's own placement is left alone: bottom, growing upward, wrapping. The
bar rests after 2.6s, which is the real mitigation.
