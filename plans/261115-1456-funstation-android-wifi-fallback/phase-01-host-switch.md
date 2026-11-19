# Phase 1: the host switches Android's network with the cable

Priority: high. Status: done; live on funstation since the 15:07 restart.

## What
One root helper, `funstation-android-net`, installed by `scripts/setup-funstation.sh`:

- **Mode** from the bridge port's carrier (`/sys/class/net/<port>/carrier`): `wired` → `lxc.net.0.link =
  br0`; `wifi` → `lxc.net.0.link = waydroid0`. No change → exit (idempotent; most calls do nothing).
- **On a change**, before restarting the container:
  - `wifi`: `funstation.lan_address=<host's Wi-Fi IPv4>` into `/var/lib/waydroid/waydroid_base.prop`;
    media app's cache address → `http://192.168.240.1:7788`.
  - `wired`: drop the property; cache address → `http://<host br0 IPv4>:7788`.
  - Cache address edited in `~/.local/share/waydroid/data/data/com.mediagram.android/shared_prefs/
    playback_settings.xml` while the container is down (the way it was changed by hand on 2026-11-11).
  - `systemctl restart waydroid-container`, then the TV user's `android-tv-session`.
- **Wi-Fi mode extras** (removed in wired mode, else Android's own announcement is doubled):
  - nftables table `funstation-spieltisch`: `iifname wlan0 tcp dport 8080 dnat to 192.168.240.112:8080`
    (own table, like `funstation-http`; never `nftables.service`, see the port-80 learning).
  - ufw: route allow wlan0 → waydroid0 port 8080.
  - Avahi static service `/etc/avahi/services/funstation-spieltisch.service`: `_mediagram-game._tcp`,
    name "Spieltisch", port 8080, TXT `url=http://<Wi-Fi IPv4>:8080/`.

## Triggers
- NetworkManager dispatcher `/etc/NetworkManager/dispatcher.d/90-funstation-android-net` on up/down of
  the bridge port: waits 10 s, then runs the helper (a flapping cable switches once).
- A oneshot unit `Before=waydroid-container.service` at boot, so a cable-less boot starts in Wi-Fi mode
  instead of with no network.

## Files
- Modify: `scripts/setup-funstation.sh` (new step after "Android TV on the home network")
- Create: `scripts/funstation/funstation-android-net` (the helper; installed to `/usr/local/bin`)

## Done when
- `bash -n` passes; helper run twice in a row changes nothing the second time.
- Unplugged: Android has `192.168.240.112`, reaches the internet, media plays via the cache server.
- Plugged: Android back on `192.168.0.44`, no nft table, no Avahi service file.

## Risks
- `waydroid-net.sh` uses iptables-legacy while ufw/nft are nft: DNAT in an nft table plus legacy
  MASQUERADE both hook in; verify a phone's connection actually arrives.
- Wi-Fi IP changes (DHCP) while in Wi-Fi mode: the shown address goes stale until the next switch.
  Mitigation: dispatcher also reruns on wlan0 `dhcp4-change`, and the helper compares the property.
