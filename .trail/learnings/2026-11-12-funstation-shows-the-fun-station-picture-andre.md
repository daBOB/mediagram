---
title: "Funstation shows the Fun-Station picture at boot, in Plasma's splash and as Android's boot animation, but not in Bigscreen's app loading screen"
author: Andre
date: 2026-11-12
branch: main
cost: { minutes: 4, tokens: 45613 }
id: l-c0994c61
scope: repo
status: active
src: agent
touches:
  - android/app/src/main/AndroidManifest.xml
  - scripts/setup-funstation.sh
---

## Decided
- 2026-11-12, the user's picture (scripts/funstation/fun-station.png) shows full screen from power-on to Bigscreen. Plymouth uses a script theme "funstation" that scales the picture to the screen, KSplash a look-and-feel package that holds only a splash, and Android a one-frame boot animation in WayDroid's overlay.

## Tried and ruled out
- Putting the picture in Bigscreen's loading screen when a tile opens. That window (StartupFeedbackWindow) is compiled into Bigscreen's launcher plugin and shows only the tile's icon, so the only lever there is the tile's Icon.

## Watch out
- `plymouth-set-default-theme -R` rebuilds the initramfs through CachyOS's limine hook (it also rewrites /boot/limine.conf). Plymouth can't be tested while KWin holds the display; it shows at the next boot.
- WayDroid's overlay (/var/lib/waydroid/overlay/system/product/media/bootanimation.zip, since /product links to /system/product) is read when Android starts, so a new file shows only after Android restarts. bootanimation draws frames unscaled and centred, so the frame is 3840x2160 to match Android's display, and the zip must be stored, not deflated. Funstation has no `zip`; the script writes the zip with Python.

In the code:
- scripts/setup-funstation.sh, step "the Fun-Station picture from power-on to Bigscreen..."
