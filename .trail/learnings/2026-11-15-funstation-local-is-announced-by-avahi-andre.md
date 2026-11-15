---
title: funstation.local is announced by avahi alone and only with its IPv4 address
author: Andre
date: 2026-11-15
branch: main
cost: { minutes: 40, tokens: 417920 }
id: l-7e15f629
scope: repo
status: active
src: agent
touches:
  - Cargo.lock
  - android/spieltisch/build.gradle.kts
  - android/spieltisch/src/main/assets/game-art/backdrop-feldzug.webp
  - android/spieltisch/src/main/assets/game-art/backdrop-gassenwechsel.webp
  - android/spieltisch/src/main/assets/game-art/backdrop-grundstueck.webp
  - android/spieltisch/src/main/assets/game-art/backdrop-herz.webp
  - android/spieltisch/src/main/assets/game-art/backdrop-nachtwache.webp
  - android/spieltisch/src/main/assets/game-art/backdrop-neuland.webp
  - android/spieltisch/src/main/assets/game-art/backdrop-neunmalklug.webp
  - android/spieltisch/src/main/assets/game-art/backdrop-pleitier.webp
---

## Decided
2026-11-15: funstation.local stopped working in browsers on the dev box. Two faults stacked. systemd-resolved on funstation answered mDNS for the host name as well as avahi, so at boot avahi saw a conflict and renamed itself funstation-2.local, then funstation-3.local, and resolved alone answered for funstation.local. Separately, a browser-style lookup (getaddrinfo with no family) on a systemd-resolved client returned only funstation's IPv6 addresses. Nothing on funstation listens on IPv6 (the player binds 0.0.0.0, the Spieltisch forward is an IPv4 rule), so every address was refused and the browser had no IPv4 to fall back on. Fix: resolved on funstation only resolves mDNS and no longer answers, and avahi announces only the IPv4 address.

## Tried and ruled out
Fixing only the name clash. avahi got funstation.local back, but the dev box still got IPv6 only. systemd-resolved sends A queries over the IPv4 mDNS transport and AAAA over IPv6, and keeps the family that answers first.
Listening on IPv6 in the player instead. On a dual-stack socket IPv4 viewers can show up as ::ffff:a.b.c.d, and the player decides direct play, transcoding and panel access by the viewer's address. That change needs a careful review, while announcing only IPv4 matches what funstation actually serves.

## Watch out
This corrects the port-80 learning, which says browsers fall back to IPv4 at once. They don't when the client's resolver hands back only IPv6.
avahi still logs "Detected another IPv4/IPv6 mDNS stack" because of mediagram_cache (mdns-sd, host name mediagram-cache-7788.local) and resolved's resolver socket. The warning is harmless as long as none of them claims funstation.local.
If the player ever listens on IPv6, set use-ipv6 back to yes.

In the code: scripts/setup-funstation.sh, step "announced on the network as …": /etc/systemd/resolved.conf.d/mdns-resolve-only.conf (MulticastDNS=resolve); /etc/avahi/avahi-daemon.conf use-ipv6=no, publish-aaaa-on-ipv4=no (original kept as avahi-daemon.conf.orig on funstation). Check with `getent ahosts funstation.local` on the dev box, which should give 192.168.0.42.
