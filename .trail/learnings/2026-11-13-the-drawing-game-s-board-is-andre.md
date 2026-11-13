---
title: "The drawing game's board is native on the iPad and talks to the box through the phone page, not its own connection"
author: Andre
date: 2026-11-13
branch: main
cost: { minutes: 34, tokens: 436433 }
id: l-625e565f
scope: repo
status: active
src: agent
touches:
  - web/test/player-initialization.test.ts
  - web/public/lib/playback/player-library-marks.js
  - web/test/player-features.test.ts
  - docs/superpowers/specs/2026-11-13-clip-a-scene-design.md
  - web/public/lib/playback/ab-loop.js
  - web/public/lib/playback/clip-flow.js
  - web/public/lib/playback/clip-panel-view.js
  - web/public/lib/playback/clip-panel.js
  - web/public/lib/playback/player-menus.js
  - web/public/lib/playback/transport.js
---

## Decided
2026-11-13, user: the drawing game (Kritzelei) gets a native drawing board in the iOS app, iOS first, Android later. The iPad mini is the board, not a player; it is handed to whoever draws. Guessing is out loud; the drawer taps who got it. Layout: tools in one row under the paper.
The page stays in charge: the iPad keeps the box's phone page for lobby, joining and reconnecting; the page asks the app over the bridge to show the native board, and strokes/taps come back over the bridge and go to the box on the page's one WebSocket. Swift has no networking.
The iPad mini is plugged in early: the first build step measures Pencil -> TV delay (target ~0.2 s) before game rules are built.
The iOS app joins the Spieltisch version line (user approved, CLAUDE.md line to follow with the design).

## Tried and ruled out
- Board with its own connection to the box (native lobby): repeats seats, tokens, reconnect and kids rules in Swift, two lobbies to keep in step, no visible gain.
- Page lobby plus a second socket only for strokes: two connections to keep in step for nothing.
- A web canvas board: user chose native.
- PencilKit's PKCanvasView: it reports a stroke only after the pen lifts, so the TV could not show the line live; needs a custom UIView with coalesced touches and force.

## Watch out
- Views are whole snapshots flushed every 50 ms; a growing drawing inside the TV snapshot would be re-parsed 20x/s on the TV box. Send the TV only appended strokes.
- buzz-moments.ts never buzzes on Wait or on a good Outcome (a test checks it); Kritzelei must ask for its buzzes explicitly.
- The 2026-11-07 controller-apps spec's "one set of screens" anti-goal: the board is a deliberate exception, the only native game screen.

In the code: docs/superpowers/specs/2026-11-13-kritzelei-drawing-game-design.md (+ 2026-11-13-kritzelei-look.html); ios/controller/Sources/Spieltisch/GameWebView.swift (bridge window.mediagramApp); crates/mediagram-game/client/src/net.ts (/ws); crates/mediagram-game/src/room_loop.rs TICK 50 ms; ios/controller/Info.plist version 0.153.3.
