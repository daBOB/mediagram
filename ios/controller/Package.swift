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
            dependencies: [.product(name: "ControllerCore", package: "ControllerCore")],
            resources: [.process("Resources")],
            // SwiftUI re-exports SwiftUICore, which exists only from iOS 18. Apple's linker drops the
            // auto-link to it; lld keeps it, and iOS 16/17 then refuse to launch the app.
            linkerSettings: [.unsafeFlags(["-Xlinker", "--ignore-auto-link-option=SwiftUICore"])]
        ),
    ],
    // UIKit and WebKit delegates are not yet annotated for Swift 6's strict checking.
    swiftLanguageModes: [.v5]
)
