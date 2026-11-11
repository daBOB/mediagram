---
title: "Funstation pairs controllers through Bigscreen's own Bluetooth page, not a custom screen"
author: Andre
date: 2026-11-11
branch: main
cost: { minutes: 24, tokens: 149880 }
id: l-66a1e24d
scope: repo
status: active
src: agent
touches:
  - scripts/setup-funstation.sh
---

## Decided
2026-11-11 the user chose Plasma Bigscreen's built-in Bluetooth settings page, opened from a "Controllers" tile on the Bigscreen home, as the way to pair more Bluetooth controllers and remotes on funstation. It is already styled for the TV and works with the Chromecast remote.

## Tried and ruled out
A custom Controllers screen (big cards, only controllers listed, pairing steps per brand, battery levels) was offered and not chosen: more work for something Bigscreen already ships. Revisit only if the built-in page turns out unusable.

## Watch out
- The page lists every nearby Bluetooth device (neighbours' TVs, headsets), not just controllers.
- When it opens it logs QML errors in its pairing side panel ("desc is not defined", "Cannot read property 'type' of null"); pairing a gamepad through it was not yet tested at the time.
- The Chromecast remote sends KEY_COFFEE (screen lock) from one button, probably power; Plasma binds that to Lock Session. The mediagram account has no password so the lock screen opens with OK.

In the code:
- scripts/setup-funstation.sh writes ~/.local/share/applications/controllers.desktop with Exec=plasma-bigscreen-settings -m kcm_mediacenter_bluetooth
- plasma-bigscreen-settings takes -m, not --module
