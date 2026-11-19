import Foundation

/// Collects a stroke's points and hands them out as draw ops: a `line` first, then `more`.
public struct StrokeBatcher: Sendable {
    private let maxPoints: Int
    private var color = 0, width = 0
    private var pending: [Int] = []
    private var last: (Int, Int)?
    private var lineSent = false
    private var active = false

    public init(maxPoints: Int = 128) { self.maxPoints = max(1, maxPoints) }

    public mutating func begin(color: Int, width: Int, x: Int, y: Int) {
        self.color = color; self.width = width
        pending = [x, y]; last = (x, y); lineSent = false; active = true
    }

    public mutating func add(x: Int, y: Int) {
        guard active, last.map({ $0 != (x, y) }) ?? true else { return }
        pending += [x, y]; last = (x, y)
    }

    /// Pending points as ops, split at `maxPoints` points each.
    public mutating func flush() -> [DrawOp] {
        var ops: [DrawOp] = []
        var i = 0
        while i < pending.count {
            let chunk = Array(pending[i..<min(i + maxPoints * 2, pending.count)])
            ops.append(lineSent ? .more(points: chunk) : .line(color: color, width: width, points: chunk))
            lineSent = true
            i += maxPoints * 2
        }
        pending = []
        return ops
    }

    /// Flushes and closes the stroke (finger lifted or touch cancelled).
    public mutating func end() -> [DrawOp] {
        let ops = flush()
        active = false; last = nil
        return ops
    }
}
