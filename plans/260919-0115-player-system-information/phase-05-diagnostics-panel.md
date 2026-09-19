# Phase 05 — Diagnostics panel

**Priority:** last · **Status:** complete · **Depends on:** phase 04

## Overview

A `#/status` view that renders the snapshot, reachable only by someone the
server places on this network.

## Key insights

- Discovery is the whole design question. A masthead link would advertise to
  a remote viewer something they cannot open; a link rendered only when
  `/api/status` answers 200 tells them nothing.
- Polling that never stops is the failure mode of every panel like this.

## Requirements

- `#/status`, routed like every other section in `app.js`.
- One link in the colophon, rendered only on a 200 from `/api/status`.
- Polls every 2s while the view is open; stops on navigating away.
- Set in the page's own faces as definition rows, not a monospace grid.

## Related code files

- Create: `web/public/lib/status-view.js`
- Modify: `web/public/app.js` (route + colophon link), `web/public/style.css`
- Create: `web/test/status-view.test.ts`

## Implementation steps

1. `renderStatus(root, snapshot)` — pure DOM building, no fetching.
2. A small poller that owns its interval and is cancelled by the router.
3. Route `#/status` in `app.js`; probe `/api/status` once at startup and
   render the colophon link only on 200.
4. Styles beside the existing `.colophon` rules.
5. Test the pure renderer against a fixture snapshot.

## Todo

- [ ] `renderStatus`
- [ ] poller with cancellation
- [ ] route + conditional link
- [ ] styles
- [ ] tests

## Success criteria

`#/status` shows catalogue, cache, encoder, transcodes, telegram, state and
uptime, refreshing every 2s; navigating away stops the interval; a remote
viewer sees no link and gets the empty state at `#/status`.

## Risks

An interval left running after navigation — the router cancels it on every
route change, and the test asserts the cancel.
