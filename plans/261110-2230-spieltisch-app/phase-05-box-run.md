# Phase 5: Box run

## Context links

- Plan: [plan.md](plan.md)
- Earlier box runs: `plans/261103-2254-game-night/reports/box-walk-report.md`
- The box is at the address in those reports. Navigation only; never change the box's settings.

## Overview

- Priority: last.
- Status: pending. Blocked until the games session (the owner of `mediagram-games`) says the test night is over and the box is free.
- What it does: proves the two apps on the real TV box, then publishes both.

## Steps

1. Coordinate with the games session; agree a time on the box.
2. Install Spieltisch by `adb` (first install, like the media app at device setup). Install the media app build from phase 4. Box setup needs "install unknown apps" allowed for Spieltisch (`REQUEST_INSTALL_PACKAGES` was `granted=false` on the emulator); that is the user's step at setup, not ours.
3. From the media app's Games row: Spieltisch opens, and Back on its shelf returns to the media app.
4. A Poster-Raten round and a Kopfnuss game with two phones. Library posters show. Measure frame times against the last Kopfnuss box run (2.5 % slow frames, 99th percentile 36 ms).
5. Kids profile in the media app means locked titles in Spieltisch.
6. Time a night's start with the media app's process killed first.
7. Publish a Spieltisch patch release. Measure frame times during its download, with a night running (a download runs during a night; only the install waits for the night's stop). The box installs it in the background without touching the media app, and the media app's pinned release stays.
8. Screenshots and numbers go into `reports/box-run-report.md`.

## Todo

- [ ] box free (games session)
- [ ] installs; handoff and Back
- [ ] posters, Kopfnuss, frame times
- [ ] kids lock across apps
- [ ] cold-start timing; the first night after a library change must build in under 30 s, or every ask on that box answers locked (the media app logs "took longer than")
- [ ] a Poster-Raten round with library posters (no film posters on the emulator, so the image path is still unseen)
- [ ] "install unknown apps" allowed for Spieltisch at setup (the user's step)
- [ ] Spieltisch-only update on the box, with frame times measured during its download
- [ ] report with screenshots

## Funstation run (2026-11-11, before the box)

The user asked for a run on funstation (WayDroid Android TV 16, x86_64); details in [reports/tester-261111-0901-spieltisch-on-funstation-report.md](reports/tester-261111-0901-spieltisch-on-funstation-report.md).

- Works there: the Games row opens Spieltisch; the night from a cold media app (about 1 s); the update check through the media app; Poster-Raten with library posters (the image path, unseen on the emulator); library titles on the phone; Back from the game to the lobby, the shelf, then the media app; the night ends on leaving. The ARM-only release runs under translation.
- Not covered there: the kids profile switch (switching back may need a PIN); a download during a night (needs a publish).
- Phones on the LAN couldn't reach a night there at first (WayDroid's NAT address, not from this branch). The funstation session bridged WayDroid onto the LAN; after that, Kopfnuss with two phones ran with 0.37 % janky frames, p99 36 ms.
- The box steps above all still stand: the box is a different device (Mali, armv7), and frame times there are the ones that count.

## Success criteria

Both apps on the box, a full night in Spieltisch, and one Spieltisch update installed on its own.

## Risk assessment

- Memory on the box with two processes: the media app is in the background while a night runs. Watch for the media app being killed, and whether that matters (the provider restarts it).
