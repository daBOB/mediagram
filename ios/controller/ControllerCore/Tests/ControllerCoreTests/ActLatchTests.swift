import XCTest
@testable import ControllerCore

final class ActLatchTests: XCTestCase {
    func testOnlyTheFirstTapOfAScreenGoesThrough() {
        var latch = ActLatch()
        XCTAssertTrue(latch.pass(.skip))
        XCTAssertFalse(latch.pass(.skip))
        XCTAssertFalse(latch.pass(.ready))
        XCTAssertFalse(latch.pass(.pick(player: nil)))
    }

    func testDrawsAlwaysGoThroughAndSpendNothing() {
        var latch = ActLatch()
        XCTAssertTrue(latch.pass(.draw(.line(color: 0, width: 1, points: [1, 2]))))
        XCTAssertTrue(latch.pass(.draw(.undo)))
        XCTAssertTrue(latch.pass(.guessed))
        XCTAssertTrue(latch.pass(.draw(.clear)))
        XCTAssertFalse(latch.pass(.keepDrawing))
    }
}
