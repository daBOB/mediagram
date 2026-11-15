# Funstation: Android falls back to Wi-Fi when the cable is out

Status: host side live on funstation since 15:07 (boot in Wi-Fi mode verified); Spieltisch 0.192.3 being released. Media 0.180.6, Spieltisch 0.192.3.

## Why
User, 2026-11-15: "the spieltisch app provides a link to localhost instead of ip address". Funstation's
Ethernet port has had no carrier since that morning. Android's only network is the bridge `br0` over the
wired port, so without the cable Android has no network at all: Spieltisch falls back to `127.0.0.1`, and
the media app reaches neither Telegram nor the cache server. Asked wired / Wi-Fi / both, the user chose
**both: keep the wired bridge, fall back automatically when unplugged** (knowing it restarts Android on
each cable change).

## Facts this rests on (checked 2026-11-15)
- WayDroid's own NAT network (`waydroid0`, `waydroid-net.sh`): host `192.168.240.1`, Android always
  `192.168.240.112` (`waydroid status`). Selected by `lxc.net.0.link` in
  `/var/lib/waydroid/lxc/waydroid/config`; changing it needs a container restart.
- SELinux is Disabled inside WayDroid, so an app can read any system property.
- Spieltisch's join address: `mediagram-game` `lan_ip()` (route towards 1.1.1.1), else `127.0.0.1`
  (`crates/mediagram-game/src/host.rs:120`). Behind NAT it would show `192.168.240.112`.
- Controllers find the TV over DNS-SD `_mediagram-game._tcp`, instance "Spieltisch", TXT `url=<joinUrl>`
  (`GameNightAnnouncer.kt`, `BoxFinder.kt`); multicast doesn't leave WayDroid's NAT.
- The media app's LAN cache address is `http://192.168.0.43:7788` (the host's wired address) in
  `shared_prefs/playback_settings.xml`.

## Phases
1. [phase-01-host-switch.md](phase-01-host-switch.md) — host switches Android's network with the cable
2. [phase-02-spieltisch-advertised-address.md](phase-02-spieltisch-advertised-address.md) — Spieltisch shows the address the host gives it
3. Verify on funstation: unplugged → media plays, QR shows `192.168.0.42:8080`, a phone joins, the
   controller app finds the TV; plugged → back to `192.168.0.44`, cache on `.43`.

## Not in scope
- Bridging over Wi-Fi (proxy ARP): keeps .44 without restarts, but Android can't get a DHCP lease after a
  boot without the cable, which is exactly today's case.
- Waiting for playback to end before switching back when the cable returns: a plug-in is rare and done
  by someone standing at the machine.

## Results (2026-11-15)
- First switch needed a restart: the system update had replaced the running kernel's modules, so
  `nft_nat` and WayDroid's legacy NAT modules could not load. After it, the boot unit chose Wi-Fi mode:
  Android `192.168.240.112` online, property `192.168.0.42`, DNAT and Avahi service in place, media app's
  cache on `192.168.240.1:7788` and reachable.
- Open: on a cable-less boot the cache server and web player start 60 s late, because they wait for
  `network-online.target` and the bridge connection never comes up (the boot before had the same).
- Open: the wired direction (plug the cable back in) is not yet tried on the machine.
