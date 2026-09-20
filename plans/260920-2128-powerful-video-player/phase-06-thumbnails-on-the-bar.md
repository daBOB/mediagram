# Phase 06 — Thumbnails on the bar

**Status:** not started

## Context

- `web/src/package/posters.ts` — a generated asset that already has a home
- `web/src/cache/` — the chunk cache, and why a sprite must not go in it
- `web/src/transcode/ffmpeg.ts` — the runner this borrows
- `web/public/lib/seek-model.js` — the bar the preview hangs off

## What it is

Hovering the scrub bar shows the frame you would land on. It is the single
thing that most makes a player feel like it knows what it is holding, and it
is the most expensive thing in this plan, which is why it is last.

## The cost, honestly

A sprite sheet per set: one frame every few seconds, tiled into a grid, as one
image. For a 20-minute episode at one frame per 5s that is 240 frames; at
160×90 in a grid that is an image of a few hundred kilobytes.

Generated once, from the same bytes the player already pulls. **That is the
part to be careful about**: a film is 5 GB in a private channel, and
generating a sprite means reading all of it. So it is generated from what the
cache already holds, opportunistically, and never by pulling a whole file
from Telegram to make a picture out of it.

Which gives the honest behaviour: **a title that has been watched has
previews; one that has not, does not.** A viewer scrubbing a film they have
never opened gets the bar they have now.

## The alternative considered

Asking ffmpeg for a single frame per hover, on demand. Rejected: each one is a
seek into a remote file, the hover is a gesture that fires many times a
second, and the result would be a request storm against the channel for
frames nobody will look at twice.

## Files

**Create** `web/src/thumbs/sprite.ts`, `web/src/thumbs/routes.ts`,
`web/public/lib/thumb-strip.js`, and tests for the three

**Modify** `web/src/routes.ts`, `web/public/lib/player.js`,
`web/public/style.css`

## Todo

- [ ] `spritePlan(duration)` → interval, columns, tile size — pure, tested
- [ ] generation from cached bytes only, refusing to reach upstream
- [ ] stored beside posters, not in the chunk cache, and counted in the
      System panel's disk figure like everything else
- [ ] a route that 404s for a set with no sheet, which is the ordinary case
- [ ] the hover preview, and the same on touch by dragging
- [ ] no preview is not a broken preview: the bar behaves exactly as now
- [ ] `./scripts/check.sh`

## Success criteria

- Hovering a watched title shows the frame at that position.
- Hovering an unwatched one shows what it shows today.
- Generating a sheet never causes a Telegram fetch.
- The System panel's disk total includes the sheets.

## Risks

| Risk | Mitigation |
|---|---|
| Sheets quietly fill the disk | Counted and capped alongside the chunk cache, with the same budget the viewer already sets |
| Generation competes with playback for the GPU | Frames are a scale-and-tile, not an encode; run at low priority and never while a transcode is live |
