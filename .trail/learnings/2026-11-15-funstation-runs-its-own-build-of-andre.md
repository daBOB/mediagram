---
title: "Funstation runs its own build of Plasma Bigscreen, because the stock Wi-Fi details dialog shows nothing and traps the remote"
author: Andre
date: 2026-11-15
branch: main
cost: { minutes: 25, tokens: 128157 }
id: l-8c52c5c6
scope: repo
status: active
src: agent
touches:
  - Cargo.lock
  - Cargo.toml
  - android/app/build.gradle.kts
  - scripts/setup-funstation.sh
  - web/package.json
  - scripts/funstation/plasma-bigscreen/PKGBUILD
---

## Decided
2026-11-15, the user chose to patch Bigscreen on funstation and make it part of the funstation setup ("add this change into our funstation distro release"). In stock Bigscreen 6.8.2 (and upstream master that day), Settings > Wi-Fi > a network > Network information shows only "Speed", and Back on the remote does nothing there. Two causes: the dialog reads the connection's old flat details list, which plasma-nm 6.8 replaced with a details model; and a Bigscreen dialog without buttons hides its button row, the one place it sends focus when it opens, while Bigscreen's Back handling only reacts to focus inside the dialog's content. The patch reads the new model and focuses the content when the button row is hidden. That second part fixes Back for every buttonless Bigscreen dialog.

## Tried and ruled out
Editing the QML files under /usr/lib/qt6/qml/org/kde/bigscreen: the module's qmldir says `prefer :/qt/qml/...`, so Qt runs the copy compiled into the plugin and ignores files on disk. The Wi-Fi page's QML is compiled into its plugin too. A rebuilt package is the only fix.

## Watch out
The setup script rebuilds only when the installed plasma-bigscreen is the same upstream release as the PKGBUILD. After a Plasma update it prints a WARNING and keeps the stock build, and the PKGBUILD needs moving to the new release (pkgver and Arch's sha256) with the patch re-checked. If upstream fixed it by then, drop the patch. The release number is the stock one's major plus .90 (1.1 becomes 1.90), so pacman -Syu keeps ours over rebuilds of the same release. Building needs a fresh package database: on funstation's 5-day-old one, makepkg could not fetch cmake (mirror 404), so run it after -Syu, never alone. Installing from a GUI package manager (Shelly) can replace the build until the script runs again.

In the code:
- scripts/funstation/plasma-bigscreen/PKGBUILD and wifi-details-and-back.patch
- scripts/setup-funstation.sh, step "Plasma Bigscreen with funstation's fixes"
