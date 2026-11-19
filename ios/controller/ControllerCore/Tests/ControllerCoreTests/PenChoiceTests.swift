import XCTest
@testable import ControllerCore

final class PenChoiceTests: XCTestCase {
    func testTheChoiceSurvivesTheSameTurnShownAgain() {
        var pen = PenChoice()
        pen.show(turn: 3)
        pen.color = 2; pen.width = 0
        pen.show(turn: 3)
        XCTAssertEqual(pen.color, 2)
        XCTAssertEqual(pen.width, 0)
    }

    func testANewTurnStartsWithBlackAndMedium() {
        var pen = PenChoice()
        pen.show(turn: 3)
        pen.color = 2; pen.width = 0
        pen.show(turn: 4)
        XCTAssertEqual(pen.color, 0)
        XCTAssertEqual(pen.width, 1)
    }
}
