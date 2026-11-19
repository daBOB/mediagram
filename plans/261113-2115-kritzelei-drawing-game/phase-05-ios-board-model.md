# Phase 05 — iOS board model in ControllerCore

**Priority:** any time after phase 02. **Status:** todo. **Spec:** my call 11 (the pure parts go in `ControllerCore` with tests).

## Overview

The board's decisions live in Swift with no UIKit, so `swift test` runs them on Linux:
- the wire types it shares with the page
- paper geometry and style
- batching a stroke into ops
- the local ink model
- the clock label

Phase 06 only wires these to views.

## Key insights

- `ios/controller/ControllerCore` already runs XCTest on Linux: `swift test` → "Executed 9 tests, with 0 failures".
- Swift's `JSONEncoder` orders keys differently from serde. Compare JSON as parsed objects (`JSONSerialization`), not as strings.
- The page sends snake_case keys (`ends_at_epoch_ms`, `left_ms`, `total_ms`, `can_go_back`). Use explicit `CodingKeys`, not a global key strategy, so the wire spelling stays visible.
- `ControllerCore` compiles in Swift 6 mode (its `Package.swift` is tools 6.0). New types are value types and `Sendable` where they cross actors.
- Paper style values must equal `client/src/ink/ink-style.ts` (phase 04), because the TV and the iPad draw the same strokes.

## Task 1: Wire types and paper style

**Files** (under `ios/controller/ControllerCore/`):
- Create: `Sources/ControllerCore/BoardWire.swift`, `Sources/ControllerCore/PaperStyle.swift`
- Test: `Tests/ControllerCoreTests/BoardWireTests.swift`, `Tests/ControllerCoreTests/PaperStyleTests.swift`

**Interfaces:**
```swift
public enum DrawOp: Codable, Equatable { case line(color: Int, width: Int, points: [Int]), more(points: [Int]), undo, clear }   // "t": line | more | undo | clear
public enum BoardAct: Encodable, Equatable { case ready, skip, guessed, keepDrawing, pick(player: Int?), draw(DrawOp) }        // "t": … | keep_drawing | pick | draw ("op")
public struct BoardPlayer: Codable, Equatable { public let id: Int; public let name: String; public let color: String; public let avatar: String? }
public struct NativeClock: Codable, Equatable { public let endsAtEpochMs: Double?; public let leftMs: Double; public let totalMs: Double }
public enum NativeScreen: Decodable, Equatable {                                                                                // "kind"
    case handover(turn: Int, drawer: BoardPlayer)
    case drawing(turn: Int, drawer: BoardPlayer, word: String, clock: NativeClock)
    case picking(turn: Int, drawer: BoardPlayer, word: String, clock: NativeClock, guessers: [BoardPlayer], canGoBack: Bool)
    public var turn: Int { get }
}
public struct NativeBoardShow: Decodable, Equatable { public let screen: NativeScreen; public let ops: [DrawOp]; public let resync: Bool }

// PaperStyle.swift — the same values as client/src/ink/ink-style.ts
public enum PaperStyle {
    public static let aspect = 16.0 / 9.0
    public static let inkMax = 10000
    public static let colors = ["#1b1b1b", "#d8342b", "#2c6bed", "#2f9e44", "#f2a20e", "#fbf8f1"]   // 5 = eraser
    public static let widths = [0.005, 0.01, 0.017]
    public static let eraserScale = 3.0
    public static func lineWidth(color: Int, width: Int, paperWidth: Double) -> Double
    /// A point on a paper of `width`×`height` as 0...inkMax each, clamped to the paper.
    public static func quantize(x: Double, y: Double, width: Double, height: Double) -> (Int, Int)
}
```

- [ ] **Step 1: Failing tests:**
  - `testActsEncodeAsTheBoxReadsThem`: each `BoardAct`, parsed with `JSONSerialization`, equals the parsed form of the phase 02 strings: `{"t":"ready"}`, `{"t":"keep_drawing"}`, `{"t":"pick","player":3}`, `{"t":"pick","player":null}`, `{"t":"draw","op":{"t":"line","color":0,"width":1,"points":[10,20,30,40]}}`, `{"t":"draw","op":{"t":"undo"}}`.
  - `testAPickOfNobodySendsNull`: the key `player` is present with `NSNull`.
  - `testTheShowDecodesFromThePage`: one JSON per screen kind (drawing and picking carry `drawer`), with snake_case clock keys and `ends_at_epoch_ms: null`, plus ops of all four kinds and `resync: true` → the expected values.
  - `testQuantizeClampsToThePaper`: (−5, 50) on 1000×562.5 → (0, 889); (1000, 0) → (10000, 0); (500, 281.25) → (5000, 5000).
  - `testTheEraserIsThreeTimesWider`: `lineWidth(color: 5, width: 1, paperWidth: 1000) == 30`, and colour 0 → 10.
- [ ] **Step 2:** `cd ios/controller/ControllerCore && swift test` → FAIL (compile).
- [ ] **Step 3:** Implement with explicit `CodingKeys` and custom `encode`/`init(from:)` for the tagged enums.
- [ ] **Step 4:** `swift test` → PASS. **Step 5: Commit** — `feat(ios): the drawing board's wire types and paper`.

## Task 2: Stroke batching, ink model and clock

**Files:**
- Create: `Sources/ControllerCore/StrokeBatcher.swift`, `Sources/ControllerCore/InkModel.swift`, `Sources/ControllerCore/BoardClock.swift`
- Test: `Tests/ControllerCoreTests/StrokeBatcherTests.swift`, `InkModelTests.swift`, `BoardClockTests.swift`

**Interfaces:**
```swift
public struct StrokeBatcher {
    public init(maxPoints: Int = 128)
    public mutating func begin(color: Int, width: Int, x: Int, y: Int)  // a stroke starts at its first point
    public mutating func add(x: Int, y: Int)                            // drops a repeat of the last point; ignored when no stroke
    /// Pending points as ops: the first after `begin` is a line, later ones more, split at `maxPoints` points.
    public mutating func flush() -> [DrawOp]
    /// Flushes and closes the stroke (finger lifted or touch cancelled).
    public mutating func end() -> [DrawOp]
}
public struct Stroke: Equatable { public var color: Int; public var width: Int; public var points: [Int] }
public struct InkModel {
    public private(set) var turn: Int?
    public private(set) var strokes: [Stroke]
    public init()
    public mutating func apply(_ op: DrawOp)
    /// Takes the box's drawing when `resync` or a new turn; keeps this board's own strokes otherwise. True when it replaced them.
    /// Bumps `epoch` whenever strokes are replaced, undone or cleared, but not on an own append.
    public private(set) var epoch: Int
    @discardableResult public mutating func show(turn: Int, ops: [DrawOp], resync: Bool) -> Bool
}
public enum BoardClock {
    public static func leftMs(_ clock: NativeClock, nowEpochMs: Double) -> Double  // running: max(0, ends − now); paused: leftMs
    public static func label(leftMs: Double) -> String                            // whole seconds rounded up, "m:ss"
}
```

- [ ] **Step 1: Failing tests:**
  - Batcher:
    - `testTheFirstFlushIsALineThenMore`
    - `testNothingPendingFlushesNothing`
    - `testARepeatedPointIsDropped`
    - `testATapIsADot`: begin then end → one `line` with 2 values.
    - `testLongBatchesSplitAt128Points`: 300 points → line(128), more(128), more(44).
    - `testAfterEndPointsAreIgnored`
  - Ink model:
    - `testLineThenMoreIsOneStroke`
    - `testUndoDropsTheLastStroke`
    - `testClearEmpties`
    - `testMoreAfterUndoDoesNothing`
    - `testANewTurnTakesTheBoxDrawing`
    - `testTheSameTurnKeepsWhatThisBoardDrew` (show with other ops, resync false → strokes unchanged, returns false)
    - `testResyncTakesTheBoxDrawing`
    - `testTheEpochMovesOnReplaceUndoAndClearButNotOnOwnStrokes`
  - Clock:
    - `testARunningClockCountsDown`: ends 100_000, now 40_000 → 60_000.
    - `testAPausedClockStands`
    - `testItNeverGoesNegative`
    - `testLabels`: 90_000 → "1:30", 4_001 → "0:05", 0 → "0:00".
- [ ] **Step 2–4:** FAIL → implement → `swift test` PASS.
- [ ] **Step 5: Commit** — `feat(ios): stroke batching, ink model and clock for the drawing board`.

## Success criteria

`swift test` in `ControllerCore` is green, and the existing 9 tests still pass. The JSON matches the box's shapes, compared as parsed objects.
