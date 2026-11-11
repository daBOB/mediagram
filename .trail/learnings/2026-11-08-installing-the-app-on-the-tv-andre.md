---
title: Installing the app on the TV box uncompiled doubled late frames
author: Andre
date: 2026-11-08
standing: true
id: l-c0069c6c
scope: repo
status: active
src: init-sessions
---

## Watch out
- An install ran without the right compile profile, which made frame stats look worse. The poster session had installed the older-Android baseline profile by mistake, and a reinstall fixed it.
- In the code: After sideloading to the box, check the compile status before trusting frame stats. Use the release script's install-dm with the speed-profile. Do not use the baselineProfiles/1 profile meant for older Android.

_From past Claude sessions here, Nov 7._
