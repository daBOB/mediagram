---
title: A finished game only reaches the TVs once its branch is merged and Spieltisch is released
author: Andre
date: 2026-11-12
branch: main
cost: { minutes: 11, tokens: 174694 }
id: l-82f0fb50
scope: repo
status: active
src: agent
touches:
  - Cargo.lock
  - Cargo.toml
  - android/app/build.gradle.kts
  - android/app/src/main/AndroidManifest.xml
  - scripts/funstation/fun-station.png
  - scripts/setup-funstation.sh
  - web/package.json
  - scripts/release-spieltisch.sh
  - crates/mediagram-game/Cargo.toml
  - crates/spieltisch-core/Cargo.toml
---

## Decided
- The user saw the old Flüchtig board on the TV a day after the city map and ferry were finished: the work sat on its own branch, never merged, and Spieltisch on the TVs is only ever what the Spieltisch release script publishes from main. Merged on 2026-11-12 and released as Spieltisch 0.176.0.
- The branch had bumped the media line, written before the games had their own app. As its plan agreed, the merge kept main's media versions and moved the bump to the Spieltisch line at the next free number.

## Watch out
- The Preisfrage branch (feat/preisfrage) also uses media 0.177.0 for its cache change, but main released media 0.177.0 to the channel on 2026-11-12 for funstation and the large heap. Whoever merges Preisfrage must move its media bump to the next free number (0.178.0 or later); its Spieltisch 0.177.0 is still free.
- When the user reports an old game on the TV, first look for finished game branches not merged into main (git log main..<branch>) before suspecting the updater.

In the code: scripts/release-spieltisch.sh publishes under #spieltisch-app; Spieltisch versions live in crates/mediagram-game/Cargo.toml, crates/spieltisch-core/Cargo.toml, crates/mediagram-game/client/package.json, android/spieltisch/build.gradle.kts (and Cargo.lock).
