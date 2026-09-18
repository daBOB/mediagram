# Phase 3 — Resume and continue watching

- `web/public/lib/watch-state.js` — loads `/api/state` once, holds it, and
  writes through. Failures are swallowed: a player that cannot save a position
  is still a player.
- Position saved every 10s while playing, on pause, on close, and on
  `pagehide` via `sendBeacon` — the one path where a normal request does not
  survive the page going away.
- Opening a title with a position seeks there. Direct play sets
  `currentTime`; a conversion starts there instead, which it already knows how
  to do. The note says so, because a film that silently starts 34 minutes in
  looks broken.
- Near the end is not a position: past 95% or within 60s of the end, a title
  starts again from the beginning and is counted watched.
- A `Continue` shelf, first in the masthead and the landing when it has
  anything in it. Plates carry a progress rule; rows carry one too.

Success: a position set on one client appears on another after a reload; a
finished title leaves the shelf; a title with no runtime never claims a
percentage.
