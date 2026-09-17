---
phase: 4
title: "Minimal web UI, direct play"
status: completed
priority: P1
effort: "1d"
dependencies: [2, 3]
---

# Phase 4: Minimal web UI, direct play

## Overview
A Bun app that lists the library and plays a title in a browser. No
transcoding: this phase proves Telegram to browser end to end, and makes the
162 course lessons watchable on the LAN.

## Key insight
The lessons are `mp4/h264/aac`, which every browser plays natively, so the
first useful version needs no ffmpeg at all. Deferring transcoding to phase 6
means the hard part (Range, seeking, part boundaries) is proven on its own,
with a `<video>` element as the test harness.

## Requirements
- Functional: a catalog page; clicking a title plays it, seekable; a title the
  browser cannot play says so plainly rather than failing silently.
- Non-functional: the UI is small enough that a framework would cost more
  than it saves.

## Architecture
```
Bun.serve
  GET  /                    static page
  GET  /api/sets            the catalog, from phase 2
  GET  /api/sets/:id/stream the bytes, from phase 2
```

No proxy: phase 2 put those routes in this same process. One origin, and the
`<video>` element's Range requests reach the part walk directly.

Playability is decided from the codec fields the catalog already returns:
`container`, `vcodec`, `acodec`. A small table marks `mp4/h264/aac` playable
and `mkv/*/ac3` not, which is honest about what phase 6 will fix.

## Related Code Files
- Create: `web/package.json`, `web/src/server.ts` (Bun.serve, proxy),
  `web/src/playable.ts` (pure: codec table), `web/public/index.html`,
  `web/public/app.js`
- Modify: `README.md` (running the player)

## Implementation Steps
1. `bun init` under `web/`. TypeScript, no framework: a list and a `<video>`.
2. `playable.ts`: pure function from container and codecs to
   `DirectPlay | NeedsTranscode(reason)`. Tested; this is the one piece with
   logic rather than glue.
3. Wire the catalog and stream routes from phase 2 into the same server that
   serves the page, so the browser sees one origin.
4. Catalog page: title, kind, duration, size, and a badge for titles that
   cannot direct-play yet.
5. Player page: `<video src="/api/sets/:id/stream">`, nothing more.
6. Document how to run it, including where the session string and the package
   URL come from.

## Success Criteria
- [ ] The catalog lists the same sets `mediagram serve` reports for the same
      index
- [ ] A course lesson plays and seeks in Chrome and Firefox
- [ ] Seeking issues a Range request and does not restart the download
- [ ] A film is listed but marked as needing transcoding, with the reason
- [ ] `playable.ts` has tests for every codec combination in the library
- [ ] The player binds to loopback

## Risk Assessment
- **Range broken between the page and the routes** is the likely bug here,
  and it looks like "seeking is slow" rather than an error. Assert the 206 and
  `Content-Range` in a test, not by hand.
- **Bun version churn.** Pin the Bun version in `package.json` so the app does
  not drift with whatever is installed.
- **Framework creep.** A catalog and a `<video>` do not need one. If the UI
  grows past that, decide then, not now.
