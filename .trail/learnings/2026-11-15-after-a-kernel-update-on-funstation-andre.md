---
title: "After a kernel update on funstation, new network rules fail until it restarts"
author: Andre
date: 2026-11-15
branch: main
cost: { minutes: 37, tokens: 174971 }
id: l-42df8152
scope: repo
status: active
src: agent
touches:
  - plans/261115-1456-funstation-android-wifi-fallback/phase-02-spieltisch-advertised-address.md
  - plans/261115-1456-funstation-android-wifi-fallback/plan.md
  - plans/261115-1448-shelf-art-renewal/plan.md
  - Cargo.lock
  - Cargo.toml
  - android/app/build.gradle.kts
  - android/spieltisch/build.gradle.kts
  - android/spieltisch/src/main/assets/game-art/backdrop-dreifelder.webp
  - android/spieltisch/src/main/assets/game-art/backdrop-durak.webp
  - android/spieltisch/src/main/assets/game-art/backdrop-fluechtig.webp
---

## Decided
2026-11-15: the Wi-Fi fallback could not take effect after the day's system update (kernel 7.3.3 to 7.3.4) until funstation restarted. CachyOS replaces the running kernel's module directory with the new one's, so any module not already loaded can't load any more. Both the port forward (nft_nat) and WayDroid's own network (its legacy iptables NAT modules) needed modules that weren't loaded yet. The user agreed to the restart, and the boot unit then came up in Wi-Fi mode.

## Watch out
An nft error "No such file or directory" on a nat table, or WayDroid's own network not reaching the internet, right after an update means the running kernel no longer matches /usr/lib/modules: compare `uname -r` with the directory names and restart before debugging further.
Don't amend a commit on main while another session works in the same checkout: one had already merged the commit into its branch, and the rewrite made its merge into main conflict.

In the code:
- scripts/funstation/funstation-android-net (nft table funstation_spieltisch, the move to waydroid0)
- scripts/setup-funstation.sh, step "without the cable, Android goes online over Wi-Fi"
