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

    /// The same values as client/src/ink/ink-style.ts, which pins them on its side.
    func testThePaperConstantsMatchThePage() {
        XCTAssertEqual(PaperStyle.colors, ["#1b1b1b", "#d8342b", "#2c6bed", "#2f9e44", "#f2a20e", "#fbf8f1"])
        XCTAssertEqual(PaperStyle.widths, [0.005, 0.01, 0.017])
        XCTAssertEqual(PaperStyle.eraserIndex, 5)
        XCTAssertEqual(PaperStyle.eraserScale, 3)
    }
}
