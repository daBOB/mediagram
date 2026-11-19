---
title: "Funstation's own remote is a Fire TV-style one; OK and Mute need remapping and Plasma must let go of the media keys"
author: Andre
date: 2026-11-19
branch: main
cost: { minutes: 5, tokens: 306416 }
id: l-4ebeccb3
scope: repo
status: active
replaces: l-0c7503dc
src: agent
touches:
  - Cargo.lock
  - Cargo.toml
  - android/app/build.gradle.kts
  - docs/project-changelog.md
  - scripts/setup-funstation.sh
  - web/package.json
---

## Decided
2026-11-19: the user named a Fire TV-style Bluetooth remote (power, mic, ring with OK, Back, Home, Menu, rewind, play/pause, fast-forward, volume, mute) the "official" funstation remote. It pairs on the USB stick as "AR" (Amazon company ID 0x0171 in its modalias). Its OK sends Keypad Enter and its Mute sends F7, which neither Bigscreen nor Android takes as OK or Mute, so a hwdb rule turns them into Enter and Mute. The battery service also takes the UPower device whose model is "AR".
Later the same evening the user asked for play/pause: the remote sends the right key (KEY_PLAYPAUSE), but Plasma's media controls hold Play/Pause, Pause, Stop, Next, Previous, Rewind and Fast Forward as global shortcuts, so KWin swallowed them before Android, and nothing on the host plays for Plasma to send them to. Those seven shortcuts are released, so the keys reach the window in front.

## Tried and ruled out
- Matching the hwdb rule by vendor and product, as the Chromecast remote's rule does: this remote's input device reports vendor 0000 and product 0000, so only a name match (`evdev:name:AR Keyboard:*`) reaches it.
- Editing kglobalshortcutsrc while the session runs: the shortcut service keeps its own copy and writes it back. It is changed over D-Bus instead (setForeignShortcut with no keys), which saves the file and tells Plasma.

## Watch out
- A key that shows up right on the input device can still be swallowed by a Plasma global shortcut before it reaches Android. Check kglobalshortcutsrc when a correct key does nothing in Android.
- Mic and Power were never pressed while recording, so what they send is unknown. Power is offered as KEY_POWER; PowerDevil holds the power-key inhibitor, so it should open the logout screen rather than switch off, but that was not tried.
- A recorder saw no presses for minutes while the remote showed "Connected": the user simply had not pressed yet. Check the raw hidraw node before suspecting the pairing; removing the device to re-pair loses the bond the user was already using.

In the code:
- scripts/setup-funstation.sh writes /etc/udev/hwdb.d/70-funstation-ar-remote.hwdb (KEYBOARD_KEY_70058=enter, KEYBOARD_KEY_70040=mute), the battery match `model:\s+(.*remote.*|AR)$`, and releases the [mediacontrol] shortcuts with `busctl --user call org.kde.kglobalaccel /kglobalaccel org.kde.KGlobalAccel setForeignShortcut asai 4 mediacontrol <action> "Media Controller" <friendly name> 0`.
