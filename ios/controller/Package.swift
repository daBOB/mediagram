// swift-tools-version: 6.0
// The Spieltisch controller for iPhone and iPad, built on Linux with xtool (`xtool dev`).
import PackageDescription

let package = Package(
    name: "Spieltisch",
    platforms: [
        .iOS(.v16),
        .macOS(.v13),
    ],
    products: [
        // An xtool project contains exactly one library product: the app.
        .library(name: "Spieltisch", targets: ["Spieltisch"]),
    ],
    dependencies: [
        .package(path: "ControllerCore"),
    ],
    targets: [
        .target(
            name: "Spieltisch",
            dependencies: [.product(name: "ControllerCore", package: "ControllerCore")]
        ),
    ],
    // UIKit and WebKit delegates are not yet annotated for Swift 6's strict checking.
    swiftLanguageModes: [.v5]
)
