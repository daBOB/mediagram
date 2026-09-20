# Phase 03 — Take the native bar away, keep the keyboard

**Priority:** last — it is the only phase that removes anything
**Status:** not started

## Context

- [plan.md](plan.md), [phase-02](phase-02-transport-and-choices.md)
- `web/public/index.html` — `<video id="video" controls …>`
- `web/public/lib/player.js` — `hudIsHeld`, `showHud`, `HUD_REST_MS`
- `web/public/style.css` — `dialog { color-scheme: dark }`, `.hud-bottom`
  padding, the `::-webkit-media-controls-*` rules

## Overview

Drop `controls`. By now everything it did has a counterpart except
picture-in-picture, which stays on the video's context menu by decision. What
it also silently did was the keyboard, and that has to be built here or the
player becomes mouse-only.

## Key insight

`hudIsHeld()` already refuses to hide the rails while playback is paused, and
already treats focus inside the dialog as a reason to stay up. That was
written for a rail of library actions and turns out to be exactly the rule a
transport bar needs — the same conclusion `ControlsVisibility.kt` reached on
the phone, independently. It needs no change; it only now matters much more,
because a hidden bar is a viewer who cannot pause.

## Requirements

- `controls` gone from the `<video>` element.
- Keyboard, on the dialog, ignored while focus is in a control that wants the
  key itself:

  | key | does |
  |---|---|
  | space, `k` | play/pause |
  | `←` `→` | skip ten, the same ten as the buttons |
  | `↑` `↓` | volume |
  | `m` | mute |
  | `f` | fullscreen |
  | `c` | subtitles on or off |
  | `0`–`9` | seek to that tenth |

  `Escape` is left to the dialog, which already closes on it.
- Focus order runs slider, transport, pickers, actions, close.
- The bar is reachable by tab even while resting, which `opacity` already
  allows for and `pointer-events: none` must not undo for the keyboard.
- Dead CSS removed: the `::-webkit-media-controls-*` rules, `.hud-bottom`'s
  clearance for a bar that is gone, and the half of the `color-scheme`
  comment that is about native controls.

## Architecture

New `web/public/lib/player-keys.js`, pure:

```js
keyAction(event, { inControl })  ->  null | { do: "playPause" | "skip" | … , by }
```

Pure for the same reason as `seek-model.js`: a key map is a table, and a
table can be proved. `player.js` binds the result to the transport built in
phase 02.

## Related code files

**Create**
- `web/public/lib/player-keys.js`, `web/test/player-keys.test.ts`

**Modify**
- `web/public/index.html` — the attribute, and the comment above the bottom
  rail that describes an arrangement that no longer exists
- `web/public/lib/player.js` — bind the keys
- `web/public/style.css` — remove what the native bar left behind
- `CLAUDE.md` — a Changelog line, and a note under Surface Parity that the web
  player now owns transport on both surfaces

## Implementation steps

1. `player-keys.js` and its tests, including the guard: a key typed into a
   `<select>`, `<input>` or the notes panel is not a transport command, and
   neither is one with ctrl, alt or meta held.
2. Bind on the dialog, `preventDefault` only for keys actually consumed —
   space must still scroll the notes panel when the notes panel has it.
3. Remove `controls`.
4. Sweep the dead CSS and the stale comments.
5. Check the resting rules: pause, let the bar rest, and confirm it can still
   be reached by tab and by pointer.
6. Firefox once, since phases 01 and 02 leaned on a Chromium-only way of
   hiding the native timeline that this phase makes moot.

## Todo

- [ ] `player-keys.js` + tests, guard included
- [ ] keys bound, `preventDefault` only where consumed
- [ ] `controls` removed
- [ ] dead CSS and stale comments gone
- [ ] tab order verified, resting bar still reachable
- [ ] Firefox check
- [ ] `CLAUDE.md` changelog entry, with sign-off before writing it
- [ ] version bumped in all three manifests
- [ ] `./scripts/check.sh`

## Success criteria

- One bar on screen, ours, in one visual language, fading on one clock.
- Nothing that worked before this plan is unreachable, picture-in-picture
  excepted and recorded.
- A keyboard-only viewer can open, play, seek, adjust and close a title.
- Firefox shows no second timeline.

## Risks

| Risk | Mitigation |
|---|---|
| A key steals a keystroke meant for a control | The guard is the first thing tested, not the last |
| Losing the native bar loses its ARIA | Labels landed in phase 02 and are checked here |
| `CLAUDE.md` edited without sign-off | §4 forbids it; the todo says ask |

## Next

Nothing in this plan. The open question it leaves is whether the phone should
gain the readouts the web bar has — `ends`, and how far ahead the buffer
is — which is piece **B** in the Android design's own table and not this work.
