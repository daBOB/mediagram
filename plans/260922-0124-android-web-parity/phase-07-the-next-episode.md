# Phase 7: The next episode

**Status:** Not started — sketch

**Deliverable:** An episode that ends offers the next one, and starts it when
the viewer lets it. Leaving the player still returns to the description screen,
as it does today.

## Context

- Phase 5 — what has been watched, and where the viewer is
- `web/public/lib/up-next.js:16-45` — the card appears in the last thirty
  seconds, counts down ten once the title actually ends, and a cancel is
  remembered for that title
- `web/public/lib/autoplay.js:28-39` — **the rule that is not obvious**: an
  unattended start waits for about sixty seconds of buffer, or gives up waiting
  after forty-five, while a start the viewer asked for begins as soon as it can
- `web/public/lib/player.js:603-609` — the standing "Play next" button that
  survives a cancelled countdown

## The shape

The buffer-before-autoplay rule matters more here than on the web. A phone on a
metered link that autoplays into a stall has spent the viewer's data to show
them a spinner. `autoplay.js` already answers it and the answer is portable.

Needs: the ordering that says which episode is next, which `Shelves.kt` already
computes; the countdown card; the standing button.

## Todo

- [ ] the next-episode rule, over `Shelves.kt`'s ordering
- [ ] the up-next card and its countdown, with cancel remembered per title
- [ ] the buffer-before-start rule, ported
- [ ] a real device run to the end of an episode, on wifi and on a throttled link
- [ ] `./scripts/check.sh`
