---
title: "Funstation's own remote is a Fire TV-style one whose OK and Mute need remapping"
author: Andre
date: 2026-11-19
branch: main
cost: { minutes: 75, tokens: 578384 }
id: l-0c7503dc
scope: repo
status: active
src: agent
touches:
  - Cargo.lock
  - Cargo.toml
  - android/app/build.gradle.kts
  - docs/project-changelog.md
  - scripts/setup-funstation.sh
  - web/package.json
  - web/public/styles/catalog.css
  - android/feature/catalog/src/main/kotlin/HomeShelves.kt
  - android/feature/catalog/src/test/kotlin/LibraryPagesFixtureTest.kt
  - android/ui-mobile/src/main/kotlin/ui/LibraryBrowseBranches.kt
---

## Decided
2026-11-19: the user named a Fire TV-style Bluetooth remote (power, mic, ring with OK, Back, Home, Menu, rewind, play/pause, fast-forward, volume, mute) the "official" funstation remote. It pairs on the USB stick as "AR" (Amazon company ID 0x0171 in its modalias). Its OK sends Keypad Enter and its Mute sends F7, which neither Bigscreen nor Android takes as OK or Mute, so a hwdb rule turns them into Enter and Mute. The battery service now also takes the UPower device whose model is "AR".

## Tried and ruled out
Matching the hwdb rule by vendor and product, as the Chromecast remote's rule does: this remote's input device reports vendor 0000 and product 0000, so only a name match (`evdev:name:AR Keyboard:*`) reaches it.

## Watch out
- Ring, Back, Home, Menu, rewind, play/pause, fast-forward and volume already arrive as the right keys. Mic and Power were never pressed while recording, so what they send is unknown. Power is offered as KEY_POWER; Plasma's PowerDevil holds the power-key inhibitor, so it should open the logout screen rather than switch off, but that was not tried.
- A recorder saw no presses for minutes while the remote showed "Connected": the user simply had not pressed yet. Check the raw hidraw node before suspecting the pairing; removing the device to re-pair loses the bond the user was already using.

In the code:
- scripts/setup-funstation.sh writes /etc/udev/hwdb.d/70-funstation-ar-remote.hwdb (KEYBOARD_KEY_70058=enter, KEYBOARD_KEY_70040=mute) and the battery match `model:\s+(.*remote.*|AR)$`.
