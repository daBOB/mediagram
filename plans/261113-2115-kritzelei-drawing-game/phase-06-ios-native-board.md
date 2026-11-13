# Phase 06 — iOS native board

**Priority:** after phases 04 and 05. **Status:** todo. **Spec:** my calls 2, 4, 5, 11. Layout A of the look (`docs/superpowers/specs/2026-11-13-kritzelei-look.html`, `data-choice="tools-bottom"`).

## Overview

The app answers the page's `showBoard`/`hideBoard` with native screens laid over the web view: the hand-over, the paper with the tool row, and the who-got-it list. It sends acts and strokes back through `window.mediagramBoard.receive`. iPads are locked to landscape. No Swift networking.

## Key insights

- The bridge script is built in `bridgeScript(memory:)` (`ios/controller/Sources/Spieltisch/GameWebView.swift`). Messages come in through `userContentController(_:didReceive:)`, and there is no app→page path yet.
- `ContentView` already hides the status bar and defers system edge gestures. The board overlay goes on top of `GameWebView` there.
- Coalesced touches give every 60 Hz sample. Predicted touches are drawn locally only and never batched.
- One line at a time: the first touch on the paper draws, and every other touch is ignored until it lifts. A cancelled touch (incoming call, Control Center) ends the stroke like a lift.
- If the page reloads, the overlay must not stay up on stale state. Hide it when a navigation starts. The page shows it again with `resync`.

## Task 1: The bridge both ways

**Files:**
- Create: `ios/controller/Sources/Spieltisch/BoardModel.swift`
- Modify: `ios/controller/Sources/Spieltisch/GameWebView.swift`, `ios/controller/Sources/Spieltisch/ContentView.swift`

**Interfaces:**
```swift
@MainActor final class BoardModel: ObservableObject {
    @Published private(set) var screen: NativeScreen?
    @Published private(set) var ink = InkModel()
    /// Set by GameWebView's coordinator: posts one act to the page.
    var send: (BoardAct) -> Void = { _ in }
    func show(_ show: NativeBoardShow)     // screen = show.screen; ink.show(turn:ops:resync:)
    func hide()
    func act(_ act: BoardAct)              // send(act); for .draw(op) also ink.apply(op)
}
```

- `bridgeScript` gains:
  - `board: <true on UIDevice.current.userInterfaceIdiom == .pad>`
  - `showBoard(j) { post({ showBoard: j }) }`
  - `hideBoard() { post({ hideBoard: true }) }`
- The coordinator:
  - decodes `showBoard` with `JSONDecoder` into `NativeBoardShow` → `board.show`
  - `hideBoard` → `board.hide()`
  - `send` encodes the act, escapes it as a JS string literal through `JSONSerialization` of a one-element array, and runs `webView.evaluateJavaScript("window.mediagramBoard && window.mediagramBoard.receive(<json string>)")`
  - `webView(_:didStartProvisionalNavigation:)` → `board.hide()`
- `ContentView`: `@StateObject private var board = BoardModel()`, passed into `GameWebView(url:board:onFailed:)`, and `.overlay { if board.screen != nil { BoardOverlay(model: board) } }`.

- [ ] **Step 1:** Implement. `cd ios/controller && xtool dev build` → builds. (UIKit code has no Linux tests. The decisions it calls are tested in ControllerCore.)
- [ ] **Step 2: Commit** — `feat(ios): the page can show and hide a native drawing board`.

## Task 2: The paper (DrawingView)

**Files:** Create `ios/controller/Sources/Spieltisch/DrawingView.swift`.

**Interfaces:**
```swift
/// The paper: draws with one finger at a time, batching points every 50 ms.
final class DrawingView: UIView {
    var color = 0, width = 1          // tool row state
    var onOps: ([DrawOp]) -> Void = { _ in }
    func redraw(_ strokes: [Stroke])  // after undo, clear, resync or a new turn
}
struct PaperView: UIViewRepresentable { @ObservedObject var model: BoardModel; let color: Int; let width: Int }
```

- Committed strokes are painted into a cached `UIImage` (`UIGraphicsImageRenderer`). The stroke in progress is a path on top, and predicted points are a thin tail redrawn every frame.
- Points go through `PaperStyle.quantize` against `bounds` into `StrokeBatcher`:
  - `touchesBegan` → `begin`
  - coalesced `touchesMoved` → `add`
  - `touchesEnded` and `touchesCancelled` → `end`
- A 50 ms `Timer` flushes the batcher, sending each op via `onOps` → `model.act(.draw(op))`.
- Line widths use `PaperStyle.lineWidth` with round caps and joins. The paper colour is `#fbf8f1`, and the eraser paints in it.
- `PaperView` keeps the paper at 16:9 inside its frame (`.aspectRatio(16/9, contentMode: .fit)`) and calls `redraw(model.ink.strokes)` when the ink was replaced or changed by undo or clear.

- [ ] **Step 1:** Implement. `xtool dev build` → builds.
- [ ] **Step 2: Commit** — `feat(ios): a finger-drawing paper that streams its strokes`.

## Task 3: The board screens, layout A

**Files:** Create in `ios/controller/Sources/Spieltisch/`:
- `BoardOverlay.swift` (a switch on `model.screen`)
- `BoardHandoverView.swift`
- `BoardDrawingView.swift` (top bar, paper, tool row)
- `BoardPickingView.swift`
- `BoardFonts.swift`
- `Resources/Fonts/*.ttf`

Also modify `ios/controller/Package.swift` (target `resources: [.process("Resources")]`) and `ios/controller/Info.plist`.

Screens, matched to the look's tokens: felt `#0f5a43`, deep `#0a4433`, line `#1c7258`, card `#fbf8f1`, ink `#0c2a22`, yellow key `#f6c731` with edge `#b98f0d`.

- **Handover:** the drawer's avatar initial and colour, „{Name} ist dran“, a yellow „Ich bin dran“ → `.ready`, and a small quiet „Überspringen“ → `.skip`. No word.
- **Drawing:**
  - Top bar: the word on a card, plus the clock ring and `BoardClock.label`, refreshed by `TimelineView(.periodic(from: .now, by: 0.25))`.
  - The paper.
  - The tool row under the paper, in this order: 5 colour swatches, a separator, 3 widths, the eraser (colour 5), undo → `.draw(.undo)`, clear → `.draw(.clear)`, a spacer, „Niemand“ (quiet) → `.pick(player: nil)`, „Erraten!“ (yellow) → `.guessed`.
- **Picking:** the word, then one large button per guesser (avatar initial in their colour, name) → `.pick(player: id)`. Then „Niemand“ → `.pick(player: nil)`. Then „Doch nicht – weiterzeichnen“ → `.keepDrawing`, only when `canGoBack`.
- **Fonts:** Bricolage Grotesque for display and Atkinson Hyperlegible for text, as TTFs from `github.com/google/fonts` (OFL; keep `OFL.txt` beside them). `BoardFonts.register()` at app start uses `CTFontManagerRegisterFontsForURL` over `Bundle.module` URLs, and falls back to the system font if registration fails.
- **Info.plist:**
  - `UISupportedInterfaceOrientations~ipad` = landscape left and right
  - `UIRequiresFullScreen` = true

  iPhones keep their orientations.

- [ ] **Step 1:** Implement. `xtool dev build` → builds.
- [ ] **Step 2: Commit** — `feat(ios): the drawing board's hand-over, paper and who-got-it screens`.

## Task 4: On the iPad

- [ ] **Step 1:** Run `scripts/refresh-ios-controllers.sh` with only the iPad plugged in. In the feature worktree: `bun run build`, `game-night-dev`, `avahi-publish` as in phase 01, the TV at `http://localhost:8080/tv`, two phones in browsers.
- [ ] **Step 2:** Walk one game. Expected:
  - The iPad lobby offers „Als Zeichenbrett verwenden“ only with Kritzelei picked.
  - The hand-over hides the word.
  - Lines appear on the TV while the finger moves.
  - A resting second finger draws nothing.
  - Undo and clear match on the TV.
  - „Erraten!“ stops the clock on iPad and TV, and „Doch nicht“ resumes it.
  - At 0 the list comes up without „Doch nicht“.
- [ ] **Step 3:** Mid-turn: lock the iPad for 20 s, then unlock. Expected: the clock stood still while it was away, and the drawing is back on the iPad and still on the TV. Kill the app and reopen it. Expected: the same.
- [ ] **Step 4:** Record what you saw (and screenshots, if the user takes them) in `plans/261113-2115-kritzelei-drawing-game/reports/ipad-walk-report.md`. Fix what is wrong, with a commit each.

## Success criteria

The app builds with xtool and installs on the iPad. A whole game is drawn on the iPad with a finger, and lock or kill mid-turn recovers.

## Risks

- `Bundle.module` resources with xtool: if fonts don't load, the fallback keeps the system font. Note it rather than stall.
- A landscape-only iPad also turns the phone page sideways on that iPad. That's accepted, because the iPad is the board.
