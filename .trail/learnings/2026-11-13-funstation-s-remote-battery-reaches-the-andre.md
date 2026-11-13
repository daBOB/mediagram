---
title: "Funstation's remote battery reaches the TV app as a root broadcast from the host"
author: Andre
date: 2026-11-13
branch: main
cost: { minutes: 40, tokens: 351267 }
id: l-382bbac8
scope: repo
status: active
src: agent
touches:
  - Cargo.lock
  - android/spieltisch/build.gradle.kts
  - android/spieltisch/src/main/assets/game-art/backdrop-dreifelder.webp
  - android/spieltisch/src/main/assets/game-art/backdrop-durak.webp
  - android/spieltisch/src/main/assets/game-art/backdrop-fluechtig.webp
  - android/spieltisch/src/main/assets/game-art/backdrop-kopfnuss.webp
  - android/spieltisch/src/main/assets/game-art/backdrop-mau-mau.webp
  - android/spieltisch/src/main/assets/game-art/backdrop-poster.webp
  - android/spieltisch/src/main/assets/game-art/backdrop-preisfrage.webp
  - android/spieltisch/src/main/assets/game-art/backdrop-quiz.webp
---

## Decided
2026-11-13, the user asked for the Bluetooth remote's battery with percentage "at the bottom in the mediagram sidebar", on funstation only. Android runs inside WayDroid and cannot see the remote, which is paired to the Linux host. The host reads the battery from UPower (the Chromecast Remote shows up as a keyboard device with model "Chromecast Remote") and pushes it into Android with `sudo -n waydroid shell -- am broadcast -a com.mediagram.android.REMOTE_BATTERY -p com.mediagram.android --ei percent N`. The app takes it only from a receiver registered while the rail is on screen and guarded by android.permission.DUMP, so root and the shell can send it and no installed app can. It sends on a change and every ten minutes, and the app keeps the last number across launches. Devices nobody tells (the TV box, phones) show nothing, so no device check is needed.

## Tried and ruled out
Pulling over HTTP from the app: the app would have to find the host's address, and it would need a new port and firewall rule. A manifest receiver: every broadcast would cold-start the whole app when it is not running.

## Watch out
`am broadcast` exits 0 even when no receiver is registered, so the host counts a send to a closed app as delivered. The ten-minute repeat covers that. A remote whose UPower model lacks "remote" is never found.

In the code: android/ui-tv/src/main/kotlin/ui/tv/chrome/TvRemoteBattery.kt, TvLibraryRail.kt (RailRows plus the badge under them), scripts/setup-funstation.sh step "the remote's battery at the foot of the mediagram rail", ~/.local/bin/funstation-remote-battery on funstation.
