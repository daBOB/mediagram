---
title: "Funstation's Android window needs a forced repaint to open, and a whole-number screen scale to fill the TV"
author: Andre
date: 2026-11-11
branch: main
cost: { minutes: 8, tokens: 222290 }
id: l-a0ae40c2
scope: repo
status: active
src: agent
touches:
  - scripts/setup-funstation.sh
---

## Decided
- 2026-11-12 the user chose to run the TV at scale 2 instead of KWin's 1.7. That way Android TV always fills the screen, with no restarts. Bigscreen, Steam and the browser grew by about 18%, to the 1080p layout Bigscreen is designed for.
- Both Android tiles (Android TV, mediagram) force one Android repaint right after asking WayDroid for its window.

## Tried and ruled out
- Keeping 1.7 and restarting Android whenever the TV reconnects. Every TV power-on would cost a 12 to 20 s boot and close open Android apps. The user declined it.

## Watch out
- "Starting Android TV is slow" was really a stuck start. WayDroid creates its window only when Android composes its next frame. `waydroid show-full-ui` tries to force one by pulling down the notification shade, which does nothing on Android TV (logcat: `TvNotificationPanel: Not launching notification handler activity`). Bigscreen's splash then sat there for minutes, until a clock tick or a key press redrew the screen. After a forced repaint the window appeared 225 ms later.
- "Android not full screen" was a WayDroid hwcomposer bug. It calibrates the fractional scale (1.7) once, at session start. A later `wl_output.scale` event, sent when the TV reconnects over HDMI, runs `scale = max((int)scale, 2)` = 2, and from then on every frame is placed at 3840/2 = 1920 logical in a 2259-wide window, covering 85% of the screen. `waydroid.display_scale` still reads 1.7, so that property does not show the problem.

In the code:
- scripts/setup-funstation.sh: tile Exec lines run `sudo -n waydroid shell -- service call SurfaceFlinger 1004` after `waydroid show-full-ui` / `waydroid app launch` (relies on the script's NOPASSWD sudoers)
- scripts/setup-funstation.sh: ~/.config/autostart/funstation-tv-scale.desktop runs `kscreen-doctor output.HDMI-A-1.scale.2` at login (kscreen-doctor needs the session's Wayland/D-Bus env, so it can't run over SSH from the script)
- hwcomposer source: github.com/waydroid/android_hardware_waydroid hwcomposer/wayland-hwc.cpp, output_handle_scale and the calibrating block in create_window
