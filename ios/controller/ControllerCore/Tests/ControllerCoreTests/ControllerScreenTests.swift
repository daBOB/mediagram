import ControllerCore
import XCTest

final class ControllerScreenTests: XCTestCase {
    let a = "http://192.168.0.37:8080/"
    let b = "http://192.168.0.41:8080/"

    func testStartsFromTheRememberedBoxOrSearches() {
        XCTAssertEqual(startScreen(remembered: nil), .searching(notice: nil))
        XCTAssertEqual(startScreen(remembered: a), .playing(url: a))
    }

    func testFoundBoxIsJoined() {
        XCTAssertEqual(ControllerScreen.searching(notice: nil).after(.found(a)), .playing(url: a))
        XCTAssertEqual(ControllerScreen.playing(url: a).after(.found(a)), .playing(url: a))
    }

    func testBoxMovedSinceLastNight() {
        XCTAssertEqual(ControllerScreen.playing(url: a).after(.found(b)), .playing(url: b))
    }

    func testTvRestartedGameNight() {
        let lost = ControllerScreen.playing(url: a).after(.pageFailed(a))
        XCTAssertEqual(lost, .searching(notice: nil))
        XCTAssertEqual(lost.after(.found(a)), .playing(url: a))
    }

    func testAFailureOfAnEarlierBoxIsIgnored() {
        XCTAssertEqual(ControllerScreen.playing(url: b).after(.pageFailed(a)), .playing(url: b))
    }

    func testForeignQrCode() {
        XCTAssertEqual(
            ControllerScreen.searching(notice: nil).after(.scanned("https://example.com")),
            .searching(notice: "Das ist nicht der Code vom Fernseher.")
        )
        XCTAssertEqual(ControllerScreen.searching(notice: nil).after(.scanned(a)), .playing(url: a))
    }
}
