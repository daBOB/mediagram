---
title: "The iOS app joins whichever Spieltisch it hears first, so dev tests on an iPad without a camera scanner can land on the wrong box"
author: Andre
date: 2026-11-13
branch: feat/kritzelei
cost: { minutes: 30, tokens: 466084 }
id: l-a738ff66
scope: repo
status: active
src: agent
touches:
  - crates/mediagram-game/src/kritzelei_clock.rs
  - crates/mediagram-game/src/kritzelei_clock_tests.rs
  - crates/mediagram-game/src/kritzelei_config.rs
  - crates/mediagram-game/src/kritzelei_rules.rs
  - crates/mediagram-game/src/kritzelei_rules_tests.rs
  - crates/mediagram-game/src/lib.rs
  - crates/mediagram-game/src/kritzelei_words.rs
  - crates/mediagram-game/src/kritzelei_words_tests.rs
  - crates/mediagram-game/src/room.rs
  - crates/mediagram-game/src/room_board.rs
---

## Decided
For a device test on the iPad 5 (no QR scanner, so it finds boxes only by Bonjour), announce the dev server under its own name and make sure no other box answers, or point a throwaway build at that name.

## Watch out
- Several boxes announce `_mediagram-game._tcp` on the home Wi-Fi at once: the TV box's Spieltisch, any other dev session's server on this PC (seen on :8090), and your own. The app takes the first result, which is random.
- `game-night-dev` doesn't announce itself; `avahi-publish` does it for the test.
- Another session can stop a dev server you started (it died with exit 144 mid-test); check it is still up before asking someone to draw.

In the code: ios/controller/Sources/Spieltisch/BoxFinder.swift takes `urls.first`. Announce with `avahi-publish -s "Spieltisch dev" _mediagram-game._tcp 8080 "url=http://<pc-ip>:8080/"`; list announcers with `avahi-browse -rpt _mediagram-game._tcp`.
