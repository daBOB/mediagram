---
title: Funstation uses only its USB Bluetooth stick; the built-in radio is switched off
author: Andre
date: 2026-11-11
branch: main
cost: { minutes: 18, tokens: 79401 }
id: l-a835c4e3
scope: repo
status: active
src: agent
touches:
  - Cargo.lock
  - Cargo.toml
  - android/app/build.gradle.kts
  - scripts/setup-funstation.sh
  - web/package.json
---

## Decided
2026-11-11 the user chose to switch off funstation's built-in Realtek Bluetooth and keep only the USB Bluetooth stick for the remote and game controllers.

## Tried and ruled out
Running both radios: Bluetooth pairings are stored per radio, so the Chromecast remote paired on the stick, and when the stick dropped off USB the remote was simply gone, and the other radio could not pick it up. Moving everything to the built-in radio was offered instead; the user preferred the stick.

## Watch out
- The stick (an "Actions general adapter") dropped off USB twice within three seconds once; if the remote stops responding, check the stick first. Its pairings survive a replug.
- Several nearby "TY" devices are Tuya smart-home gadgets (they advertise only service 0xA201), not controllers; the Bigscreen Bluetooth page lists them and will try to pair them.
- Clone Joy-Cons never showed up in any scan on 2026-11-11; not yet tried with the stick alone.
- Linux hid-nintendo handles original Joy-Cons; each one is its own controller unless Steam merges them.

In the code:
- scripts/setup-funstation.sh step "Bluetooth on the USB stick only" writes /etc/udev/rules.d/80-funstation-builtin-bluetooth-off.rules (USB 0bda:c821, authorized=0)
- pairings live in /var/lib/bluetooth/<radio address>/<device address>/
- stick = F4:4E:FC:F7:15:56 (USB 10d7:b012), built-in = 0C:CD:D0:02:B0:AC
