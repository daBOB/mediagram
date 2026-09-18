# Phase 4 — Fullscreen HUD

**Priority:** last, shows phases 1–3. **Status:** done.

## Design

The dialog fills the viewport; the video is `object-fit: contain` across all
of it. Two floating rails over the picture:

- **top** — title, and close.
- **bottom** — jump slider, position and end time, audio chooser, notes
  toggle, and the conversion note under them.

Both fade out after the pointer has rested, and return on movement, on focus,
or whenever playback is paused. Anything focused keeps them up, so a keyboard
viewer never loses the controls they are tabbing through.

The summary stops opening by itself: a page of German notes over a picture is
not a caption. It becomes a toggle that is only offered when the set has one.

**Preload without playing:** `preload="auto"` and no `video.play()` anywhere.
The transcode still begins at open — the server has to encode before there is
anything to buffer — but nothing starts moving until the viewer says so.

## Audio chooser

A native `<select>`: it is a menu of one-from-many, it is reachable by
keyboard for free, and a hand-built listbox here would be a worse version of
something the platform ships. Labels come from `subtitle-label.js`, which is
renamed to `language-label.js` — it was never subtitle-specific.

Choosing a track restarts the conversion at the current position. On a title
that was playing directly it *starts* one, and the note says why.

## Files

- modify `web/public/index.html`, `style.css`, `lib/player.js`
- create `web/public/lib/audio-chooser.js`
- rename `lib/subtitle-label.js` → `lib/language-label.js`
- modify `web/test/subtitle-label.test.ts` → `language-label.test.ts`

## Success criteria

- No autoplay: opening a title buffers and waits.
- HUD hides on rest, returns on move, never hides while focus is inside it.
- Chooser absent for a single-track title, present for the 26 that have more.
- Esc still closes, and closing still tears down the encode.
