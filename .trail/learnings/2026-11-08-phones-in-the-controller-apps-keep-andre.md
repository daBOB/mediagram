---
title: Phones in the controller apps keep their own name and avatar
author: Andre
date: 2026-11-08
standing: true
id: l-4f75ad6a
scope: repo
status: active
src: init-sessions
---

## Decided
- The apps find the TV box by address, so a new box address would otherwise send every player back to typing their name. The app stores name and avatar itself rather than relying on the page.
- In the code: In the controller apps, store the player name and avatar in app storage through the bridge (crates/mediagram-game/client). The browser path keeps using page storage and must stay unchanged.

_From past Claude sessions here, Nov 7._
