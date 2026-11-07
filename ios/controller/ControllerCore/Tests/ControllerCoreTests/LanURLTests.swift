import ControllerCore
import XCTest

final class LanURLTests: XCTestCase {
    func testAcceptsPlainHttpOnThePrivateLan() {
        for url in [
            "http://192.168.0.37:8080/",
            "http://10.0.0.5:8080/",
            "http://172.20.1.2:8080/x.html",
            "http://spieltisch.local:8080/",
        ] {
            XCTAssertEqual(lanGameURL(url), url, url)
        }
    }

    func testRejectsAnythingThatIsNotTheBox() {
        for raw in [
            "https://192.168.0.37/",
            "http://8.8.8.8:8080/",
            "http://172.32.0.1/",
            "http://192.168.0.37.evil.com/",
            "javascript:alert(1)",
            "",
            "http://example.com/",
            "WIFI:S:home;T:WPA;P:x;;",
        ] {
            XCTAssertNil(lanGameURL(raw), raw)
        }
    }

    func testSameBoxIsSameSchemeHostAndPort() {
        XCTAssertTrue(sameBox("http://192.168.0.37:8080/", "http://192.168.0.37:8080/avatars/x.webp"))
        XCTAssertFalse(sameBox("http://192.168.0.37:8080/", "http://192.168.0.37:8081/"))
        XCTAssertFalse(sameBox("http://192.168.0.37:8080/", "http://example.com/"))
    }
}
