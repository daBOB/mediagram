import Foundation

public struct Stroke: Equatable, Sendable {
    public var color: Int
    public var width: Int
    public var points: [Int]
    public init(color: Int, width: Int, points: [Int]) { self.color = color; self.width = width; self.points = points }
}

/// The board's own copy of the drawing; follows the box's rules so both show the same ink.
public struct InkModel: Sendable {
    public private(set) var turn: Int?
    public private(set) var strokes: [Stroke] = []
    /// Moves when strokes are replaced, undone or cleared, not on an own append; the view redraws on it.
    public private(set) var epoch = 0
    /// A `more` only extends a stroke while one is open: after a `line` or `more`, not after undo or clear.
    private var open = false

    public init() {}

    public mutating func apply(_ op: DrawOp) {
        switch op {
        case let .line(color, width, points):
            strokes.append(Stroke(color: color, width: width, points: points)); open = true
        case let .more(points):
            guard open, !strokes.isEmpty else { return }
            strokes[strokes.count - 1].points += points
        case .undo:
            if !strokes.isEmpty { strokes.removeLast() }
            open = false; epoch += 1
        case .clear:
            strokes = []; open = false; epoch += 1
        }
    }

    /// Takes the box's drawing when `resync` or a new turn; keeps this board's own strokes otherwise. True when it replaced them.
    @discardableResult
    public mutating func show(turn: Int, ops: [DrawOp], resync: Bool) -> Bool {
        guard resync || turn != self.turn else { return false }
        self.turn = turn
        strokes = []; open = false
        for op in ops { apply(op) }
        epoch += 1
        return true
    }
}
