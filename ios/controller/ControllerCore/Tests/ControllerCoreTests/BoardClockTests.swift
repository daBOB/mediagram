import XCTest
@testable import ControllerCore

final class BoardClockTests: XCTestCase {
    func testARunningClockCountsDown() {
        let c = NativeClock(endsAtEpochMs: 100_000, leftMs: 0, totalMs: 90_000)
        XCTAssertEqual(BoardClock.leftMs(c, nowEpochMs: 40_000), 60_000)
    }

    func testAPausedClockStands() {
        let c = NativeClock(endsAtEpochMs: nil, leftMs: 33_000, totalMs: 90_000)
        XCTAssertEqual(BoardClock.leftMs(c, nowEpochMs: 1_000_000), 33_000)
    }

    func testItNeverGoesNegative() {
        let c = NativeClock(endsAtEpochMs: 100_000, leftMs: 0, totalMs: 90_000)
        XCTAssertEqual(BoardClock.leftMs(c, nowEpochMs: 150_000), 0)
    }

    func testLabels() {
        XCTAssertEqual(BoardClock.label(leftMs: 90_000), "1:30")
        XCTAssertEqual(BoardClock.label(leftMs: 4_001), "0:05")
        XCTAssertEqual(BoardClock.label(leftMs: 0), "0:00")
        XCTAssertEqual(BoardClock.label(leftMs: -5), "0:00")
    }
}
