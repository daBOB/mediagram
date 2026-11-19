import XCTest
@testable import ControllerCore

final class InkModelTests: XCTestCase {
    func testLineThenMoreIsOneStroke() {
        var m = InkModel()
        m.apply(.line(color: 1, width: 2, points: [1, 2]))
        m.apply(.more(points: [3, 4]))
        XCTAssertEqual(m.strokes, [Stroke(color: 1, width: 2, points: [1, 2, 3, 4])])
    }

    func testUndoDropsTheLastStroke() {
        var m = InkModel()
        m.apply(.line(color: 0, width: 0, points: [1, 1]))
        m.apply(.line(color: 1, width: 0, points: [2, 2]))
        m.apply(.undo)
        XCTAssertEqual(m.strokes, [Stroke(color: 0, width: 0, points: [1, 1])])
    }

    func testClearEmpties() {
        var m = InkModel()
        m.apply(.line(color: 0, width: 0, points: [1, 1]))
        m.apply(.clear)
        XCTAssertEqual(m.strokes, [])
    }

    func testMoreAfterUndoDoesNothing() {
        var m = InkModel()
        m.apply(.line(color: 0, width: 0, points: [1, 1]))
        m.apply(.line(color: 1, width: 0, points: [2, 2]))
        m.apply(.undo)
        m.apply(.more(points: [9, 9]))
        XCTAssertEqual(m.strokes, [Stroke(color: 0, width: 0, points: [1, 1])])
        var empty = InkModel()
        empty.apply(.more(points: [9, 9]))
        XCTAssertEqual(empty.strokes, [])
    }

    func testANewTurnTakesTheBoxDrawing() {
        var m = InkModel()
        XCTAssertTrue(m.show(turn: 1, ops: [.line(color: 2, width: 1, points: [5, 5]), .more(points: [6, 6])], resync: false))
        XCTAssertEqual(m.turn, 1)
        XCTAssertEqual(m.strokes, [Stroke(color: 2, width: 1, points: [5, 5, 6, 6])])
    }

    func testTheSameTurnKeepsWhatThisBoardDrew() {
        var m = InkModel()
        m.show(turn: 1, ops: [], resync: false)
        m.apply(.line(color: 0, width: 0, points: [1, 1]))
        XCTAssertFalse(m.show(turn: 1, ops: [.line(color: 4, width: 2, points: [8, 8])], resync: false))
        XCTAssertEqual(m.strokes, [Stroke(color: 0, width: 0, points: [1, 1])])
    }

    func testResyncTakesTheBoxDrawing() {
        var m = InkModel()
        m.show(turn: 1, ops: [], resync: false)
        m.apply(.line(color: 0, width: 0, points: [1, 1]))
        XCTAssertTrue(m.show(turn: 1, ops: [.line(color: 4, width: 2, points: [8, 8])], resync: true))
        XCTAssertEqual(m.strokes, [Stroke(color: 4, width: 2, points: [8, 8])])
        m.apply(.more(points: [9, 9])) // a resynced stroke may still be continued by the box's own ops
        XCTAssertEqual(m.strokes.count, 1)
    }

    func testTheEpochMovesOnReplaceUndoAndClearButNotOnOwnStrokes() {
        var m = InkModel()
        let e0 = m.epoch
        m.show(turn: 1, ops: [], resync: false)
        let e1 = m.epoch
        XCTAssertGreaterThan(e1, e0)
        m.apply(.line(color: 0, width: 0, points: [1, 1]))
        m.apply(.more(points: [2, 2]))
        XCTAssertEqual(m.epoch, e1)
        m.apply(.undo)
        XCTAssertGreaterThan(m.epoch, e1)
        let e2 = m.epoch
        m.apply(.clear)
        XCTAssertGreaterThan(m.epoch, e2)
    }
}
