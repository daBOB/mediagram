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
# shortcut: follows feat/funstation until it is merged; switch to main then.
REPO_BRANCH=feat/funstation

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
sudo pacman -Syu --needed --noconfirm plasma-bigscreen waydroid chromium git rustup bun ffmpeg

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

step "web player on port 8770"
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
# Cover art: `mediagram posters` needs a TMDB key, and its config wants the Telegram app
# and channel too even though posters never use them. Asked once; empty means no art.
POSTERS_CONFIG=~/.config/mediagram/config.toml
if [ ! -f "$POSTERS_CONFIG" ]; then
  read -r -p "TMDB v3 key or v4 token for cover art (Enter to skip): " tmdb_key </dev/tty
  if [ -n "$tmdb_key" ]; then
    install -d -m 700 ~/.config/mediagram
    sudo sed -n -E 's/^MEDIAGRAM_API_ID=(.*)/api_id = \1/p; s/^MEDIAGRAM_API_HASH=(.*)/api_hash = "\1"/p; s/^MEDIAGRAM_CHAT_ID=(.*)/channel = "\1"/p' "$PLAYER_ENV" |
      install -m 600 /dev/stdin "$POSTERS_CONFIG"
    echo "tmdb_key = \"$tmdb_key\"" >>"$POSTERS_CONFIG"
  fi
fi
# The player reads and fills the same cache server as the Android app; the token lets it write.
for _ in $(seq 1 20); do sudo test -s /var/lib/mediagram-cache/token && break; sleep 0.5; done
{
  echo "MEDIAGRAM_CACHE_SERVER=http://127.0.0.1:7788"
  echo "MEDIAGRAM_CACHE_SERVER_TOKEN=$(sudo cat /var/lib/mediagram-cache/token)"
} | sudo install -m 600 /dev/stdin /etc/mediagram-cache-client.env
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

step "never dim, blank, sleep or lock: it is a TV box"
kwriteconfig6 --file powerdevilrc --group AC --group SuspendAndShutdown --key AutoSuspendAction 0
kwriteconfig6 --file powerdevilrc --group AC --group Display --key DimDisplayWhenIdle false
kwriteconfig6 --file powerdevilrc --group AC --group Display --key TurnOffDisplayWhenIdle false
kwriteconfig6 --file kscreenlockerrc --group Daemon --key Autolock false
kwriteconfig6 --file kscreenlockerrc --group Daemon --key LockOnResume false
systemctl --user try-restart plasma-powerdevil 2>/dev/null || true
# The CachyOS welcome window would cover the TV interface after every boot.
rm -f ~/.config/autostart/cachyos-hello.desktop

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

step "Android TV runs from login on, and Bigscreen has tiles for it"
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
# WayDroid lists only phone-launcher apps; mediagram is TV-only, so it gets its own tile.
cat >~/.local/share/applications/mediagram.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=mediagram
Comment=Films, series and game night
Exec=waydroid app launch com.mediagram.android
Icon=video-television
Categories=AudioVideo;Video;Player;
EOF
cat >~/.local/share/applications/android-tv.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=Android TV
Comment=The Android TV home screen
Exec=waydroid show-full-ui
Icon=waydroid
Categories=AudioVideo;Video;
EOF
systemctl --user daemon-reload 2>/dev/null || true
systemctl --user enable android-tv-session.service

step "log $ME straight into Plasma Bigscreen"
printf '[Autologin]\nUser=%s\nSession=plasma-bigscreen-wayland\n' "$ME" |
  sudo tee /etc/plasmalogin.conf >/dev/null

printf '\nfunstation ready: reboot to start the TV interface\n'
