# Kritzelei, the drawing game with a native board on the iPad — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A party drawing game where the drawer draws on the iPad with a finger, the TV shows the line live, the room guesses out loud and the drawer taps who got it.

**Architecture:** The box gains a third role, the board, held by the iPad's phone page. The page drives a native SwiftUI/UIKit board over the `window.mediagramApp` bridge and forwards its strokes to the box on its one WebSocket. The box keeps each turn's drawing as an append-only list of ops and sends TV and board only what was added (`ServerMsg::Draw`), outside the snapshot views.

**Tech Stack:** Rust (mediagram-game, ts-rs), React/TS (game client, bun test), Swift 6 toolchain in Swift 5 mode (xtool, ControllerCore with XCTest on Linux).

**Spec:** `docs/superpowers/specs/2026-11-13-kritzelei-drawing-game-design.md`, look: `docs/superpowers/specs/2026-11-13-kritzelei-look.html` (layout A).

## Global Constraints

- Build in a worktree `../mediagram-kritzelei` on `feat/kritzelei`, cut from `main`, merged back to `main`.
- The drawing iPad: iPad (5th gen) `iPad6,11`, iOS 16.7.12, udid `82f1e9524ba059e46e46f8f458e6e3530ed3ae3d`. Finger only: no Pencil, no pressure.
- iOS app stays `.iOS(.v16)`, Swift 5 language mode. UIKit/WebKit only in `Sources/Spieltisch`, pure logic in `ControllerCore` (runs `swift test` on Linux).
- Paper 16:9. Points are x,y pairs, each `0..=10000` of paper width/height. Colours `#1b1b1b #d8342b #2c6bed #2f9e44 #f2a20e`, index 5 = eraser in paper colour `#fbf8f1`. Widths as a fraction of paper width `[0.005, 0.01, 0.017]`, the eraser 3× wider.
- Clock 90 s, reveal 6 s, guesser +2, drawer +1, two rounds below 5 players, minimum 2 players.
- The word reaches only the board until the reveal. Never in a TV scene or phone screen before it.
- German copy, verbatim: „Als Zeichenbrett verwenden“, „Doch mitspielen“, „Kritzelei braucht das iPad als Zeichenbrett.“, „Du zeichnest – hol dir das iPad“, „Rate laut!“, „Anna ist dran“ (name), „Ich bin dran“, „Überspringen“, „Erraten!“, „Doch nicht – weiterzeichnen“, „Niemand“, „Ruft laut, was ihr seht!“, „Richtig!“.
- Never run `cargo fmt` on `crates/mediagram-game` (not kept in rustfmt style). Files under ~200 lines, Preisfrage's file pattern.
- No plan or phase references in code, comments or test names.
- ts-rs writes `client/src/protocol/*.ts` during `cargo test`; commit the regenerated files with the Rust change.
- The server embeds `client/dist`: `bun run build` in the client before running `game-night-dev`.
- Version: Spieltisch minor bump, by pattern (another session commits to main), in every Spieltisch manifest and `ios/controller/Info.plist` (`CFBundleVersion` = major·1,000,000 + minor·1,000 + patch).

## Review Focus

1. A TV page that reloads or connects mid-turn shows the whole drawing, not half of it → room test in phase 02.
2. The iPad's page reconnects or the app restarts mid-turn: the board gets the drawing and the turn back, and board and TV agree → `resync` in phases 04–06, tests there.
3. A second „Erraten!“ pick, or one arriving after the reveal, never scores twice → rules test in phase 03.
4. Garbage or flooding draw ops (odd lengths, values over 10000, huge batches, a turn over the cap) are dropped without a panic → rules tests in phase 03.
5. A second game right after the first: the TV's draw cursor from the old game must not slice past the new drawing → room test in phase 02.

## Phases

| # | Phase | Status |
|---|-------|--------|
| 01 | [Latency spike on the real iPad (throwaway, gate)](phase-01-latency-spike.md) | todo |
| 02 | [The board role in the room](phase-02-board-role-in-the-room.md) | todo |
| 03 | [Kritzelei rules, words and views](phase-03-kritzelei-rules-and-views.md) | todo |
| 04 | [The page and the TV](phase-04-page-and-tv.md) | todo |
| 05 | [iOS board model in ControllerCore](phase-05-ios-board-model.md) | todo |
| 06 | [iOS native board](phase-06-ios-native-board.md) | todo |
| 07 | [Version, device walk, merge](phase-07-version-walk-merge.md) | todo |

Phase 01 is a gate: if finger→TV p95 is over 200 ms, stop and bring the numbers to the user before phase 02.
Phases 02→03→04 are sequential (protocol types flow on). Phase 05 can run any time after 02. Phase 06 needs 04 and 05.

## Decisions taken here (builder's calls in the spec)

- Wire: `Action::Board { act: BoardAct }`, `ServerMsg::Board { screen }`, `ServerMsg::Draw { turn, from, ops }`; exact JSON in phase 02.
- Ops: `line` starts a stroke, `more` extends it, `undo`, `clear`. The board sends a batch every 50 ms, at most 128 points per op.
- Buzz: `PhoneView.buzz` (the game asks for it); the old screen rules stay for every other game.
- The game keeps no log lines (`take_log` stays empty).
- Fonts: Bricolage Grotesque and Atkinson Hyperlegible TTFs bundled in the app, registered at launch.
