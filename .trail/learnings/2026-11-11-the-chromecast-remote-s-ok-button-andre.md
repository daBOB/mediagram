---
title: "The Chromecast remote's OK button is remapped to Enter, because Bigscreen ignores Select"
author: Andre
date: 2026-11-11
branch: main
cost: { minutes: 5, tokens: 43944 }
id: l-4423d26d
scope: repo
status: active
src: agent
touches:
  - scripts/setup-funstation.sh
---

## Decided
- 2026-11-12: the remote's OK (HID consumer usage c0041) is remapped to KEY_ENTER through a udev hwdb entry for this remote model (Google 18d1:9450). Bigscreen reacts to Enter/Return in 23 places in its QML and to Select only on its lock screen. Android's Generic.kl in the WayDroid-ATV image maps key 28 (Enter) to DPAD_CENTER, the same as 353 (Select), so OK keeps working in Android.

## Watch out
- After the hwdb changes, the remote must reconnect. libevdev drops key codes a device didn't advertise when it was opened, so the new Enter is lost until the remote re-registers (`bluetoothctl disconnect`, then any button press).
- Over SSH, `getevent` can't read Android's input: WayDroid feeds keys through FIFOs (/dev/input/wl_keyboard_events). Use `dumpsys input` RecentQueue instead. To get a device's scancode table without pressing any buttons, call EVIOCGKEYCODE_V2 by index.

In the code:
- scripts/setup-funstation.sh, "Bluetooth on the USB stick only" step: /etc/udev/hwdb.d/70-funstation-chromecast-remote.hwdb
