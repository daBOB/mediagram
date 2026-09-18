# Phase 01 — Per-title technical line

**Priority:** first (page-only, immediately visible) · **Status:** complete

## Overview

`codecLine(set)` gives `container · vcodec · acodec`. Add a fuller sibling
that also carries resolution, HDR, size, part count and average bitrate, and
show it where only a badge speaks now.

## Key insights

- Every field is already in the catalog row the page holds; no route changes.
- Average bitrate (`total / duration`) is what makes the `needs transcode`
  badge legible, and has never been shown.
- `SDR` is the absence of a fact. Omit it; show `HDR10`, `HLG`, `DV`.

## Requirements

- `technicalLine(set)` in `web/public/lib/format.js`, declared in `format.d.ts`.
- Tolerates every field being null — a row with no duration shows no bitrate
  rather than `NaN Mbps`.
- `codecLine` unchanged; `search-view.js` and `course-view.js` untouched.

## Related code files

- Modify: `web/public/lib/format.js`, `web/public/lib/format.d.ts`
- Modify: `web/public/lib/shelf-view.js` (card), `web/public/lib/player.js` (HUD)
- Modify: `web/test/format.test.ts`

## Implementation steps

1. Add `bitrateLabel(set)` (private) and `technicalLine(set)` to `format.js`.
2. Declare both in `format.d.ts`.
3. Render it on the shelf card beneath the title, and in the player HUD.
4. Extend `web/test/format.test.ts`.

## Todo

- [ ] `technicalLine` + null handling
- [ ] `.d.ts` declarations
- [ ] shelf card
- [ ] player HUD
- [ ] tests

## Success criteria

`technicalLine` on a full row reads
`1080p · HDR10 · MKV · HEVC · EAC3 · 14.2 GB · 5 parts · 9.4 Mbps`; on an
empty row it is `""`. `bun test` green.

## Risks

Card layout crowding at narrow widths — mitigated by letting the line wrap
and setting it smaller than the title.
