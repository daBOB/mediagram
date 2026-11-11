---
title: "The home cache server listens on 192.168.0.240:7788"
author: Andre
date: 2026-11-08
id: l-755d6cf2
scope: repo
status: active
touches:
  - crates/mediagram-cache/src/config.rs
---

## Decided
The LAN cache server (`mediagram_cache`, cache-server-runs-on-another-machine) listens on `0.0.0.0:7788` (`crates/mediagram-cache/src/config.rs` DEFAULT_LISTEN) at 192.168.0.240 and answers `GET /v1/status`. Port 80 on that host is a different server (openresty) that answers 404.

**Why:** 2026-10-03 the TV box showed "Home cache server — Status: Not found" while the server ran. The stored manual address was `http://192.168.0.240`; `normalizeManualAddress` (android/feature/system LanCacheInput.kt) only adds `http://`, never the default port, and `pickLanServer` probes the address as stored → port 80 → 404 → refused. mDNS fallback also failed on the box: avahi on the dev machine sees `_mediagram-cache._tcp` at 192.168.0.240:7788, but the box's NsdService got no answer within the app's 10 s window (an MdnsEvent arrived just as the listener was unregistered).

## Watch out
enter `http://192.168.0.240:7788` (done on the box; status then "Connected to 192.168.0.240:7788"). If "Not found" returns, check `curl http://192.168.0.240:7788/v1/status` and the stored address's port before suspecting the server. Since 0.99.8 (`c73f06ee`) `pickLanServer` adds port 7788 to a port-less manual address at probe time (`withDefaultPort`, LanServerPicker.kt), so stored addresses heal. mDNS on the box (2026-10-04): the server answers QU/QM/legacy PTR queries in < 50 ms from a wired host (probe: scratch `mdns_probe.py`), but the box — on **Wi-Fi** (eth0 idle) — never gets ServiceFou
…

_From Claude Code's memory of this repo, last updated Oct 3._
