// swift-tools-version: 6.0
// The Spieltisch app's decisions, kept free of UIKit so `swift test` runs them on Linux.
import PackageDescription

let package = Package(
    name: "ControllerCore",
    platforms: [.iOS(.v15), .macOS(.v13)],
    products: [.library(name: "ControllerCore", targets: ["ControllerCore"])],
    targets: [
        .target(name: "ControllerCore"),
        .testTarget(name: "ControllerCoreTests", dependencies: ["ControllerCore"]),
    ]
)
