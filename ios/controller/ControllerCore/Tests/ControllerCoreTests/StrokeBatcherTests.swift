import XCTest
@testable import ControllerCore

final class StrokeBatcherTests: XCTestCase {
    func testTheFirstFlushIsALineThenMore() {
        var b = StrokeBatcher()
        b.begin(color: 1, width: 2, x: 10, y: 20)
        b.add(x: 30, y: 40)
        XCTAssertEqual(b.flush(), [.line(color: 1, width: 2, points: [10, 20, 30, 40])])
        b.add(x: 50, y: 60)
        XCTAssertEqual(b.flush(), [.more(points: [50, 60])])
    }

    func testNothingPendingFlushesNothing() {
        var b = StrokeBatcher()
        XCTAssertEqual(b.flush(), [])
        b.begin(color: 0, width: 0, x: 1, y: 1)
        _ = b.flush()
        XCTAssertEqual(b.flush(), [])
    }

    func testARepeatedPointIsDropped() {
        var b = StrokeBatcher()
        b.begin(color: 0, width: 0, x: 1, y: 1)
        b.add(x: 1, y: 1)
        _ = b.flush()
        b.add(x: 1, y: 1)
        XCTAssertEqual(b.flush(), [])
        b.add(x: 2, y: 1)
        XCTAssertEqual(b.flush(), [.more(points: [2, 1])])
    }

    func testATapIsADot() {
        var b = StrokeBatcher()
        b.begin(color: 3, width: 1, x: 7, y: 9)
        XCTAssertEqual(b.end(), [.line(color: 3, width: 1, points: [7, 9])])
    }

    func testLongBatchesSplitAt128Points() {
        var b = StrokeBatcher()
        b.begin(color: 0, width: 1, x: 0, y: 0)
        for i in 1..<300 { b.add(x: i, y: 0) }
        let ops = b.end()
        XCTAssertEqual(ops.count, 3)
        guard case let .line(_, _, p0) = ops[0], case let .more(p1) = ops[1], case let .more(p2) = ops[2] else {
            return XCTFail("wrong shapes \(ops)")
        }
        XCTAssertEqual([p0.count, p1.count, p2.count], [256, 256, 88])
    }

    func testAfterEndPointsAreIgnored() {
        var b = StrokeBatcher()
        b.begin(color: 0, width: 0, x: 1, y: 1)
        _ = b.end()
        b.add(x: 5, y: 5)
        XCTAssertEqual(b.flush(), [])
        XCTAssertEqual(b.end(), [])
    }
}
