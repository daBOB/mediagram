import XCTest
@testable import ControllerCore

final class PaperStyleTests: XCTestCase {
    func testQuantizeClampsToThePaper() {
        let a = PaperStyle.quantize(x: -5, y: 50, width: 1000, height: 562.5)
        XCTAssertEqual(a.0, 0); XCTAssertEqual(a.1, 889)
        let b = PaperStyle.quantize(x: 1000, y: 0, width: 1000, height: 562.5)
        XCTAssertEqual(b.0, 10000); XCTAssertEqual(b.1, 0)
        let c = PaperStyle.quantize(x: 500, y: 281.25, width: 1000, height: 562.5)
        XCTAssertEqual(c.0, 5000); XCTAssertEqual(c.1, 5000)
    }

    func testTheEraserIsThreeTimesWider() {
        XCTAssertEqual(PaperStyle.lineWidth(color: 5, width: 1, paperWidth: 1000), 30, accuracy: 1e-9)
        XCTAssertEqual(PaperStyle.lineWidth(color: 0, width: 1, paperWidth: 1000), 10, accuracy: 1e-9)
    }

    func testAnOutOfRangeWidthIsClamped() {
        XCTAssertEqual(PaperStyle.lineWidth(color: 0, width: 9, paperWidth: 1000), 17, accuracy: 1e-9)
        XCTAssertEqual(PaperStyle.lineWidth(color: 0, width: -1, paperWidth: 1000), 5, accuracy: 1e-9)
    }
}
