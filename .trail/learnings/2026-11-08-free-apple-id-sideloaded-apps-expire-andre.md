---
title: "Free Apple ID sideloaded apps expire weekly, so refresh before game night"
author: Andre
date: 2026-11-08
standing: true
id: l-3db0a14f
scope: repo
status: active
src: init-sessions
touches:
  - scripts/refresh-ios-controllers.sh
---

## Watch out
- The household uses a free Apple ID, so installed iPhone and iPad apps stop opening after 7 days. Only 3 sideloaded apps are allowed per device.
- In the code: Run scripts/refresh-ios-controllers.sh weekly. xtool install re-signs and restarts the 7 days. xtool builds iOS on Linux using an Xcode SDK. Sideloadly is only a backup and runs on Windows or Mac.

_From past Claude sessions here, Nov 7._
