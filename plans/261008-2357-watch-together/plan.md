# Watch Together Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Screens in one household play one title in step (within about 0.5 s) across the web, phone/tablet and TV. Anyone can play, pause, seek, change speed or move on to the next episode.

**Architecture:**
- **One unpinned `#mlib-together v=1` text message per session**, edited in place; the last edit wins.
- **The pure rules live once on the web**, in `web/public/lib/together/` (format, moves, follow and decide). Android ports them to Kotlin in `core/model`, and both are held to the same fixtures.
- **The web server and the Rust core only do I/O.** They read, send, edit and delete the message, and push together edits at once, skipping the 5 s debouncer.
- **Each player follows the session and reports only its viewer's own moves:**
  - on the web, through the player's call paths;
  - on Android, through a `ForwardingPlayer`.

**Tech Stack:** Bun/TS (server), browser ES modules, teleproto, Rust + grammers + UniFFI, Kotlin/Compose/media3, kotlinx-serialization-json (element API).

**Spec:** `plans/261008-2357-watch-together/spec.md`

## Global Constraints

- **Version:** 0.120.0 (the next minor), in all three manifests at once (`CLAUDE.md` § Versioning).
- **The web is the reference** for wording and layout. A deliberate difference is written down (`CLAUDE.md` § Surface Parity).
- **Line limits:** every new file is under 200 lines. A file on the ratchet that grows gets its ceiling raised with a dated sentence (`web/test/code-standards.test.ts`). Rust files are under 200 lines (`crates/mediagram/tests/code_standards.rs`).
- **Pins:** never pin together messages. The pin flood limit was measured at FLOOD_WAIT_633.
- **Discovery:** never use channel search to find a session; it misses fresh messages.
- **Commits:** conventional commits, with no AI references. No plan labels in code or test names.
- **Devices:**
  - always `ANDROID_SERIAL=caad49da` for the tablet, and `192.168.0.46:5555` for the box;
  - on the box, navigate only;
  - ask before `scripts/release-android.sh`.
- **The native core:** rebuild the `.so`s with the bindings after any core change.

## Review Focus

1. **Two screens act in the same second.** Both must end up in the state Telegram kept, which is the last edit. That relies on the echo rule in `follow`, covered by the `follow.json` cases and controller test 3. Also check that the web hub's `heard` doesn't drop a foreign edit while an own write is queued.
2. **A screen buffering a large file** (the Android byte path crawls on big files) must not seek-loop. Drift checks skip while buffering (web test 5, Android test 5). Watch for it on the walk with a 4K title on the box.
3. **The viewer's own moves through paths no test touches:** the headset button, the lock screen, PiP actions, the TV remote's `seekBy`, keyboard keys on the web. Each must report exactly once and never while following. The Android `ViewerPlayer` test with `Util.handlePlayPauseButtonAction`, and web test 3, cover the common paths. The walk covers the rest.
4. **A kids profile on one screen while the session moves to an older-rated title.** That screen leaves, with a notice (Android test 7). The web has no per-tab kids gate beyond the catalog it shows; Join is offered only for visible titles.
5. **Telegram goes away mid-session** (the router reboots). Screens play on alone, Join is greyed, and on reconnect the web's `open` event refreshes. Android refreshes on the next Home or player open. Nothing may crash or loop retries faster than one per 30 s.

## Phases

| # | Phase | Depends on | Status |
|---|---|---|---|
| 01 | [Probe: history read and edit push](phase-01-probe-history-read-and-edit-push.md) | none | pending |
| 02 | [Shared session logic (web reference) and fixtures](phase-02-shared-session-logic-web-reference.md) | 01 | pending |
| 03 | [Web server: channel, hub, routes, push](phase-03-web-server-channel-hub-routes-push.md) | 02 | pending |
| 04 | [Web player: follower and UI](phase-04-web-player-follower-and-ui.md) | 03 | pending |
| 05 | [Core (Rust): together I/O and push](phase-05-core-together-io-and-push.md) | 03 (fixture) | pending |
| 06 | [Android: session logic port (Kotlin)](phase-06-android-session-logic-port.md) | 02 | pending |
| 07 | [Android: channel, live state, controller](phase-07-android-channel-live-state-and-controller.md) | 05, 06 | pending |
| 08 | [Android UI: phone/tablet and TV](phase-08-android-phone-and-tv-ui.md) | 07, 04 | pending |
| 09 | [Docs, version, three-surface walk](phase-09-docs-version-and-device-walk.md) | all | pending |

## Changes from the spec, for the user to confirm

1. **The Android rules are a Kotlin port in `core/model`, not a Rust port.** The precedent is `ProfileRolesFixtureTest`. The core keeps only I/O, so a dozen pure functions don't cross UniFFI.
2. **The Android follower lives in `feature/player`, not a new `feature/together`.** `settings.gradle.kts` forbids feature-to-feature dependencies, and the follower drives `PlayerHandle`.
3. **On the web, the shared rules are plain JS in `public/lib/together/`**, so the browser and the server both import them. The spec has been updated to say so.
4. **Dismissing PiP on Android leaves the session.** Dismissing it stops this screen's playback, and the drift check would otherwise fight it. PiP itself, and going to the background, still keep you in, as the spec says.
5. **Missed pushes are re-read every 30 s**, on the web by the server's hub and on Android by the controller. The spec said "resyncs on reconnect", and nothing tells either side reliably that it reconnected.

## Review

(Filled in at close-out.)
