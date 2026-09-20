# Phase 04 — Handling

**Status:** done

## Context

- `web/public/lib/player-keys.js` — the table this extends
- `web/public/lib/transport.js` — `act`, which already routes what the table decides
- `web/public/style.css` — `video { object-fit: contain }`, and why

## What this is

The things that make a player feel like a tool rather than a page. All of it
is the browser's own machinery; none of it touches the server.

| | |
|---|---|
| **Picture-in-picture** | `requestPictureInPicture()`. Left out when the bar was built because it was the only control nobody had asked for. Somebody has now. |
| **Frame stepping** | `,` and `.` — pause, then move one frame. `currentTime += 1/fps`, and the index knows no fps, so it is a nominal 1/24 with the honesty to say so. |
| **A/B loop** | `a` sets the start, `b` the end, `a` again clears. A lecture's worth re-watching is a lecture worth looping. |
| **Zoom and aspect** | `z` cycles fit · fill · 16:9 · 4:3. `object-fit` and a transform, for files mastered with the bars baked in. |
| **Speed by keyboard** | `[` and `]` step the picker that already exists. |

## The one that is not obvious

**Frame stepping without a frame rate.** The index stores no fps and probing
for it is a round trip this does not want. A step of 1/24s is a frame on a
24fps film, slightly less than one at 25, and about half a frame at 50 — so it
is a *nominal* step, and the readout says `≈` rather than pretending.

`requestVideoFrameCallback` would give the true answer, but only while
playing, which is exactly when nobody is stepping.

## Files

**Modify** `web/public/lib/player-keys.js` (+ its test),
`web/public/lib/transport.js`, `web/public/index.html`,
`web/public/style.css`

**Create** `web/public/lib/framing.js` + `web/test/framing.test.ts`,
`web/public/lib/ab-loop.js` + `web/test/ab-loop.test.ts`

## Todo

- [ ] the key table grows `,` `.` `[` `]` `a` `b` `p` `z`, guard tests first
- [ ] frame step, and a readout that admits it is nominal
- [ ] A/B loop, including b-before-a, and clearing
- [ ] `framing.js`: the cycle, and what each does to `object-fit`
- [ ] picture-in-picture, and the bar staying right when the viewer leaves it
- [ ] `./scripts/check.sh`

## Success criteria

- Every new key is guarded exactly as the existing ones are: a field keeps
  them, a button keeps space and Enter, a modifier gives them to the browser.
- A loop set across a seek still loops.
- Leaving picture-in-picture does not leave the bar claiming the film is
  somewhere it is not.
- Zoom is remembered for the title, not for the player: a 2.39:1 film
  cropped to fill must not crop the next thing opened.

## Risks

| Risk | Mitigation |
|---|---|
| Eight more keys is eight more collisions | The table is one place and the guard is tested before the keys are |
| PiP and fullscreen fight | Both read `document.pictureInPictureElement` / `fullscreenElement` rather than keeping their own flag |
