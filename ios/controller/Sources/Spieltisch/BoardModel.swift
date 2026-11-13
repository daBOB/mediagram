import ControllerCore
import Combine

/// The native drawing board the page drives: the screen it asked for, the drawing, and the acts going back.
@MainActor final class BoardModel: ObservableObject {
    @Published private(set) var screen: NativeScreen?
    @Published private(set) var ink = InkModel()
    /// Bumped when the ink was replaced or changed by undo or clear (`ink.epoch`); not on an own stroke.
    @Published private(set) var inkEpoch = 0
    /// Set by GameWebView's coordinator: posts one act to the page.
    var send: (BoardAct) -> Void = { _ in }
    private var latch = ActLatch()

    func show(_ show: NativeBoardShow) {
        screen = show.screen
        ink.show(turn: show.screen.turn, ops: show.ops, resync: show.resync)
        inkEpoch = ink.epoch
        latch = ActLatch()
    }

    func hide() {
        screen = nil
    }

    func act(_ act: BoardAct) {
        guard latch.pass(act) else { return }
        send(act)
        if case let .draw(op) = act {
            ink.apply(op)
            inkEpoch = ink.epoch
        }
    }
}
