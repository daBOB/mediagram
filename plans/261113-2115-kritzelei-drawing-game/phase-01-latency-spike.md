# Phase 01 — Latency spike on the real iPad (throwaway, gate)

**Priority:** first. **Status:** todo. **Spec:** "The iPad is connected early" (How — decided by the user).

## Overview

Before any game rules: install the current app on the iPad, then measure finger → TV through the bridge, the page and the box with a throwaway build. The spike lives on `spike/kritzelei-latency` in `../mediagram-kritzelei-spike` and is **never merged**. Only its report is kept.

## Key insights

- The iPad has never run the app. xtool build + install is unproven on it, so that comes first.
- The iPad 5's QR scanner is unavailable (`DataScannerViewController` needs an A12). The app finds the box only by Bonjour `_mediagram-game._tcp` with TXT `url` (`ios/controller/Sources/Spieltisch/BoxFinder.swift:13`). `game-night-dev` doesn't announce, so announce with `avahi-publish`. While doing this, turn the TV box's Spieltisch off, or the finder may take the box (it uses the first result).
- The TV role is loopback-only. On the dev PC, the TV page is `http://localhost:8080/tv` in a desktop browser.
- `UITouch.timestamp` is seconds since boot. Epoch ms of a touch is `Date().timeIntervalSince1970*1000 − (ProcessInfo.processInfo.systemUptime − touch.timestamp)*1000`.
- This measures from the touch event onward. It misses digitizer latency and the TV box's WebView. The real box is checked in phase 07.

## Task 1: The unchanged app on the iPad, joined to a dev server

**Files:** none changed.

- [ ] **Step 1:** Unplug every iOS device except the iPad (the refresh script installs on all of them). Run `scripts/refresh-ios-controllers.sh`. Expected: `iPad von Andre: aktualisiert (läuft wieder 7 Tage)`. If the iPad refuses to open the app, the user trusts the developer profile under Einstellungen → Allgemein → VPN und Geräteverwaltung. If the build or install fails, stop and report the error. Don't work around it.
- [ ] **Step 2:** `cd crates/mediagram-game/client && bun install && bun run build`, then `GAME_PORT=8080 cargo run -p mediagram-game --bin game-night-dev`. Note the join URL it prints.
- [ ] **Step 3:** `avahi-publish -s "Spieltisch dev" _mediagram-game._tcp 8080 "url=<join URL>"` (keep it running). Open `http://localhost:8080/tv`.
- [ ] **Step 4:** Open the app on the iPad. Expected: it finds the dev server, shows „Wie heißt du?“, joins, and the TV page shows the player. Note how the page looks in iOS 16 WebKit on 1024×768. Any layout breakage goes in the report.

## Task 2: Throwaway ink path (spike branch only)

**Files (all on the spike branch):**
- Modify: `crates/mediagram-game/src/protocol_client.rs` — `Action::SpikeInk { points: Vec<u16>, at: ServerTime }`
- Modify: `crates/mediagram-game/src/protocol.rs` — `ServerMsg::SpikeInk { points: Vec<u16>, at: ServerTime }`
- Modify: `crates/mediagram-game/src/room.rs` — in `handle`, a `SpikeInk` action is re-encoded as `ServerMsg::SpikeInk` and pushed to the outbox of every conn whose role is `Tv`
- Modify: `crates/mediagram-game/client/src/phone/PhoneLobby.tsx` — when `inApp()`, a „Latenztest“ button calls `mediagramApp.spike()`. It also defines `window.__spikeInk(json)`, which parses `{points, t}` and sends `{t:"action", action:{t:"spike_ink", points, at: conn.serverNow() - (Date.now() - t)}}`
- Modify: `crates/mediagram-game/client/src/tv/TvApp.tsx` — on `spike_ink`, draw the points on a fixed overlay canvas (scaled from 0..10000). In the next `requestAnimationFrame`, record `conn.serverNow() - at`. Overlay text: count, median and p95 of the last 200 samples
- Create: `ios/controller/Sources/Spieltisch/SpikeInkView.swift` — a full-screen UIView with coalesced touches that draws locally. Every 50 ms it calls `webView.evaluateJavaScript("window.__spikeInk('<json>')")` with `{points, t}`, where `t` is the epoch ms of the batch's first touch. A close button removes it
- Modify: `ios/controller/Sources/Spieltisch/GameWebView.swift` — `spike()` in the bridge script posts `{spike: true}`, and the coordinator adds `SpikeInkView` over the web view

- [ ] **Step 1:** Make the changes. `cargo build -p mediagram-game`, `bun run build`, `xtool dev build`. All build.
- [ ] **Step 2:** Install on the iPad (`scripts/refresh-ios-controllers.sh`), restart `game-night-dev`, join, tap „Latenztest“.

## Task 3: Measure, report, decide

- [ ] **Step 1:** Draw ordinary strokes for about 60 s (short lines, long loops, fast scribbles). Read the overlay: n, median, p95.
- [ ] **Step 2:** Write `plans/261113-2115-kritzelei-drawing-game/reports/spike-latency-report.md`. Include the setup, the numbers, how smooth the finger lines felt at 60 Hz, how the page rendered on iOS 16, and any install trouble.
- [ ] **Step 3:** Decide:
  - p95 ≤ 120 ms: go on to phase 02.
  - 120–200 ms: go on. Phase 07 must measure on the real TV box.
  - Over 200 ms: **stop**. Bring the numbers to the user with what dominated (batching, bridge, network, render).
- [ ] **Step 4:** Remove the spike worktree and branch (`git worktree remove ../mediagram-kritzelei-spike && git branch -D spike/kritzelei-latency`). Commit the report on `main`, together with the spec and plan if they aren't committed yet.

## Success criteria

The app runs on the iPad and joins a dev server. A p95 number exists in the report. The gate decision is written down.

## Risks

- xtool can't install on this iPad. Stop and report. Sideloadly is the documented fallback, but it runs only on Windows/macOS.
- 60 Hz touch sampling makes fast lines look jagged. Note it. Coalesced touches in phase 06 take every sample.
