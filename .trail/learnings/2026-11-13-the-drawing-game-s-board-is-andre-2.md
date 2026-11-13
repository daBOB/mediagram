---
title: "The drawing game's board is native on the 5th-generation iPad, finger only, and talks to the box through the phone page"
author: Andre
date: 2026-11-13
branch: main
cost: { minutes: 1, tokens: 3171 }
id: l-e7408a44
scope: repo
status: active
replaces: l-625e565f
src: agent
touches:
  - CLAUDE.md
  - ios/controller/Package.swift
  - ios/controller/Sources/Spieltisch/GameWebView.swift
  - crates/mediagram-game/client/src/net.ts
  - crates/mediagram-game/src/room_loop.rs
  - ios/controller/Info.plist
---

## Decided
2026-11-13, user: the drawing game (Kritzelei) gets a native drawing board in the iOS app, iOS first, Android later. The iPad is the board, not a player; it is handed to whoever draws. Guessing is out loud; the drawer taps who got it. Layout: tools in one row under the paper.
The drawing iPad is an iPad (5th generation, iPad6,11) on iOS 16.7.12 ("i have another ipad with ios 16"). No Apple Pencil works with it and it senses no pressure, so the board is finger only: no pressure, one touch draws at a time. The paper is 16:9, which is what layout A leaves on 1024x768 pt and matches the TV.
The page stays in charge: the iPad keeps the box's phone page for lobby, joining and reconnecting; the page asks the app over the bridge to show the native board, and strokes/taps come back over the bridge and go to the box on the page's one WebSocket. Swift has no networking.
The iPad is plugged in early: the first build step measures finger -> TV delay (target ~0.2 s) before game rules are built.
The iOS app joins the Spieltisch version line (user approved, CLAUDE.md line to follow with the design).

## Tried and ruled out
- The iPad mini 2 (iPad4,5) the household also has: stuck on iOS 12.5.7, the app targets iOS 16 and uses SwiftUI, and it has no Pencil support either.
- Board with its own connection to the box (native lobby): repeats seats, tokens, reconnect and kids rules in Swift, two lobbies to keep in step, no visible gain.
- Page lobby plus a second socket only for strokes: two connections to keep in step for nothing.
- A web canvas board: user chose native.
- PencilKit's PKCanvasView: it reports a stroke only after the touch lifts, so the TV could not show the line live; needs a custom UIView with coalesced touches.

## Watch out
- Older notes say "iPad mini with Apple Pencil" for drawing; the real drawing iPad has no Pencil support.
- Views are whole snapshots flushed every 50 ms; a growing drawing inside the TV snapshot would be re-parsed 20x/s on the TV box. Send the TV only appended strokes.
- buzz-moments.ts never buzzes on Wait or on a good Outcome (a test checks it); Kritzelei must ask for its buzzes explicitly.
- The 2026-11-07 controller-apps spec's "one set of screens" anti-goal: the board is a deliberate exception, the only native game screen.

In the code: docs/superpowers/specs/2026-11-13-kritzelei-drawing-game-design.md (+ 2026-11-13-kritzelei-look.html); ios/controller/Package.swift (platforms .iOS(.v16)); ios/controller/Sources/Spieltisch/GameWebView.swift (bridge window.mediagramApp); crates/mediagram-game/client/src/net.ts (/ws); crates/mediagram-game/src/room_loop.rs TICK 50 ms; ios/controller/Info.plist version 0.153.3.
