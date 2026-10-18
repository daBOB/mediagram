# Probe: Miracast display, AirPlay from Safari

2026-10-19. Phase 01 of `plans/261018-1921-cast-to-tv`.

## Miracast (tablet `caad49da` → Hisense AnyView Cast)

**Result: positive.** While connected, Android reports the Hisense as a presentation display that an app can draw on.

| | |
|---|---|
| Logical displays | 2: id 0 (built-in), **id 2 "Living Room TV"** |
| Type | `WIFI`, uniqueId `wifi:3a:1b:9e:d2:fd:d4` |
| Flags | `FLAG_PRESENTATION`, `FLAG_TRUSTED` (no `FLAG_SECURE`) |
| Mode | 1920 × 1080 @ 60 Hz, density 320; no HDR types |
| Wifi display status | `mActiveDisplayState=2` (connected), `isRemembered false` |

How it was connected (needed again for the phase 09 device check):

1. The Hisense's input set to **AnyView Cast** (its waiting screen up).
2. The tablet's Settings → Connection & sharing → **Cast**. The Android "Wireless display" switch is not needed: Xiaomi's own Cast dialog lists the TV.
3. Choose **Screen mirroring** (not "App extension"), pick **Living Room TV** (not `VIERA_txhx800w_8daa`, a neighbour's Panasonic), and allow it on the TV.
4. The first attempt failed ("Tap to try again"); the second connected.

What it means for phase 09:
- Build it as planned: `DisplayManager.getDisplays(DISPLAY_CATEGORY_PRESENTATION)` finds display 2, and an `android.app.Presentation` on it shows full screen on the TV.
- The display is **not secure** (no `FLAG_SECURE`). If the player's window or video surface is marked secure, the TV would show black. Check this in the phase 09 device check.
- Limits as the spec says: 1080p60, no HDR.

## AirPlay (the user's Apple device, Safari)

Not run yet.
