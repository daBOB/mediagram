---
title: "The funstation web player answers on port 80 through a kernel redirect, not a proxy"
author: Andre
date: 2026-11-12
branch: main
cost: { minutes: 5, tokens: 166304 }
id: l-b495a17d
scope: repo
status: active
src: agent
touches:
  - scripts/setup-funstation.sh
---

## Decided
2026-11-12: the user asked for funstation to be reachable as funstation.local. The web player was running the whole time, but only on port 8770, so a browser given the bare name asked port 80, got nothing (ufw drops it), and the player looked dead. Port 80 now goes to the player through an nftables redirect.

## Tried and ruled out
A TCP or HTTP proxy on port 80 (systemd-socket-proxyd, nginx). Behind it every viewer arrives from loopback, and the player picks direct play or a transcode, and who may see its panel, by the viewer's address. Trusting X-Forwarded-For would only fix that for an HTTP proxy, and adds a setting. The redirect keeps the real source address.

## Watch out
Never enable nftables.service on funstation: its /etc/nftables.conf starts with `flush ruleset`, which also wipes ufw's tables. The redirect lives in its own table, loaded by its own oneshot unit.
The player binds IPv4 only (0.0.0.0), so funstation.local's IPv6 addresses are refused at once and browsers fall back to IPv4 without delay.
funstation.box is not an mDNS name: only .local is. It needs a record in the home DNS (Technitium on 192.168.0.240, also the DHCP server, domain "home"). .box is a real public TLD, so without that record the name goes to the internet and fails.

In the code: scripts/setup-funstation.sh, step "web player on port 8770, and on 80…": /etc/funstation-http.nft (table inet funstation_http, `fib daddr type local tcp dport 80 redirect to :8770`) and funstation-http.service. Android's own bridged address (.44) is not redirected.
