---
title: "Funstation's on-screen keyboard needs the plasma-keyboard package, and KWin only picks it up from a real config change"
author: Andre
date: 2026-11-15
branch: main
cost: { minutes: 2, tokens: 138128 }
id: l-75d44f18
scope: repo
status: active
src: agent
touches:
  - scripts/setup-funstation.sh
---

## Decided
2026-11-15, the user asked to activate the on-screen keyboard on funstation. Plasma Bigscreen already configures plasma-keyboard as KWin's input method (and sets KWIN_IM_SHOW_ALWAYS=1 so it shows even with a hardware keyboard), but the package was never installed, so KWin reported the virtual keyboard as unavailable. The setup script now installs plasma-keyboard.

## Tried and ruled out
Writing the same InputMethod value into ~/.config/kwinrc and calling KWin's reconfigure did nothing. Writing it again with `kwriteconfig6 --notify` also did nothing: KConfig sends no change notification when the value does not change, and KWin only reloads its input method when it gets one.

## Watch out
To load it into a running session without logging out, the value KWin sees has to actually change. Deleting the duplicate keys from ~/.config/kwinrc with --notify worked, because KWin then falls back to Bigscreen's identical value. Check with qdbus6 org.kde.KWin /VirtualKeyboard: `available` must be true and a plasma-keyboard process must be running.

In the code:
- scripts/setup-funstation.sh, the pacman line (plasma-keyboard)
- Bigscreen's defaults: ~/.config/plasma-bigscreen/kwinrc [Wayland] InputMethod=/usr/share/applications/org.kde.plasma.keyboard.desktop
