---
title: "Funstation's wallet is switched off, because nobody can answer a wallet dialog on the TV"
author: Andre
date: 2026-11-22
branch: main
cost: { minutes: 6, tokens: 178923 }
id: l-aece75a3
scope: repo
status: active
src: agent
touches:
  - plans/reports/research-261121-2320-ryzen-3-4300u-datasheet-and-optimization-techniques-report.md
  - scripts/setup-funstation.sh
---

## Decided
2026-11-22, the user saw a KDE Wallet password dialog on funstation's TV. It came from a headless Chromium run over SSH the night before (a WebGL probe): Chromium asks the wallet for a secret when it starts. The account has no login password, so the wallet can never unlock itself and the dialog stays until someone answers it. The wallet is now switched off, on funstation and in the setup script.

## Tried and ruled out
- Keeping the wallet and giving Chromium a flag to skip it: that fixes only our own probes, not the next program that asks.

## Watch out
- With the wallet off a program gets a refusal, not a dialog. Checked both ways in: the wallet's own open call returns -1, and the Secret Service name is not offered at all.
- Wi-Fi does not need the wallet: its key is stored by the system (psk-flags 0). A network added later through Bigscreen's settings was not tried with the wallet off.
- The old wallet file held nothing (60 bytes) and its password is not known.

In the code:
- scripts/setup-funstation.sh: `kwriteconfig6 --file kwalletrc --group Wallet --key Enabled false`, right after `passwd -d`
- Is a dialog open? Over SSH, with DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1000/bus: `qdbus6 --literal org.kde.KWin /WindowsRunner org.kde.krunner1.Match "Wallet"` lists a "KDE Wallet Service" window
- Close a stuck one: `pkill -x ksecretd` (it starts again on demand)
- The journal line that marks a request: ksecretd "Using kwallet without parent window!"
