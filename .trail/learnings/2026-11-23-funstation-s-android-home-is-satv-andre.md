---
title: "Funstation's Android home is saTV, and mediagram reaches its Continue Watching row only through the media session"
author: Andre
date: 2026-11-23
branch: main
cost: { minutes: 10, tokens: 283724 }
id: l-9366058f
scope: repo
status: active
src: agent
touches:
  - scripts/setup-funstation.sh
  - docs/project-changelog.md
  - android/feature/player/src/main/kotlin/PlaybackService.kt
---

## Decided
2026-11-23, the user asked for the saTV launcher (github.com/kcmaker10/satv) on funstation's Android and for a connection between it and Continue. saTV 1.0.0 is installed from its GitHub release and set as Android's home, so the existing Android TV tile opens it. The LineageOS launcher stays installed.

saTV fills its Continue Watching row from two places only: a tracker account (Trakt, Simkl, MDBList, WeTrakr) and "Watched on this TV", which it records from the media sessions other apps publish. mediagram already publishes one (PlaybackService), so the connection is a permission, not code: saTV needs notification access to read sessions.

## Tried and ruled out
- Android TV's Watch Next channel: saTV does not read it, and a sideloaded launcher could not anyway, the permission is for system apps.
- Having the card resume the title: saTV opens a history card with the app's plain launcher intent, and Android brings an existing task forward without telling the app. Nothing on mediagram's side can make that a resume.
- Putting the shared Continue list on the row: saTV records only what played on this TV, for at least two minutes, while it was listening.
Both of the last two need a patched saTV (it is GPL-3 with source in each release) plus a provider and a deep link in mediagram. Not built; the user has to choose a fork first.

## Watch out
- saTV's TMDB lookup sends no language and accepts only an exact match on the English or original title, so the library's German titles ("Der Nebel", "Sieben") get no TMDB artwork there and keep the poster mediagram reports.
- The release's SHA256SUMS.txt has CRLF line ends; a `\.apk$` match finds nothing.
- `cmd notification allow_listener` returned without output and the setting read back unchanged for a moment; it was there on the next read.
- WayDroid's Android ran in GMT although the host is Europe/Berlin. `cmd alarm set-timezone` fixes it and it persists.
- Someone picked up the remote within a minute of saTV appearing on the TV, after 13 idle minutes. An idle remote is not an empty room.

In the code: scripts/setup-funstation.sh (the saTV block after Symfonium), android/feature/player/src/main/kotlin/PlaybackService.kt, PlayerMediaMetadata.kt. saTV side: data/WatchRecorder.kt, data/WatchHistory.kt, LauncherState.open.
