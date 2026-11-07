# Game Night Controller Apps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Native Android and iPhone/iPad apps that find the TV box on the Wi-Fi, show its game-night phone page, keep the screen on and vibrate at four moments.

**Architecture:** Each app is a full-screen web view on the box's phone page plus a small native bridge (`window.mediagramApp`). The page decides when to buzz and stores the player's memory through the bridge; the TV app announces game night by DNS-SD with the join URL in a TXT record.

**Tech Stack:** React/TS phone page (bun test), Kotlin + Compose + WebView + NsdManager (Android), Swift + SwiftUI + WKWebView + Network.framework built with xtool on Linux (iOS).

**Spec:** `docs/superpowers/specs/2026-11-07-controller-apps-design.md` — every phase reads it.

Worktree `../mediagram-controller`, branch `feat/controller-apps` (off `feat/spieltisch`).

## Global Constraints

- Guests in a plain browser see no change: QR → `http://<box>:8080/` keeps working exactly as now.
- One set of phone screens: no game screen is redrawn natively.
- Nothing needs a Mac. iOS: xtool (Swift 6.4, Xcode 27.1 `.xip`), free Apple ID, Sideloadly as backup (Windows/macOS).
- Name on the home screen: **Spieltisch**. App UI copy is German.
- Android `applicationId` and iOS `bundleID`: `com.mediagram.controller` (fixed: a free Apple ID allows 10 new App IDs per 7 days).
- Versioning (CLAUDE.md § Versioning): bump by pattern, never exact string; read the real current version with `git show HEAD:Cargo.toml | grep -m1 ^version` first. The first code commit takes the next minor above `feat/spieltisch`; later commits bump patch.
- Device commands always pin a serial: `ANDROID_SERIAL=<serial>`. Never install on the TV box except in phase 06.
- No plan references (phase numbers, finding codes) in code comments, test names or commit messages.

## Contracts (every phase uses these exact names)

- **JS bridge** `window.mediagramApp` (present only inside the apps):
  `vibrate(kind: string): void` · `getMemory(): string` (JSON object, string values) · `setMemory(key: string, value: string | null): void`.
- **Buzz kinds:** `"turn" | "accepted" | "wrong" | "won"`. Unknown kinds do nothing.
- **Page memory keys:** `spieltisch.name`, `spieltisch.token`, `spieltisch.avatar` (already used by the page).
- **DNS-SD:** type `_mediagram-game._tcp`, instance name `Spieltisch`, port = host port, TXT `url=<joinUrl>` (exactly the URL the QR code shows).
- **LAN URL rule** (both apps, for TXT urls and scanned QR codes): accept only `http://` whose host is an IPv4 address in 10.0.0.0/8, 172.16.0.0/12 or 192.168.0.0/16, or a name ending `.local`; any port; path kept. Anything else is rejected.
- **Same box:** two URLs are the same box when scheme, host and port match. The web view never leaves the current box.
- **Controller screen reducer** (both apps): screens `Searching(notice: String?)`, `Playing(url)`; events `Found(url)`, `Scanned(raw)`, `PageFailed(url)`. `start(remembered)` = `Playing(remembered)` or `Searching(null)`. `Found(u)` → `Playing(u)` (no change if already `Playing(u)`). `Scanned(raw)` → `Playing(lanUrl)` or `Searching("Das ist nicht der Code vom Fernseher.")`. `PageFailed(u)` → `Searching(null)` only when currently `Playing(u)`. Entering `Searching` restarts discovery.

## Review Focus

1. The host re-sends the same screen on every change (other players picking) → the phone must not buzz again. Pinned in phase 02 (`buzz-moments.test.ts`, "same screen again").
2. The box got a new address since last night → the remembered URL is dead → discovery's URL wins without waiting on a white page. Pinned in phases 04/05 (reducer: `Playing(a)` + `Found(b)` → `Playing(b)`).
3. The TV leaves and restarts game night while the app is open → the app follows. Pinned in phases 04/05 (reducer: `PageFailed` → `Searching`, then `Found`).
4. A guest scans some other QR code (a website, a Wi-Fi code) → rejected with a notice, app stays waiting. Pinned in phases 04/05 (LAN URL table + reducer `Scanned`).
5. A link or redirect would take the web view off the box → blocked, so the bridge is never exposed to another origin. Pinned in phases 04/05 (`sameBox` table).

## Phases

| # | Phase | Depends on | Status |
|---|-------|-----------|--------|
| 01 | [iOS tooling on Linux (with the user)](phase-01-ios-tooling-on-linux.md) | — | todo |
| 02 | [Phone page: bridge, buzz moments, memory](phase-02-phone-page-bridge-and-buzz.md) | — | todo |
| 03 | [TV announces game night](phase-03-tv-announces-game-night.md) | — | todo |
| 04 | [Android controller app](phase-04-android-controller-app.md) | 02 | todo |
| 05 | [iOS controller app + weekly refresh](phase-05-ios-controller-app.md) | 01, 02 | todo |
| 06 | [Box walk, docs, versioning](phase-06-box-walk-docs-and-versioning.md) | 02–05 | todo |

Dev loop without the box (phases 04/05): run the dev host
`cargo run -p mediagram-game --bin game-night-dev` and announce it with
`avahi-publish -s Spieltisch _mediagram-game._tcp 8080 url=http://<this machine's LAN IP>:8080/`.
