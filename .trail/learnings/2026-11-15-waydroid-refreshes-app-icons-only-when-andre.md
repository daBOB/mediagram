---
title: "WayDroid refreshes app icons only when Android starts, not when an app is updated"
author: Andre
date: 2026-11-15
branch: main
cost: { minutes: 3, tokens: 37959 }
id: l-47e881fb
scope: repo
status: active
src: agent
touches:
  - Cargo.lock
  - Cargo.toml
  - android/app/build.gradle.kts
  - plans/261115-1414-app-icons/plan.md
  - scripts/setup-funstation.sh
  - web/package.json
  - android/app/src/main/res/drawable/ic_launcher.xml
---

## Decided
2026-11-15: Bigscreen's mediagram tile on funstation takes its icon from the PNG WayDroid extracts from the installed app, so the tile follows the app's icon instead of a theme icon.

## Watch out
Android writes those PNGs (all at once) when its session starts. Installing an update with pm install leaves the old picture in place: after the film leader icon went in, the file still showed the old ring. To show a new icon without restarting Android (which closes whatever is on the TV), render the app's vector drawable to a 432x432 PNG and copy it over that file, then rebuild Plasma's app cache (kbuildsycoca6) so Bigscreen reads it. Android overwrites it with its own render at the next start.

In the code:
- scripts/setup-funstation.sh, the mediagram.desktop tile (Icon=)
- android/app/src/main/res/drawable/ic_launcher.xml, the icon's vector source
- funstation: ~/.local/share/waydroid/data/icons/com.mediagram.android.png
