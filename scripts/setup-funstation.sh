#!/usr/bin/env bash
# Turns a fresh CachyOS (KDE Plasma) install into a funstation, the top-tier
# mediagram TV machine. Run it on the machine itself, as the user who will be
# logged in on the TV:
#
#   bash setup-funstation.sh [name]        # e.g. funstation-2
#
# Safe to run again: each step overwrites or checks its own state, and running
# it again is how a changed script reaches a funstation that already exists.
set -euo pipefail

NAME="${1:-}"
ME="$(id -un)"
# The dev machine that maintains every funstation over SSH.
DEV_KEY='ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIF2gmaWnNNxNLbjuCjdSij8+LulsCuZdEDFB7KfsG1l+'
ATV_OTA=https://waydroid-atv.github.io/ota/a16-tv
REPO_URL=https://github.com/daBOB/mediagram.git
REPO="$HOME/mediagram"
# The Fun-Station picture: the boot and splash screens, and the Android TV tile's logo.
ART="$REPO/scripts/funstation/fun-station.png"
# shortcut: follows feat/funstation until it is merged; switch to main then.
REPO_BRANCH=feat/funstation
# The TV apps ask TMDB in the library's language (else the device's); posters must ask in
# the same one to share their cache server entries.
TMDB_LANGUAGE="${TMDB_LANGUAGE:-de-DE}"

if [ "$ME" = root ]; then
  echo "run as the TV user, not root" >&2
  exit 1
fi
# Asks for the password once on a fresh install. Not `sudo -v`: that keeps asking
# even after the NOPASSWD rule below exists, because the wheel rule still wants one.
sudo true

step() { printf '\n== %s\n' "$*"; }

if [ -n "$NAME" ]; then
  step "name: $NAME"
  sudo hostnamectl set-hostname "$NAME"
fi

step "passwordless sudo for $ME, so the dev machine can maintain it"
SUDOERS=/etc/sudoers.d/90-$ME
echo "$ME ALL=(ALL) NOPASSWD: ALL" | sudo tee "$SUDOERS.new" >/dev/null
# A broken sudoers file locks sudo out, so only a valid one replaces the old.
sudo visudo -cqf "$SUDOERS.new"
sudo chmod 440 "$SUDOERS.new"
sudo mv "$SUDOERS.new" "$SUDOERS"

step "ssh from the dev machine"
sudo systemctl enable --now sshd
install -d -m 700 ~/.ssh
touch ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
grep -qF "$DEV_KEY" ~/.ssh/authorized_keys || echo "$DEV_KEY" >>~/.ssh/authorized_keys

step "packages (and the rest of the system up to date)"
sudo pacman -Syu --needed --noconfirm plasma-bigscreen waydroid chromium git rustup bun ffmpeg swayidle avahi

step "announced on the network as $(hostname).local"
sudo systemctl enable --now avahi-daemon

step "mediagram source (public repo, updated on every run)"
[ -d "$REPO/.git" ] || git clone "$REPO_URL" "$REPO"
git -C "$REPO" fetch -q origin "$REPO_BRANCH"
git -C "$REPO" checkout -q -B "$REPO_BRANCH" FETCH_HEAD

step "cache server: the LAN chunk store every player in the house reads through"
rustup default stable >/dev/null
# The uploader's CLI too: the web player runs `mediagram posters` for its cover art.
(cd "$REPO" && cargo build --release -p mediagram-cache -p mediagram)
sudo install -m 755 "$REPO/target/release/mediagram_cache" /usr/local/bin/mediagram_cache
sudo install -m 755 "$REPO/target/release/mediagram" /usr/local/bin/mediagram
sudo install -m 644 "$REPO/crates/mediagram-cache/mediagram-cache.service" /etc/systemd/system/
# 128 GiB of the SSD for chunks (the server's own default is 64 GiB).
sudo install -d /etc/systemd/system/mediagram-cache.service.d
printf '[Service]\nEnvironment=MEDIAGRAM_CACHE_BUDGET=%s\n' $((128 * 1024 * 1024 * 1024)) |
  sudo tee /etc/systemd/system/mediagram-cache.service.d/budget.conf >/dev/null
sudo systemctl daemon-reload
sudo systemctl enable mediagram-cache
# restart, not just start: a re-run that pulled new code must load the new binary.
sudo systemctl restart mediagram-cache

step "web player on port 8770, and on 80 for a bare http://funstation.local"
(cd "$REPO/web" && bun install --frozen-lockfile)
mkdir -p ~/.cache ~/.local/share/mediagram-player ~/.local/share/mediagram
# The player's own Telegram session and the library channel, kept root-only and never
# in the (public) repo; systemd reads these files as root before dropping to $ME.
# A player with neither a session nor an installed channel index cannot start, so a
# fresh machine signs in once here (api id/hash, channel, phone, code). Afterwards
# the installed index carries it, and Settings in the player can sign in again.
PLAYER_ENV=/etc/mediagram-player.env
if ! sudo grep -q '^MEDIAGRAM_SESSION=.' "$PLAYER_ENV" 2>/dev/null && [ ! -e ~/.cache/mediagram-channel-index/current ]; then
  echo "The web player needs its own Telegram session (separate from the TV app's):"
  settings=$(cd "$REPO/web" && bun run src/login.ts --stdout </dev/tty)
  # Only the Telegram lines: the unit sets the address and index path itself.
  echo "$settings" | grep -E '^MEDIAGRAM_(API_ID|API_HASH|SESSION|CHAT_ID|CHANNEL_ACCESS_HASH)=' |
    sudo install -m 600 /dev/stdin "$PLAYER_ENV"
  sudo grep -q '^MEDIAGRAM_SESSION=.' "$PLAYER_ENV" || { echo "sign-in did not produce a session" >&2; exit 1; }
fi
# Cover art: `mediagram posters` reads it from the cache server below and, with a TMDB key,
# fetches what the server lacks. Its config wants the Telegram app and channel too even
# though posters never use them. The key is asked once; empty means art only from the server.
POSTERS_CONFIG=~/.config/mediagram/config.toml
if [ ! -f "$POSTERS_CONFIG" ]; then
  read -r -s -p "TMDB v3 key or v4 token for cover art (Enter to skip): " tmdb_key </dev/tty
  echo >&2
  install -d -m 700 ~/.config/mediagram
  # A player set up from an installed index may have no env file, or no Telegram lines in
  # it: those keys stay unfilled instead of aborting the setup, and the rest is still written.
  { sudo sed -n -E 's/^MEDIAGRAM_API_ID=(.*)/api_id = \1/p; s/^MEDIAGRAM_API_HASH=(.*)/api_hash = "\1"/p; s/^MEDIAGRAM_CHAT_ID=(.*)/channel = "\1"/p' "$PLAYER_ENV" 2>/dev/null || true; } |
    install -m 600 /dev/stdin "$POSTERS_CONFIG"
  [ -z "$tmdb_key" ] || echo "tmdb_key = \"$tmdb_key\"" >>"$POSTERS_CONFIG"
fi
# Only when absent, so a language set by hand survives a re-run.
grep -Eq '^tmdb_language[[:space:]]*=' "$POSTERS_CONFIG" ||
  echo "tmdb_language = \"$TMDB_LANGUAGE\"" >>"$POSTERS_CONFIG"
# The player reads and fills the same cache server as the Android app; the token lets it write.
for _ in $(seq 1 20); do sudo test -s /var/lib/mediagram-cache/token && break; sleep 0.5; done
CACHE_TOKEN=$(sudo cat /var/lib/mediagram-cache/token)
{
  echo "MEDIAGRAM_CACHE_SERVER=http://127.0.0.1:7788"
  echo "MEDIAGRAM_CACHE_SERVER_TOKEN=$CACHE_TOKEN"
} | sudo install -m 600 /dev/stdin /etc/mediagram-cache-client.env
# Posters too, so art one player fetched is every player's. Replaced on every run: a
# recreated server token would leave posters signing with the old one and getting 401s.
# 600 first, so the copy sed writes is never readable by others either.
chmod 600 "$POSTERS_CONFIG"
sed -i -E '/^cache_server(_token)?[[:space:]]*=/d' "$POSTERS_CONFIG"
{
  echo 'cache_server = "http://127.0.0.1:7788"'
  echo "cache_server_token = \"$CACHE_TOKEN\""
} >>"$POSTERS_CONFIG"
sudo tee /etc/systemd/system/mediagram-player.service >/dev/null <<EOF
[Unit]
Description=mediagram web player
After=network-online.target
Wants=network-online.target

[Service]
User=$ME
WorkingDirectory=$REPO/web
EnvironmentFile=$PLAYER_ENV
EnvironmentFile=/etc/mediagram-cache-client.env
Environment=MEDIAGRAM_PLAYER_ADDR=0.0.0.0:8770
# Profiles, Continue and My List are the household's, synced through Telegram like every player's.
Environment=MEDIAGRAM_SYNC_STATE=1
# Only read before the channel's index was ever installed; posters land beside it.
Environment=MEDIAGRAM_LIBRARY_DB=$HOME/.local/share/mediagram/library.db
ExecStart=/usr/bin/bun run src/index.ts
Restart=on-failure
# It holds a Telegram auth key and spawns ffmpeg; it writes only its caches and its own state.
PrivateTmp=true
ProtectSystem=strict
ProtectHome=read-only
ReadWritePaths=$HOME/.cache $HOME/.local/share/mediagram-player $HOME/.local/share/mediagram
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
EOF
sudo systemctl daemon-reload
sudo systemctl enable mediagram-player
sudo systemctl restart mediagram-player
sudo ufw allow 7788/tcp comment "mediagram cache server"
sudo ufw allow 8770/tcp comment "mediagram web player"
# A browser given the bare name (http://funstation.local) asks port 80. The kernel hands
# that to the player rather than a proxy relaying it, so the player still sees each
# viewer's own address, which decides direct play and who may see its panel. A table of
# its own, which ufw leaves alone; nftables.service stays off, its config flushes ufw's too.
sudo tee /etc/funstation-http.nft >/dev/null <<'EOF'
destroy table inet funstation_http
table inet funstation_http {
  chain prerouting {
    type nat hook prerouting priority dstnat; policy accept;
    fib daddr type local tcp dport 80 redirect to :8770
  }
}
EOF
sudo tee /etc/systemd/system/funstation-http.service >/dev/null <<'EOF'
[Unit]
Description=Port 80 reaches the mediagram web player

[Service]
Type=oneshot
RemainAfterExit=true
ExecStart=/usr/bin/nft -f /etc/funstation-http.nft

[Install]
WantedBy=multi-user.target
EOF
sudo systemctl daemon-reload
sudo systemctl enable funstation-http
sudo systemctl restart funstation-http

step "Plasma never dims, blanks, sleeps or locks: funstation's own idle step below darkens it"
kwriteconfig6 --file powerdevilrc --group AC --group SuspendAndShutdown --key AutoSuspendAction 0
kwriteconfig6 --file powerdevilrc --group AC --group Display --key DimDisplayWhenIdle false
kwriteconfig6 --file powerdevilrc --group AC --group Display --key TurnOffDisplayWhenIdle false
kwriteconfig6 --file kscreenlockerrc --group Daemon --key Autolock false
kwriteconfig6 --file kscreenlockerrc --group Daemon --key LockOnResume false
# No password: nobody can type one with a TV remote, and a lock screen can still come up
# by hand. PAM's nullok lets OK through it; SSH refuses empty passwords by default.
sudo passwd -d "$ME" >/dev/null
systemctl --user try-restart plasma-powerdevil 2>/dev/null || true
# The CachyOS welcome window would cover the TV interface after every boot.
rm -f ~/.config/autostart/cachyos-hello.desktop
# A whole-number scale, so Android TV fills the screen: WayDroid rounds a fractional one
# (KWin picks 1.7 for this TV) up to 2 whenever the TV reconnects, and Android then covers
# only 85% of it. At 2 Bigscreen gets the 1080p layout it is designed for.
# shortcut: keyed to the HDMI port; a screen on another port keeps KWin's own scale.
mkdir -p ~/.config/autostart
cat >~/.config/autostart/funstation-tv-scale.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=TV at scale 2
Exec=kscreen-doctor output.HDMI-A-1.scale.2
NoDisplay=true
EOF

step "Android TV 16 (WayDroid-ATV, with Google apps)"
if ! sudo test -f /var/lib/waydroid/images/system.img; then
  sudo waydroid init -f -c "$ATV_OTA/system" -v "$ATV_OTA/vendor" -r lineage -s GAPPS
fi
# Without this the setup wizard waits forever for a Bluetooth remote to pair.
PROPS=/var/lib/waydroid/waydroid_base.prop
sudo grep -qx 'atv.setup.bt_remote_pairing=false' "$PROPS" ||
  echo 'atv.setup.bt_remote_pairing=false' | sudo tee -a "$PROPS" >/dev/null
# CachyOS ships ufw switched on; it drops Android's DHCP and DNS on the WayDroid
# bridge, so Android never gets an address. ufw skips rules it already has.
sudo ufw allow in on waydroid0 comment "WayDroid DHCP and DNS"
sudo ufw route allow in on waydroid0 comment "WayDroid to the internet"
sudo systemctl enable --now waydroid-container

step "Android TV runs from login on, and Bigscreen has tiles for it and for controllers"
mkdir -p ~/.config/systemd/user ~/.local/share/applications
# No ExecStop: when start finds a session already running it exits at once, and a
# stop hook would then end the very session it found.
cat >~/.config/systemd/user/android-tv-session.service <<'EOF'
[Unit]
Description=Android TV (WayDroid session), ready before anyone opens it
PartOf=graphical-session.target
After=graphical-session.target

[Service]
ExecStart=/usr/bin/waydroid session start
Restart=on-failure

[Install]
WantedBy=graphical-session.target
EOF
# Both Android tiles bring up the full Android window, mediagram's after starting it.
mkdir -p ~/.local/bin
cat >~/.local/bin/funstation-android <<'EOF'
#!/bin/sh
# Shows the full Android window, after starting the app named by $1 if one is.
# In WayDroid's one-app-window mode an app whose window was once closed stays hidden
# while it is still Android's top app, so apps open in the full window too.
[ $# -gt 0 ] && waydroid app launch "$1"
waydroid show-full-ui
# WayDroid opens its window only when Android draws its next frame, and its own redraw
# trick (pulling the notification shade) does nothing on Android TV: force that frame.
sudo -n waydroid shell -- service call SurfaceFlinger 1004 >/dev/null
# Bigscreen minimizes the window when its home screen comes up, and WayDroid never
# restores a window it already has, so KWin does.
js=$(mktemp --suffix=.js)
echo 'workspace.windowList().forEach(w => { if (w.resourceClass == "Waydroid") { w.minimized = false; workspace.activeWindow = w; } });' >"$js"
qdbus6 org.kde.KWin /Scripting org.kde.kwin.Scripting.unloadScript funstation-android >/dev/null 2>&1
id=$(qdbus6 org.kde.KWin /Scripting org.kde.kwin.Scripting.loadScript "$js" funstation-android)
qdbus6 org.kde.KWin "/Scripting/Script$id" org.kde.kwin.Script.run
qdbus6 org.kde.KWin /Scripting org.kde.kwin.Scripting.unloadScript funstation-android >/dev/null
rm -f "$js"
EOF
chmod +x ~/.local/bin/funstation-android
# WayDroid lists only phone-launcher apps; mediagram is TV-only, so it gets its own tile.
cat >~/.local/share/applications/mediagram.desktop <<EOF
[Desktop Entry]
Type=Application
Name=mediagram
Comment=Films, series and game night
Exec=$HOME/.local/bin/funstation-android com.mediagram.android
Icon=video-television
Categories=AudioVideo;Video;Player;
EOF
# The Android TV tile wears the Fun-Station logo, which Bigscreen's loading screen shows
# too: the logo cut from the picture, its dark sky made transparent for the tile's card.
# Two magick runs: chained into one, the grey mask took the logo's colour with it.
# shortcut: crop and star box measured on this picture; a new picture needs new numbers.
ICON=~/.local/share/icons/fun-station.png
LOGO=$(mktemp -d)
mkdir -p "$(dirname "$ICON")"
magick "$ART" -crop 1240x470+410+195 +repage "$LOGO/logo.png"
magick "$LOGO/logo.png" -colorspace gray -level 6%,40% -fill black -draw "rectangle 1150,0 1240,200" "$LOGO/mask.png"
magick "$LOGO/logo.png" "$LOGO/mask.png" -alpha off -compose CopyOpacity -composite -colorspace sRGB "$LOGO/cut.png"
magick "$LOGO/cut.png" -channel A -morphology Open Disk:3 +channel -trim +repage -resize 496x496 \
  -background none -gravity center -extent 512x512 "$ICON"
rm -rf "$LOGO"
cat >~/.local/share/applications/android-tv.desktop <<EOF
[Desktop Entry]
Type=Application
Name=Android TV
Comment=The Android TV home screen
Exec=$HOME/.local/bin/funstation-android
Icon=$ICON
Categories=AudioVideo;Video;
EOF
# Bigscreen's own Bluetooth page, one tile away instead of three menus deep.
cat >~/.local/share/applications/controllers.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=Controllers
Comment=Pair gamepads and remotes over Bluetooth
Exec=plasma-bigscreen-settings -m kcm_mediacenter_bluetooth
Icon=input-gamepad
Categories=Settings;HardwareSettings;
EOF
# WayDroid's own tile for the TV remote service opens a translucent splash that never
# finishes and freezes the Android TV window behind it. WayDroid keeps a NoDisplay it
# finds when it rewrites its tiles, so this holds even before the tile first exists.
kwriteconfig6 --file ~/.local/share/applications/waydroid.com.google.android.tv.remote.service.desktop \
  --group "Desktop Entry" --key NoDisplay true
# Bigscreen reads its tiles from Plasma's app cache, which missed the rewritten files and
# kept running the tiles' old commands. Rebuilt inside the session, so it is the session
# locale's cache and not one for this shell's.
systemd-run --user --wait --quiet kbuildsycoca6 >/dev/null 2>&1 || true
systemctl --user daemon-reload 2>/dev/null || true
systemctl --user enable android-tv-session.service

step "after an hour without input the screen goes dark and Android sleeps"
# HDMI off alone left the CPU near 20 W, because Android kept drawing; with Android's
# display asleep too it drew 2.4 W. Android keeps running, and any key wakes both in
# under a second. Playback and Spieltisch keep the screen on, which holds the timer.
cat >~/.config/systemd/user/funstation-idle.service <<'EOF'
[Unit]
Description=After an hour without input, the screen goes dark and Android sleeps
PartOf=graphical-session.target
After=graphical-session.target

[Service]
ExecStart=/usr/bin/swayidle timeout 3600 'kscreen-doctor --dpms off; sudo -n waydroid shell -- input keyevent KEYCODE_SLEEP' resume 'sudo -n waydroid shell -- input keyevent KEYCODE_WAKEUP; kscreen-doctor --dpms on'
Restart=on-failure

[Install]
WantedBy=graphical-session.target
EOF
systemctl --user daemon-reload 2>/dev/null || true
systemctl --user enable funstation-idle.service
# Android TV's Chromecast receiver gets no device certificate under WayDroid; it crashed
# and restarted every 10-20 s around the clock. mediagram's Play on TV does not use it.
# shortcut: only while Android runs; on a fresh install, run this script again after the first boot.
if waydroid status 2>/dev/null | grep -q 'Session:.*RUNNING'; then
  sudo waydroid shell -- pm disable-user --user 0 com.google.android.apps.mediashell >/dev/null
fi

step "the remote's battery at the foot of the mediagram rail"
# Android under WayDroid cannot see the Bluetooth remote, which is paired to this host.
# UPower reads its battery here, and the app takes the number from a broadcast that only
# root and the shell may send; a device nobody tells, like the TV box, never shows one.
mkdir -p ~/.local/bin
cat >~/.local/bin/funstation-remote-battery <<'EOF'
#!/usr/bin/env bash
# Sent when it changes, and every ten minutes for an app that was not running to hear it.
last='' sent=0
while :; do
  pct=''
  for dev in $(upower -e); do
    info=$(upower -i "$dev")
    if grep -qi 'model:.*remote' <<<"$info"; then
      pct=$(awk '/percentage:/ { print int($2); exit }' <<<"$info")
      break
    fi
  done
  now=$(date +%s)
  if [ -n "$pct" ] && { [ "$pct" != "$last" ] || [ $((now - sent)) -ge 600 ]; }; then
    sudo -n waydroid shell -- am broadcast -a com.mediagram.android.REMOTE_BATTERY \
      -p com.mediagram.android --ei percent "$pct" >/dev/null 2>&1 && { last=$pct; sent=$now; }
  fi
  sleep 60
done
EOF
chmod +x ~/.local/bin/funstation-remote-battery
cat >~/.config/systemd/user/funstation-remote-battery.service <<'EOF'
[Unit]
Description=The TV remote's battery, handed to the mediagram app in Android
PartOf=graphical-session.target
After=graphical-session.target

[Service]
ExecStart=%h/.local/bin/funstation-remote-battery
Restart=on-failure

[Install]
WantedBy=graphical-session.target
EOF
systemctl --user daemon-reload 2>/dev/null || true
systemctl --user enable funstation-remote-battery.service

step "Bluetooth on the USB stick only"
# The remote and controllers pair on a USB Bluetooth stick (the user's choice). With the
# built-in Realtek radio on as well, a pairing could land on either and be lost with it.
# shortcut: keyed to this machine's Realtek part (0bda:c821); another radio stays on.
sudo tee /etc/udev/rules.d/80-funstation-builtin-bluetooth-off.rules >/dev/null <<'EOF'
ACTION=="add", SUBSYSTEM=="usb", ATTR{idVendor}=="0bda", ATTR{idProduct}=="c821", ATTR{authorized}="0"
EOF
sudo udevadm control --reload
# The Chromecast remote's OK sends Select, which Bigscreen ignores outside its lock screen;
# it waits for Enter. Android's key map takes Enter as OK too, so OK works in both.
sudo tee /etc/udev/hwdb.d/70-funstation-chromecast-remote.hwdb >/dev/null <<'EOF'
evdev:input:b0005v18D1p9450*
 KEYBOARD_KEY_c0041=enter
EOF
sudo systemd-hwdb update
sudo udevadm trigger --subsystem-match=input --action=change

step "log $ME straight into Plasma Bigscreen"
printf '[Autologin]\nUser=%s\nSession=plasma-bigscreen-wayland\n' "$ME" |
  sudo tee /etc/plasmalogin.conf >/dev/null

step "the Fun-Station picture from power-on to Bigscreen, and while Android starts"
# Plymouth, from power-on until the login, scaled to whatever the screen is.
THEME=/usr/share/plymouth/themes/funstation
sudo install -Dm644 "$ART" "$THEME/fun-station.png"
sudo tee "$THEME/funstation.plymouth" >/dev/null <<EOF
[Plymouth Theme]
Name=Fun-Station
Description=The Fun-Station picture, full screen
ModuleName=script

[script]
ImageDir=$THEME
ScriptFile=$THEME/funstation.script
EOF
sudo tee "$THEME/funstation.script" >/dev/null <<'EOF'
Window.SetBackgroundTopColor(0, 0, 0);
Window.SetBackgroundBottomColor(0, 0, 0);
picture = Sprite(Image("fun-station.png").Scale(Window.GetWidth(), Window.GetHeight()));
picture.SetPosition(Window.GetX(), Window.GetY(), 0);
EOF
# -R rebuilds the initramfs, which carries the theme; only when the theme changes.
[ "$(plymouth-set-default-theme)" = funstation ] || sudo plymouth-set-default-theme -R funstation
# Plasma's own splash after the autologin, so the picture stays up until Bigscreen.
SPLASH=~/.local/share/plasma/look-and-feel/org.funstation.splash
install -Dm644 "$ART" "$SPLASH/contents/splash/images/fun-station.png"
cat >"$SPLASH/metadata.json" <<'EOF'
{ "KPackageStructure": "Plasma/LookAndFeel",
  "KPlugin": { "Id": "org.funstation.splash", "Name": "Fun-Station", "Description": "The Fun-Station picture as the login splash" } }
EOF
cat >"$SPLASH/contents/splash/Splash.qml" <<'EOF'
import QtQuick

Rectangle {
    property int stage // set by ksplashqml as the session starts; the picture needs no steps
    color: "black"
    Image { anchors.fill: parent; source: "images/fun-station.png"; fillMode: Image.PreserveAspectCrop }
}
EOF
kwriteconfig6 --file ksplashrc --group KSplash --key Engine KSplashQML
kwriteconfig6 --file ksplashrc --group KSplash --key Theme org.funstation.splash
# Android's boot animation: one frame, which bootanimation draws unscaled and centred.
# shortcut: sized for a 4K TV (Android's display here); a 1080p TV would show its middle.
BOOTANIM=$(mktemp -d)
mkdir "$BOOTANIM/part0"
magick "$ART" -resize 3840x2160^ -gravity center -extent 3840x2160 "$BOOTANIM/part0/00000.png"
printf '3840 2160 1\np 0 0 part0\n' >"$BOOTANIM/desc.txt"
# Stored, not deflated: bootanimation reads its frames straight out of the zip.
(cd "$BOOTANIM" && python3 -c 'import sys, zipfile
with zipfile.ZipFile("bootanimation.zip", "w", zipfile.ZIP_STORED) as z:
    for name in sys.argv[1:]: z.write(name)' desc.txt part0/00000.png)
sudo install -Dm644 "$BOOTANIM/bootanimation.zip" /var/lib/waydroid/overlay/system/product/media/bootanimation.zip
rm -rf "$BOOTANIM"

# Last, because the switch drops the wired link and any SSH session running over it.
step "Android TV on the home network, so phones can join its game night"
# Behind WayDroid's own NAT bridge only this machine reaches Android: a phone can't open
# its game night (port 8080) or find it over mDNS. On a bridge over the wired port
# Android gets an address from the home router like any other device, and WayDroid's
# network script steps aside for any bridge that isn't its own waydroid0.
# shortcut: wired only, since a Wi-Fi client can't carry another device's MAC; a
# Wi-Fi-only funstation keeps the NAT bridge, and phones can't join its game night.
LXC=/var/lib/waydroid/lxc/waydroid/config
WIRED=$(nmcli -t -f DEVICE,TYPE,STATE device | awk -F: '$2 == "ethernet" && $3 == "connected" {print $1; exit}')
if [ -n "$WIRED" ] && ! nmcli -t -f NAME con show | grep -qx br0; then
  OLD=$(nmcli -g GENERAL.CONNECTION device show "$WIRED")
  # The bridge carries the port's MAC, so the router hands this machine its old address.
  sudo nmcli con add type bridge ifname br0 con-name br0 bridge.stp no \
    bridge.mac-address "$(cat "/sys/class/net/$WIRED/address")" >/dev/null
  sudo nmcli con add type ethernet ifname "$WIRED" master br0 con-name "br0-$WIRED" >/dev/null
  # In a unit of its own, so it finishes even when it cuts off the SSH session that started it.
  sudo systemd-run --quiet --wait --collect --unit funstation-bridge sh -c \
    "nmcli con mod '$OLD' connection.autoconnect no; nmcli con up 'br0-$WIRED'; nmcli con up br0"
fi
if nmcli -t -f NAME con show | grep -qx br0 && ! sudo grep -qx 'lxc.net.0.link = br0' "$LXC"; then
  sudo sed -i 's/^lxc\.net\.0\.link = .*/lxc.net.0.link = br0/' "$LXC"
  sudo systemctl restart waydroid-container
  # The session ends with the container; without a TV session yet it starts at the next login.
  systemctl --user restart android-tv-session 2>/dev/null || true
fi

printf '\nfunstation ready: reboot to start the TV interface\n'
