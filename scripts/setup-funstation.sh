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
(cd "$REPO" && cargo build --release -p mediagram-cache)
sudo install -m 755 "$REPO/target/release/mediagram_cache" /usr/local/bin/mediagram_cache
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
# The Telegram app and the library channel: asked once, kept root-only, never in the
# (public) repo. systemd reads these files as root before dropping to $ME.
PLAYER_ENV=/etc/mediagram-player.env
sudo install -m 600 /dev/null "$PLAYER_ENV.new"
sudo test -f "$PLAYER_ENV" && sudo cp "$PLAYER_ENV" "$PLAYER_ENV.new"
setting() {
  sudo grep -q "^$1=." "$PLAYER_ENV.new" && return
  local value
  read -r -p "$2: " value </dev/tty
  echo "$1=$value" | sudo tee -a "$PLAYER_ENV.new" >/dev/null
}
setting MEDIAGRAM_API_ID "Telegram api_id (my.telegram.org)"
setting MEDIAGRAM_API_HASH "Telegram api_hash"
setting MEDIAGRAM_CHAT_ID "library channel id (-100...)"
setting MEDIAGRAM_CHANNEL_ACCESS_HASH "library channel access hash"
sudo mv "$PLAYER_ENV.new" "$PLAYER_ENV"
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

step "log $ME straight into Plasma Bigscreen"
printf '[Autologin]\nUser=%s\nSession=plasma-bigscreen-wayland\n' "$ME" |
  sudo tee /etc/plasmalogin.conf >/dev/null

printf '\nfunstation ready: reboot to start the TV interface\n'
