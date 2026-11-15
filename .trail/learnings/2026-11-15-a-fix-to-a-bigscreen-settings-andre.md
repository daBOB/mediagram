---
title: A fix to a Bigscreen settings page only shows once the open Settings window is closed
author: Andre
date: 2026-11-15
branch: main
cost: { minutes: 1, tokens: 1493 }
id: l-198fef51
scope: repo
status: active
src: agent
touches:
  - scripts/setup-funstation.sh
---

## Decided
After installing a rebuilt plasma-bigscreen on funstation (2026-11-15), the user saw no change: the Settings window had been opened before the install, and Bigscreen brings that same running process back each time Settings is opened, so it kept the old compiled QML. Closing that process made the next open load the fix.

## Watch out
After installing anything that changes a settings page, check the settings process start time against the install time in /var/log/pacman.log and close it if it is older. The home shell itself keeps old Bigscreen code until the session restarts.
Run long package updates on funstation as a systemd unit, not inside an SSH session: a Restart from Bigscreen's power menu or a dropped connection ends the session, and pacman then leaves /var/lib/pacman/db.lck behind (remove it only after checking no pacman is running).

In the code:
- scripts/setup-funstation.sh, step "Plasma Bigscreen with funstation's fixes"
- scripts/funstation/plasma-bigscreen/PKGBUILD
- ps -o pid,lstart -C plasma-bigscreen-settings
- systemd-run --user --unit=<name> --collect <script>
