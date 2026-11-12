---
title: "Bigscreen minimizes the Android window, and WayDroid never restores it, so the tiles do"
author: Andre
date: 2026-11-12
branch: main
cost: { minutes: 2, tokens: 22131 }
id: l-4f09f280
scope: repo
status: active
src: agent
touches:
  - scripts/setup-funstation.sh
---

## Decided
- 2026-11-12: both Android tiles run one helper. It starts the app if one is named, shows the full Android window, forces one Android repaint, and then has KWin unminimize and activate the window whose resourceClass is "Waydroid".

## Watch out
- "Android TV is not opening" has three different causes, each fixed in the helper: no window until Android draws a frame (fixed by the forced repaint), the app hidden in one-app-window mode (fixed by always using the full window), and the existing window minimized by Bigscreen's home screen (fixed by the KWin unminimize). The third looked like the first: Bigscreen's splash sat on top, while the Waydroid window existed with minimized=true.
- To check from SSH, load a one-line KWin script through qdbus6 (org.kde.KWin /Scripting loadScript, then Script<N>.run, then unload) that prints each window's resourceClass and minimized flag; the output lands in the system journal (kwin_wayland).

In the code:
- scripts/setup-funstation.sh writes ~/.local/bin/funstation-android; the tiles run `funstation-android` and `funstation-android com.mediagram.android`
