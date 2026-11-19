/// The drawer's finger on the paper, in ink coordinates: one line at a time. The first finger down
/// draws; any finger that touches while it draws is ignored until that finger lifts itself.
public struct Pen<Finger: Equatable> {
    /// The line in progress as this board drew it since its last `begin`; nil while no finger draws.
    public private(set) var stroke: Stroke?
    private var finger: Finger?
    private var batcher = StrokeBatcher()

    public init() {}

    public func draws(_ finger: Finger) -> Bool { self.finger == finger }

    /// Starts a line, unless another finger is drawing; false when this finger is ignored.
    @discardableResult
    public mutating func down(_ finger: Finger, color: Int, width: Int, x: Int, y: Int) -> Bool {
        guard self.finger == nil else { return false }
        self.finger = finger
        stroke = Stroke(color: color, width: width, points: [])
        move(finger, x: x, y: y)
        return true
    }

    public mutating func move(_ finger: Finger, x: Int, y: Int) {
        guard draws(finger), var line = stroke, line.points.suffix(2) != [x, y] else { return }
        if line.points.isEmpty {
            batcher.begin(color: line.color, width: line.width, x: x, y: y)
        } else {
            batcher.add(x: x, y: y)
        }
        line.points += [x, y]
        stroke = line
    }

    public mutating func flush() -> [DrawOp] { batcher.flush() }

    /// The finger lifted, or its touch was cancelled: the line's last ops. Nil for a finger that was not drawing.
    public mutating func up(_ finger: Finger) -> [DrawOp]? {
        guard draws(finger) else { return nil }
        self.finger = nil
        stroke = nil
        return batcher.end()
    }

    /// The board's ink was replaced, undone or cleared under the finger. Points not yet sent are dropped:
    /// as a `more` they would join the box's last stroke. The finger's next point begins a new line.
    public mutating func inkReplaced() {
        _ = batcher.end()
        stroke?.points = []
    }
}

extension Pen: Sendable where Finger: Sendable {}
