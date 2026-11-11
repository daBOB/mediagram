---
title: Native game-night controller apps (Android + iOS via xtool on Linux)
author: Andre
date: 2026-11-08
id: l-0edcee14
scope: repo
status: active
touches:
  - docs/superpowers/specs/2026-11-07-controller-apps-design.md
---

## Decided
2026-11-07 the user asked for native controller apps: "to have more control over the device. awake, vibration, drawing on device".
Spec: docs/superpowers/specs/2026-11-07-controller-apps-design.md, on main since the spieltisch merge d5000d74 (worktree and branch removed 2026-11-07).

User decisions: household AND guests play; iPhones sideloaded with a FREE Apple ID (7-day signing → weekly refresh needed);
build iOS with xtool on Linux (github.com/xtool-org/xtool), Sideloadly as backup (Windows/macOS only); Android = a SEPARATE
controller app, not a screen in the media app; app finds the box on Wi-Fi by itself and joins the lobby; vibrate on your turn,
buzzer accepted, wrong answer, game won; an iPad mini is kept specially for drawing games (no vibration motor; Apple Pencil).

My calls (unconfirmed, in the spec): thin web-view shell around the box's phone page + JS bridge; DNS-SD discovery with QR
fallback; name "Spieltisch"; player memory kept by the app (box DHCP moves).

**Why:** browser page on plain http can't hold Wake Lock or vibrate on iPhone (game-night-phones-cannot-be-kept-awake).

## Watch out
guests without the app keep the browser path unchanged; see game-night-backlog.

_From Claude Code's memory of this repo, last updated Nov 7._
