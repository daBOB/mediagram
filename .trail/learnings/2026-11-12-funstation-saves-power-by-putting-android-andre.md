---
title: "Funstation saves power by putting Android's display to sleep, not just the HDMI"
author: Andre
date: 2026-11-12
branch: main
cost: { minutes: 8, tokens: 407069 }
id: l-bccf56bc
scope: repo
status: active
src: agent
touches:
  - scripts/setup-funstation.sh
---

## Decided
- 2026-11-12, the user's choice: after an hour without input, funstation turns the HDMI signal off and puts Android's display to sleep, while the cache server, web player and Android keep running. Any key wakes both in under a second. The user picked this over a full suspend, which would take the services offline and needs the Bluetooth remote to wake the machine (untested).
- The Android TV Chromecast receiver is disabled. Under WayDroid it gets no device certificate, so it crashed and restarted every 10-20 s. mediagram's Play on TV does not use Google Cast.

## Tried and ruled out
- HDMI off alone: the CPU package stayed near 20 W, because Android kept drawing mediagram's animated home screen.
- Switching CPU boost off: about 2.5 W saved while Android draws, nothing once Android sleeps. Dropped.
- Measured CPU package power: screen on 20.4 W, HDMI off 20.0 W, HDMI off with Android asleep 2.4 W.

## Watch out
- The idle timer is held by Android's keep-screen-on (mediagram while playing, Spieltisch while open), which WayDroid's bridge passes to KWin as an idle inhibit. A paused film still goes dark after the hour.
- After an hour of no signal the TV itself may go to standby and drop off HDMI; then it has to be switched on with its own power button.

In the code:
- scripts/setup-funstation.sh, step "after an hour without input...": user unit funstation-idle.service runs `swayidle timeout 3600 'kscreen-doctor --dpms off; sudo -n waydroid shell -- input keyevent KEYCODE_SLEEP' resume '... KEYCODE_WAKEUP; kscreen-doctor --dpms on'`
- `pm disable-user --user 0 com.google.android.apps.mediashell` (undo: `pm enable`)
